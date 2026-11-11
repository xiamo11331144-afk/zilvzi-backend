const fs = require('node:fs');
const vm = require('node:vm');
const test = require('node:test');
const assert = require('node:assert/strict');

function makePage(postImpl) {
  let page;
  const toasts = [];
  const storage = {};
  const app = { globalData: {} };
  const wx = {
    showToast: item => toasts.push(item.title), showLoading() {}, hideLoading() {},
    login: async () => ({ code: 'wx-code' }),
    setStorageSync: (key, value) => { storage[key] = value; },
    switchTab() {}, navigateBack() {}
  };
  const context = {
    Page: value => { page = value; }, getApp: () => app,
    getCurrentPages: () => [], wx, console,
    require: () => ({ post: postImpl })
  };
  vm.runInNewContext(fs.readFileSync('miniprogram/pages/login/login.js', 'utf8'), context);
  page.setData = patch => Object.assign(page.data, patch);
  return { page, toasts, app, storage };
}

const businessError = async () => ({ code: 500, message: 'simulated business failure' });

test('sendCode business failure does not claim success or start cooldown', async () => {
  const { page, toasts } = makePage(businessError);
  Object.assign(page.data, { loginType: 'email', email: 'scan@example.invalid' });
  let timerStarted = false;
  page.startCountdown = () => { timerStarted = true; };
  await page.sendCode();
  assert.deepEqual(toasts, ['simulated business failure']);
  assert.equal(page.data.codeSent, false);
  assert.equal(timerStarted, false);
  assert.equal(page.data.sendingCode, false);
});

test('login and registration business failures show messages and release submit guard', async () => {
  for (const mode of ['login', 'register']) {
    const { page, toasts } = makePage(businessError);
    Object.assign(page.data, { mode, agreed: true });
    await page.submit();
    assert.deepEqual(toasts, ['simulated business failure']);
    assert.equal(page.data.submitting, false);
  }
});

test('wechat business failure does not store a login session', async () => {
  const { page, toasts, app, storage } = makePage(businessError);
  await page.wechatLogin();
  assert.deepEqual(toasts, ['simulated business failure']);
  assert.equal(page.data.wechatLoggingIn, false);
  assert.equal(app.globalData.isLoggedIn, undefined);
  assert.deepEqual(storage, {});
});

test('concurrent submit calls issue only one request', async () => {
  let resolveRequest;
  let count = 0;
  const post = () => { count++; return new Promise(resolve => { resolveRequest = resolve; }); };
  const { page } = makePage(post);
  Object.assign(page.data, { agreed: true });
  const first = page.submit();
  const second = page.submit();
  await Promise.resolve();
  assert.equal(count, 1);
  resolveRequest({ code: 500, message: 'done' });
  await Promise.all([first, second]);
  assert.equal(page.data.submitting, false);
});
