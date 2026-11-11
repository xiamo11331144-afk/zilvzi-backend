const fs = require('node:fs');
const vm = require('node:vm');
const test = require('node:test');
const assert = require('node:assert/strict');

class Element {
  constructor(tag) { this.tagName = tag; this.children = []; this.dataset = {}; this.style = {}; this.value = ''; this.classList = { add() {}, remove() {} }; }
  appendChild(node) { this.children.push(node); return node; }
  replaceChildren(...nodes) { this.children = nodes; }
}

function loadAdmin(fetchImpl = async () => ({ json: async () => ({ code: 200, data: [] }) })) {
  const html = fs.readFileSync('backend/src/main/resources/static/admin/index.html', 'utf8');
  const script = html.match(/<script>([\s\S]*?)<\/script>/i);
  assert.ok(script, 'inline admin script exists');
  const elements = new Map();
  const document = {
    addEventListener() {},
    createElement: tag => new Element(tag),
    createTextNode: value => ({ textContent: value }),
    getElementById: id => { if (!elements.has(id)) elements.set(id, new Element('div')); return elements.get(id); },
    querySelectorAll: () => []
  };
  const context = { document, localStorage: { getItem: () => null }, console, fetch: fetchImpl, alert() {}, confirm() { return false; }, setTimeout, clearTimeout };
  vm.runInNewContext(script[1], context);
  return { html, context, elements };
}

test('admin dynamic user data stays text and action metadata', () => {
  const { html, context } = loadAdmin();
  const attack = `"><img src=x onerror=alert(1)>\'\n`;
  const rows = context.buildUsers([{ id: 9, nickname: attack, phone: attack, level: 1, experience: 0 }]);
  assert.equal(rows[0].children[1].textContent, attack);
  assert.equal(rows[0].children[2].textContent, attack);
  const edit = rows[0].children[6].children[0];
  assert.equal(edit.dataset.action, 'edit-user');
  assert.equal(edit.dataset.nickname, attack);
  assert.doesNotMatch(html, /\bon[a-z]+\s*=/i);
  assert.doesNotMatch(context.buildUsers.toString(), /innerHTML/);
  assert.equal(context.B, '');
});

test('all admin list renderers keep untrusted values in text nodes', async () => {
  const attack = '<svg onload=alert(1)>';
  const payloadByPath = {
    '/api/circle/list?size=100': { id: 2, name: attack, creatorNickname: attack },
    '/api/circle/all-posts?size=100&keyword=': { id: 3, nickname: attack, content: attack },
    '/api/circle/all-comments?size=200&keyword=': { id: 4, nickname: attack, content: attack },
    '/api/admin/topics?keyword=': { id: 5, title: attack, nickname: attack },
    '/api/admin/topics/5/replies': { id: 6, nickname: attack, content: attack }
  };
  const fetchImpl = async url => {
    const path = String(url);
    const match = Object.keys(payloadByPath).find(key => path === key);
    return { json: async () => ({ code: 200, data: match ? [payloadByPath[match]] : [] }) };
  };
  const { context, elements } = loadAdmin(fetchImpl);
  await context.loadCircles();
  await context.loadPosts();
  await context.loadComments();
  await context.loadTopics();
  await context.showTopicReplies(5);
  assert.equal(elements.get('circleList').children[0].children[1].textContent, attack);
  assert.equal(elements.get('postList').children[0].children[2].textContent, attack);
  assert.equal(elements.get('commentList').children[0].children[2].textContent, attack);
  assert.equal(elements.get('topicList').children[0].children[1].children[0].textContent, attack);
  assert.equal(elements.get('replyList').children[0].children[0].children[1].textContent, attack);
});
