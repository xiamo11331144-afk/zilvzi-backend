const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const root = path.resolve(__dirname, '../..')
const source = file => fs.readFileSync(path.join(root, file), 'utf8')
const empty = () => ({ notificationUnread: 0, circleUnread: 0, privateUnread: 0, pendingRequests: 0 })
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
const snapshot = value => JSON.parse(JSON.stringify(value))

function badgeHarness() {
  const app = { globalData: { isLoggedIn: true, token: 'test-session-a', userId: 1 } }
  const calls = [], tabBar = {}, pages = []
  const wx = {
    setTabBarBadge({ index, text }) { tabBar[index] = text },
    removeTabBarBadge({ index }) { delete tabBar[index] }
  }
  const request = { get(url) { const d = deferred(); calls.push({ ...d, url }); return d.promise } }
  const context = { module: { exports: {} }, require: () => request, getApp: () => app, wx }
  vm.runInNewContext(source('miniprogram/utils/badges.js'), context)
  const api = context.module.exports
  function page() {
    const p = { data: {}, setData(data) { Object.assign(this.data, snapshot(data)) } }
    pages.push(p)
    return p
  }
  return { app, calls, tabBar, api, page }
}

function pageHarness(name, request, counts = empty()) {
  let page
  const notices = []
  const wx = { showToast: options => notices.push(options.title), showModal(options) { this.modal = options }, navigateTo() {}, switchTab() {}, setNavigationBarTitle() {} }
  const badgeApi = { emptyCounts: empty, bind() {}, unbind() {}, refresh: async () => counts }
  const context = {
    Page(config) { page = config }, wx, getApp: () => ({ globalData: { userId: 1 } }),
    require(id) { return id.endsWith('/badges') ? badgeApi : request }, console
  }
  vm.runInNewContext(source(`miniprogram/pages/${name}/${name}.js`), context)
  page.data = snapshot(page.data)
  page.setData = function (data) { Object.assign(this.data, snapshot(data)) }
  page._visible = true
  return { page, wx, notices }
}

test('批量已读和清除的业务失败不能清空界面或计数', async () => {
  const { page, wx } = pageHarness('notification', {
    put: async () => ({ code: 500, message: '保存失败' }),
    del: async () => ({ code: 500, message: '删除失败' })
  })
  page.setData({ unreadCount: 1, notifications: [{ id: 1, isRead: 0 }] })
  await page.markAllAsRead()
  assert.equal(page.data.unreadCount, 1)
  assert.equal(page.data.notifications[0].isRead, 0)
  page.clearAll()
  await wx.modal.success({ confirm: true })
  assert.equal(page.data.notifications.length, 1)
  assert.equal(page.data.unreadCount, 1)
  assert.equal(page.data.actionBusy, false)
})

test('迟到的列表响应不会恢复已经成功标记为已读的通知', async () => {
  const response = deferred()
  const { page } = pageHarness('notification', {
    get: () => response.promise,
    put: async () => success(null)
  })
  page.setData({ notifications: [{ id: 1, isRead: 0, type: 'system' }], unreadCount: 1 })
  const pending = page.loadNotifications()
  await page.readNotification({ currentTarget: { dataset: { id: 1 } } })
  response.resolve(success({ list: [{ id: 1, isRead: 0 }], unreadCount: 1, hasMore: false }))
  await pending
  assert.equal(page.data.notifications[0].isRead, 1)
  assert.equal(page.data.unreadCount, 0)
})

const success = data => ({ code: 200, data })

test('角标有固定归属：申请、互动、通知加真实私信；首页只保留快捷入口', async () => {
  const h = badgeHarness(), page = h.page()
  const promise = h.api.bind(page)
  assert.equal(h.calls[0].url, '/api/notification/badges')
  h.calls[0].resolve(success({ notificationUnread: 2, circleUnread: 3, privateUnread: 4, pendingRequests: 1 }))
  await promise
  assert.deepEqual(h.tabBar, { 1: '1', 2: '3', 3: '6' })
  assert.equal(page.data.badgeCounts.notificationUnread, 2)
  assert.equal(page.data.badgeCounts.privateUnread, 4)
})

test('并行页面共用请求，强制刷新后忽略旧响应', async () => {
  const h = badgeHarness()
  const first = h.api.bind(h.page())
  const shared = h.api.bind(h.page())
  assert.equal(h.calls.length, 1)
  const fresh = h.api.refresh(true)
  h.calls[1].resolve(success({ ...empty(), privateUnread: 1 }))
  await fresh
  h.calls[0].resolve(success({ ...empty(), privateUnread: 8 }))
  await Promise.all([first, shared])
  assert.equal(h.tabBar[3], '1')
})

test('退出和切换账号后，迟到的请求不能恢复旧账号角标', async () => {
  const h = badgeHarness(), page = h.page()
  const first = h.api.bind(page)
  h.app.globalData.isLoggedIn = false
  h.app.globalData.token = null
  h.api.reset()
  h.calls[0].resolve(success({ ...empty(), privateUnread: 99 }))
  await first
  assert.deepEqual(h.tabBar, {})
  h.app.globalData = { isLoggedIn: true, token: 'test-session-b', userId: 2 }
  const next = h.api.bind(page)
  assert.deepEqual(page.data.badgeCounts, empty())
  h.calls[1].resolve(success({ ...empty(), circleUnread: 2 }))
  await next
  assert.deepEqual(h.tabBar, { 2: '2' })
})

test('角标请求失败或数据缺失时保留最后成功计数并提示重试', async () => {
  const h = badgeHarness(), page = h.page()
  let pending = h.api.bind(page)
  h.calls[0].resolve(success({ ...empty(), privateUnread: 5 }))
  await pending
  pending = h.api.refresh(true)
  h.calls[1].resolve({ code: 500, message: '暂时失败' })
  await pending
  assert.equal(h.tabBar[3], '5')
  assert.equal(page.data.badgeError, '暂时失败')
  pending = h.api.refresh(true)
  h.calls[2].resolve(success({ notificationUnread: 0 }))
  await pending
  assert.equal(h.tabBar[3], '5')
  assert.match(page.data.badgeError, /不完整/)
})

test('通知已读业务失败时保留未读状态；成功只改变对应通知', async () => {
  let fail = true
  const { page, notices } = pageHarness('notification', { put: async () => fail ? { code: 403, message: '无权操作' } : success(null) }, { ...empty(), notificationUnread: 1 })
  page.setData({ notifications: [{ id: 1, isRead: 0, type: 'system' }, { id: 2, isRead: 0, type: 'system' }], unreadCount: 2 })
  await page.readNotification({ currentTarget: { dataset: { id: 1 } } })
  assert.equal(page.data.unreadCount, 2)
  assert.equal(page.data.notifications[0].isRead, 0)
  assert.deepEqual(notices, ['无权操作'])
  fail = false
  await page.readNotification({ currentTarget: { dataset: { id: 1 } } })
  assert.equal(page.data.notifications[0].isRead, 1)
  assert.equal(page.data.notifications[1].isRead, 0)
  assert.equal(page.data.unreadCount, 1)
})

test('全部已读和清除只提交当前分类，私信与其他分类保留', async () => {
  const urls = []
  const { page, wx } = pageHarness('notification', {
    put: async url => { urls.push(url); return success(null) },
    del: async url => { urls.push(url); return success(null) }
  }, { ...empty(), notificationUnread: 3 })
  page.setData({ category: 'circle', notifications: [{ id: 1, isRead: 0 }], unreadCount: 1 })
  await page.markAllAsRead()
  assert.equal(page.data.unreadCount, 0)
  page.clearAll()
  await wx.modal.success({ confirm: true })
  assert.deepEqual(urls, ['/api/notification/read/all?category=circle', '/api/notification/clear?category=circle'])
  assert.equal(page.data.notifications.length, 0)
})

test('切换通知分类后忽略旧列表；分页失败重试仍请求原页', async () => {
  const calls = []
  const { page } = pageHarness('notification', { get(url, data) { const d = deferred(); calls.push({ ...d, data: snapshot(data) }); return d.promise } })
  const old = page.loadNotifications()
  page.selectCategory({ currentTarget: { dataset: { category: 'circle' } } })
  calls[1].resolve(success({ list: [{ id: 2, type: 'like' }], unreadCount: 1, hasMore: true }))
  await new Promise(resolve => setImmediate(resolve))
  calls[0].resolve(success({ list: [{ id: 1 }], unreadCount: 9, hasMore: false }))
  await old
  assert.equal(page.data.notifications[0].id, 2)
  const more = page.loadMore()
  calls[2].reject(new Error('网络失败'))
  await more
  assert.equal(page.data.page, 1)
  const retry = page.retryLoad()
  assert.deepEqual(calls[3].data, { category: 'circle', page: 2, size: 20 })
  calls[3].resolve(success({ list: [{ id: 3, type: 'comment' }], unreadCount: 1, hasMore: false }))
  await retry
  assert.deepEqual(page.data.notifications.map(n => n.id), [2, 3])
})

test('通知删除成功后重载第一页，避免偏移分页跳过下一条', async () => {
  const calls = []
  const { page } = pageHarness('notification', {
    del: async () => success(null),
    get: async (url, data) => { calls.push(snapshot(data)); return success({ list: [{ id: 21 }], unreadCount: 0, hasMore: false }) }
  })
  page.setData({ page: 2, notifications: [{ id: 1 }] })
  await page.deleteOne({ currentTarget: { dataset: { id: 1 } } })
  assert.equal(calls[0].page, 1)
  assert.deepEqual(page.data.notifications.map(n => n.id), [21])
})

test('聊天打开最新页并刷新计数，更早消息按顺序追加到前面', async () => {
  const calls = []
  const { page } = pageHarness('message', { get: async (url, data) => {
    calls.push({ url, ...snapshot(data) })
    return success(data.page === 1 ? Array.from({ length: 50 }, (_, i) => ({ id: i + 51, fromUserId: 2, content: `消息${i + 51}` })) : [{ id: 50, fromUserId: 2, content: '更早消息' }])
  } })
  page.setData({ partnerId: 2, viewMode: 'chat' })
  await page.loadConversation(2)
  assert.equal(page.data.messages[0].id, 51)
  assert.equal(page.data.messages[49].id, 100)
  assert.equal(page.data.messagesHasMore, true)
  assert.equal(page.data.scrollToView, 'msg-bottom')
  await page.loadOlderMessages()
  assert.equal(calls[1].page, 2)
  assert.equal(page.data.messages[0].id, 50)
  assert.equal(page.data.messages[50].id, 100)
  assert.equal(page.data.messagesHasMore, false)
})

test('聊天或会话列表业务失败显示错误，不显示成无消息', async () => {
  const { page } = pageHarness('message', { get: async () => ({ code: 500, message: '查询失败' }) })
  await page.loadConversations()
  assert.equal(page.data.loadError, '查询失败')
  assert.equal(page.data.conversationPage, 0)
  page.setData({ viewMode: 'chat', partnerId: 2 })
  await page.loadConversation(2)
  assert.equal(page.data.loadError, '查询失败')
  assert.equal(page.data.messagePage, 0)
})
