const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')

const root = path.resolve(__dirname, '..')
const success = data => ({ code: 200, data })

function loadPage(relativePath, request) {
  let definition
  const calls = { toast: [], dates: [], posts: [], gets: [], modals: [], hiddenLoading: 0 }
  const wx = {
    getStorageSync: key => calls.storage && calls.storage[key],
    setStorageSync: (key, value) => { calls.storage = { ...calls.storage, [key]: value } },
    showToast: options => calls.toast.push(options),
    showLoading() {},
    hideLoading() { calls.hiddenLoading++ },
    showModal(options) { calls.modals.push(options) }
  }
  const context = {
    Page: value => { definition = value },
    getApp: () => ({ globalData: { userId: 1 } }),
    wx,
    console,
    Date,
    request,
    require: name => {
      if (name.endsWith('/request')) return request
      if (name.endsWith('/checkin-review')) {
        const module = { exports: {} }
        vm.runInNewContext(fs.readFileSync(path.join(root, 'miniprogram/utils/checkin-review.js'), 'utf8'), {
          module, require: () => request
        })
        return module.exports
      }
      if (name.endsWith('/badges')) return { emptyCounts: () => ({}), refresh() {}, bind() {}, unbind() {} }
      if (name.endsWith('/util')) return {
        formatDate: () => '2026-11-10',
        getDaysInMonth: (year, month) => new Date(year, month, 0).getDate()
      }
      throw new Error(`Unexpected require: ${name}`)
    }
  }
  vm.runInNewContext(fs.readFileSync(path.join(root, relativePath), 'utf8'), context)
  const page = { ...definition, data: JSON.parse(JSON.stringify(definition.data)) }
  page.setData = changes => Object.assign(page.data, changes)
  return { page, calls, wx }
}

function deferred() {
  let resolve
  const promise = new Promise(done => { resolve = done })
  return { promise, resolve }
}

test('课表OCR失败完整显示错误与排查编号，不使用易截断的Toast', () => {
  const { page, calls, wx } = loadPage('miniprogram/pages/plan/plan.js', {})
  wx.chooseMessageFile = options => options.success({ tempFiles: [{ name: 'schedule.jpg', path: 'temporary-file' }] })
  wx.uploadFile = options => options.success({ statusCode: 200, data: JSON.stringify({ code: 500, message: '百度OCR识别失败（错误码：17）（排查编号：abc12345）' }) })
  page.uploadSchedule()
  assert.equal(calls.modals[0].title, '课表识别失败')
  assert.match(calls.modals[0].content, /17.*abc12345/)
  assert.equal(calls.toast.length, 0)
})

function reviewHarness(post) {
  const { page, calls } = loadPage('miniprogram/pages/message/message.js', { post })
  page._visible = true
  page.setData({ partnerId: 2, viewMode: 'chat' })
  calls.refreshes = []
  page.loadConversation = async id => { calls.refreshes.push(id) }
  return { page, calls, event: { currentTarget: { dataset: { checkinid: '17' } } } }
}

test('首页空任务和全部待审核都不能显示全部完成，已通过任务才算完成', async () => {
  for (const [records, complete] of [
    [[], false], [[{ id: 1, checkinStatus: 'pending', completed: 0 }], false],
    [[{ id: 1, checkinStatus: 'approved', completed: 1 }], true]
  ]) {
    const { page } = loadPage('miniprogram/pages/index/index.js', { get: async () => success(records) })
    await page.loadGoals()
    assert.equal(page.data.allChecked, complete)
    assert.equal(page.data.tasksLoading, false)
  }
})

test('首页任务加载失败不伪装成全部完成或没有任务', async () => {
  const { page } = loadPage('miniprogram/pages/index/index.js', { get: async () => ({ code: 500, message: '任务查询失败' }) })
  page.setData({ allChecked: true })
  await page.loadGoals()
  assert.equal(page.data.allChecked, false)
  assert.equal(page.data.tasksLoadError, '任务查询失败')
})

test('首页等待断卡处理完成后再读取任务和计划', async () => {
  const pending = deferred(), calls = []
  const { page } = loadPage('miniprogram/pages/index/index.js', {
    post: () => { calls.push('check'); return pending.promise },
    get: async url => { calls.push(url); return success([]) }
  })
  const loading = page.loadExecutionState()
  assert.deepEqual(calls, ['check'])
  pending.resolve(success({ hasStreakBreak: false, affectedPlans: [] }))
  await loading
  assert.deepEqual(calls, ['check', '/api/checkin/today', '/api/plan/list'])
})

test('达到阈值提示停止补发并保留原计划入口，当天不重复弹窗但仍显示提醒', async () => {
  const { page, calls } = loadPage('miniprogram/pages/index/index.js', { post: async () => success({
    hasStreakBreak: true, affectedPlans: [{ planId: 9, planName: '学习计划', supplementPaused: true,
      infeasibleReason: '断卡任务占比过高', remainingDays: 4, supplementCount: 0 }]
  }) })
  await page.checkStreakBreak()
  assert.equal(calls.modals.length, 1)
  assert.match(calls.modals[0].content, /停止自动补发/)
  assert.match(calls.modals[0].content, /后续继续原日程/)
  assert.doesNotMatch(calls.modals[0].content, /已自动补发/)
  await page.checkStreakBreak()
  assert.equal(calls.modals.length, 1)
  assert.equal(page.data.streakBreakPlans[0].planId, 9)
  assert.match(page.data.streakBreakPlans[0].reminder, /停止自动补发/)
})

test('补发调整失败和检测请求失败均显示真实结果，不宣称补发成功', async () => {
  let failedRequest = false
  const { page, calls } = loadPage('miniprogram/pages/index/index.js', { post: async () => failedRequest
    ? { code: 500, message: '断卡查询失败' }
    : success({ hasStreakBreak: true, affectedPlans: [{ planId: 9, planName: '学习计划', adjustmentFailed: true }] }) })
  await page.checkStreakBreak()
  assert.match(calls.modals[0].content, /调整失败/)
  assert.doesNotMatch(calls.modals[0].content, /已自动补发/)
  failedRequest = true
  await page.checkStreakBreak()
  assert.equal(page.data.streakBreakError, '断卡查询失败')
})

test('计划页同类引领搭子必须先上传凭证，不能直接提交', async () => {
  let posted = 0, opened
  const { page } = loadPage('miniprogram/pages/plan/plan.js', {
    get: async () => success({ required: true }),
    post: async () => { posted++; return success({ status: 'pending' }) }
  })
  page.selectComponent = () => ({ open: task => { opened = task } })
  page.setData({ phaseTasks: { 1: [{ tasks: [{ id: 7, type: 'study', completed: 0 }] }] } })
  await page.completeTask({ currentTarget: { dataset: { id: 7 } } })
  assert.equal(opened.id, 7)
  assert.equal(posted, 0)
})

test('打卡页单项和一键打卡遇到同类搭子时均先上传凭证', async () => {
  let posted = 0
  const opened = []
  const { page } = loadPage('miniprogram/pages/checkin/checkin.js', {
    get: async () => success({ required: true }),
    post: async () => { posted++; return success({ status: 'pending' }) }
  })
  page.selectComponent = () => ({ open: task => opened.push(task.id) })
  page.setData({ todayTasks: [{ id: 7, type: '学习', checked: false, pending: false }] })
  await page.doCheckin({ currentTarget: { dataset: { id: 7 } } })
  await page.batchCheckin()
  assert.deepEqual(opened, [7, 7])
  assert.equal(posted, 0)
})

test('凭证组件拒绝空照片，提交期间防重复，失败保留照片和重试能力', async () => {
  let definition, posted = 0
  const pending = deferred()
  const notices = [], events = []
  const request = { post: () => { posted++; return pending.promise } }
  vm.runInNewContext(fs.readFileSync(path.join(root, 'miniprogram/components/checkin-proof/checkin-proof.js'), 'utf8'), {
    Component: config => { definition = config }, require: () => request,
    wx: { showToast: options => notices.push(options.title) }
  })
  const component = { ...definition.methods, data: JSON.parse(JSON.stringify(definition.data)),
    setData(data) { Object.assign(this.data, data) }, triggerEvent(...args) { events.push(args) } }
  component.open({ id: 7, name: '任务' })
  await component.submit()
  assert.equal(posted, 0)
  assert.equal(notices[0], '请先上传凭证照片')
  component.setData({ image: 'https://example.com/proof.png' })
  const submitted = component.submit()
  await component.submit()
  assert.equal(posted, 1)
  pending.resolve({ code: 500, message: '请稍后重试' })
  await submitted
  assert.equal(component.data.visible, true)
  assert.equal(component.data.submitting, false)
  assert.equal(component.data.image, 'https://example.com/proof.png')
  assert.equal(events.length, 0)
  request.post = async (url, body) => {
    assert.equal(body.taskId, 7)
    assert.equal(body.proofImage, 'https://example.com/proof.png')
    return success({ status: 'pending' })
  }
  await component.submit()
  assert.equal(component.data.visible, false)
  assert.equal(events[0][0], 'submitted')
  assert.equal(events[0][1].status, 'pending')
})

test('照片上传HTTP成功但业务失败时不保存图片，也不能提交审核', async () => {
  let definition, posted = 0
  const notices = []
  const request = { UPLOAD_URL: 'https://example.com/upload', post: async () => { posted++ } }
  vm.runInNewContext(fs.readFileSync(path.join(root, 'miniprogram/components/checkin-proof/checkin-proof.js'), 'utf8'), {
    Component: config => { definition = config }, require: () => request,
    getApp: () => ({ globalData: { token: 'test-only' } }),
    wx: {
      showToast: options => notices.push(options.title),
      chooseMedia: options => options.success({ tempFiles: [{ tempFilePath: 'temporary-photo' }] }),
      uploadFile: options => {
        options.success({ statusCode: 200, data: JSON.stringify({ code: 500, message: '上传失败' }) })
        options.complete()
      }
    }
  })
  const component = { ...definition.methods, data: JSON.parse(JSON.stringify(definition.data)),
    setData(data) { Object.assign(this.data, data) } }
  component.open({ id: 7 })
  component.chooseImage()
  assert.equal(component.data.image, '')
  assert.equal(component.data.uploading, false)
  await component.submit()
  assert.equal(posted, 0)
  assert.deepEqual(notices, ['照片上传失败，请重试', '请先上传凭证照片'])
})

test('引领者首页使用最新搭子关系，收到的lead关系也进入凭证上传', async () => {
  let posts = 0
  const { page } = loadPage('miniprogram/pages/index/index.js', {
    get: async url => {
      assert.equal(url, '/api/checkin/review-required')
      return success({ required: true })
    },
    post: async () => { posts++; return success({ status: 'approved' }) }
  })
  page.setData({ partners: [], todayGoals: [{ id: 11, name: '学习任务', type: '学习', checked: false }] })
  await page.toggleCheckin({ currentTarget: { dataset: { id: 11 } } })
  assert.equal(page.data.showProofModal, true)
  assert.equal(page.data.proofTaskId, 11)
  assert.equal(posts, 0)
})

test('搭子接口业务或网络失败时首页不绕过凭证直接打卡', async () => {
  for (const networkFailure of [false, true]) {
    let posts = 0
    const { page, calls } = loadPage('miniprogram/pages/index/index.js', {
      get: async () => { if (networkFailure) throw new Error('断网'); return { code: 500 } },
      post: async () => { posts++; return success({ status: 'approved' }) }
    })
    page.setData({ todayGoals: [{ id: 11, checked: false }] })
    await page.toggleCheckin({ currentTarget: { dataset: { id: 11 } } })
    assert.equal(posts, 0)
    assert.match(calls.toast[0].title, /审核条件查询失败|断网/)
  }
})

test('首页以服务端pending为准，不把直接提交误显示为已完成', async () => {
  const { page, calls } = loadPage('miniprogram/pages/index/index.js', {
    get: async () => success({ required: false }),
    post: async () => success({ status: 'pending' })
  })
  page.setData({ todayGoals: [{ id: 11, checked: false, pending: false }] })
  await page.toggleCheckin({ currentTarget: { dataset: { id: 11 } } })
  assert.equal(page.data.todayGoals[0].checked, false)
  assert.equal(page.data.todayGoals[0].pending, true)
  assert.match(calls.toast[0].title, /等待搭子审核/)
})

test('凭证驳回向审核接口提交false和原因，成功后刷新当前对话', async () => {
  const posts = []
  const { page, calls, event } = reviewHarness(async (url, body) => { posts.push({ url, body }); return success({ status: 'rejected' }) })
  page.rejectCheckin(event)
  assert.equal(calls.modals[0].editable, true)
  await calls.modals[0].success({ confirm: true, content: '  请补充清晰凭证  ' })
  assert.deepEqual(JSON.parse(JSON.stringify(posts)), [{ url: '/api/checkin/review', body: { checkinId: 17, approved: false, comment: '请补充清晰凭证' } }])
  assert.deepEqual(calls.refreshes, [2])
  assert.equal(calls.toast[0].title, '已驳回打卡')
  assert.equal(page.data.reviewingCheckinId, null)
})

test('凭证通过保留approved=true契约，不提交驳回原因', async () => {
  const posts = []
  const { page, calls, event } = reviewHarness(async (url, body) => { posts.push(body); return success({ status: 'approved' }) })
  page.confirmCheckin(event)
  assert.equal(calls.modals[0].editable, false)
  await calls.modals[0].success({ confirm: true })
  assert.deepEqual(JSON.parse(JSON.stringify(posts)), [{ checkinId: 17, approved: true }])
  assert.equal(calls.toast[0].title, '已确认打卡')
})

test('审核取消或弹窗失败不发送请求，并恢复可操作状态', async () => {
  let posted = 0
  const { page, calls, event } = reviewHarness(async () => { posted++; return success(null) })
  page.rejectCheckin(event)
  await calls.modals[0].success({ confirm: false })
  assert.equal(page.data.reviewingCheckinId, null)
  page.confirmCheckin(event)
  calls.modals[1].fail()
  assert.equal(posted, 0)
  assert.equal(page.data.reviewingCheckinId, null)
})

test('打开审核窗口及请求期间，重复通过或驳回只发送一次', async () => {
  const pending = deferred()
  let posted = 0
  const { page, calls, event } = reviewHarness(() => { posted++; return pending.promise })
  page.rejectCheckin(event)
  page.confirmCheckin(event)
  assert.equal(calls.modals.length, 1)
  const operation = calls.modals[0].success({ confirm: true, content: '' })
  page.rejectCheckin(event)
  assert.equal(posted, 1)
  pending.resolve(success({ status: 'rejected' }))
  await operation
  assert.equal(page.data.reviewingCheckinId, null)
})

test('审核业务失败保留凭证，不显示成功、不刷新，并允许重试', async () => {
  const { page, calls, event } = reviewHarness(async () => ({ code: 500, message: '该打卡已被审核' }))
  page.setData({ messages: [{ id: 9, type: 'checkin_proof' }] })
  page.rejectCheckin(event)
  await calls.modals[0].success({ confirm: true })
  assert.equal(page.data.messages[0].type, 'checkin_proof')
  assert.equal(calls.toast[0].title, '该打卡已被审核')
  assert.deepEqual(calls.refreshes, [])
  assert.equal(page.data.reviewingCheckinId, null)
})

test('审核网络失败释放操作锁，切换对话后成功响应不刷新其他会话', async () => {
  let failed = true
  const { page, calls, event } = reviewHarness(async () => {
    if (failed) throw new Error('网络连接失败')
    return success({ status: 'rejected' })
  })
  page.rejectCheckin(event)
  await calls.modals[0].success({ confirm: true })
  assert.equal(page.data.reviewingCheckinId, null)
  assert.equal(calls.toast[0].title, '网络连接失败')
  failed = false
  page.rejectCheckin(event)
  page.setData({ partnerId: 3 })
  await calls.modals[1].success({ confirm: true })
  assert.deepEqual(calls.refreshes, [])
  assert.equal(calls.hiddenLoading, 2)
})

test('凭证卡片的审核入口仅对收到的凭证开放，已处理卡片不误标审核通过', async () => {
  const { page } = loadPage('miniprogram/pages/message/message.js', { get: async () => success([
    { id: 1, fromUserId: 2, type: 'checkin_proof', relatedId: 17 },
    { id: 2, fromUserId: 1, type: 'checkin_proof', relatedId: 18 },
    { id: 3, fromUserId: 2, type: 'checkin_confirmed', relatedId: 19, content: '[IMG]https://example.com/proof.png[/IMG]\n\n❌ 打卡未通过：图片不清楚' }
  ]) })
  page._visible = true
  await page.loadConversation(2)
  assert.equal(page.data.messages[0].isCheckinProof, true)
  assert.equal(page.data.messages[1].isCheckinProof, false)
  assert.equal(page.data.messages[2].isCheckinProof, false)
  assert.equal(page.data.messages[2].proofImage, 'https://example.com/proof.png')
  const template = fs.readFileSync(path.join(root, 'miniprogram/pages/message/message.wxml'), 'utf8')
  assert.match(template, /wx:if="\{\{item.isCheckinProof\}\}" class="review-actions"/)
  assert.match(template, /bindtap="rejectCheckin"/)
  assert.match(template, /审核已处理/)
})

test('日历按显示月份请求并正确跨年', async () => {
  const calls = []
  const { page } = loadPage('miniprogram/pages/checkin/checkin.js', {
    get: async (url, params) => {
      if (url === '/api/checkin/dates') calls.push(params)
      return success([])
    }
  })
  page.setData({ currentYear: 2027, currentMonth: 1 })
  page.prevMonth()
  await new Promise(resolve => setImmediate(resolve))
  assert.deepEqual(JSON.parse(JSON.stringify(calls[0])), { year: 2026, month: 12 })
  page.nextMonth()
  page.nextMonth()
  await new Promise(resolve => setImmediate(resolve))
  assert.deepEqual(JSON.parse(JSON.stringify(calls.slice(1))), [{ year: 2027, month: 1 }, { year: 2027, month: 2 }])
})

test('较旧月份请求晚返回时不覆盖最新月份', async () => {
  const pending = []
  const request = {
    get: (url, params) => {
      const wait = deferred()
      pending.push({ params, wait })
      return wait.promise
    }
  }
  const { page } = loadPage('miniprogram/pages/checkin/checkin.js', request)
  page.setData({ currentYear: 2026, currentMonth: 12 })
  const oldRequest = page.loadCheckedDates()
  page.setData({ currentYear: 2027, currentMonth: 1 })
  const newRequest = page.loadCheckedDates()
  pending[1].wait.resolve(success(['2027-01-02']))
  await newRequest
  pending[0].wait.resolve(success(['2026-12-31']))
  await oldRequest
  assert.deepEqual(Array.from(page.data.checkedDates), ['2027-01-02'])
  assert.deepEqual(JSON.parse(JSON.stringify(pending.map(item => item.params))), [
    { year: 2026, month: 12 }, { year: 2027, month: 1 }
  ])
})

test('待审核任务不参与批量提交，驳回任务允许重提且提示等待审核', async () => {
  const posts = []
  const request = {
    get: async url => success(url.endsWith('/review-required') ? { required: false } : url.endsWith('/stats') ? { total: 0, streak: 0 } : []),
    post: async (url, body) => {
      posts.push({ url, body })
      return success({ status: 'pending' })
    }
  }
  const { page, calls } = loadPage('miniprogram/pages/checkin/checkin.js', request)
  page.setData({ todayTasks: [
    { id: 1, name: '审核中', pending: true, checked: false },
    { id: 2, name: '驳回重试', rejected: true, pending: false, checked: false },
    { id: 3, name: '已通过', pending: false, checked: true }
  ] })
  await page.batchCheckin()
  assert.deepEqual(posts.map(item => item.body.taskId), [2])
  assert.match(calls.toast.at(-1).title, /等待审核/)
  assert.equal(page.data.showSuccess, false)
})

test('单项打卡 pending 不提示已通过', async () => {
  const request = {
    post: async () => success({ status: 'pending' }),
    get: async url => success(url.endsWith('/review-required') ? { required: false } : url.endsWith('/stats') ? { total: 0, streak: 0 } : [])
  }
  const { page, calls } = loadPage('miniprogram/pages/checkin/checkin.js', request)
  page.setData({ todayTasks: [{ id: 7, pending: false, checked: false, streakBreak: false }] })
  await page.doCheckin({ currentTarget: { dataset: { id: 7 } } })
  assert.match(calls.toast[0].title, /等待审核/)
  assert.doesNotMatch(calls.toast[0].title, /通过|成功/)
})

test('计划页 pending 提交只设置待审核，不标记完成', async () => {
  const calls = { post: [], toast: [] }
  const request = {
    post: async (url, body) => {
      calls.post.push({ url, body })
      return success({ status: 'pending' })
    },
    get: async url => success(url.endsWith('/review-required') ? { required: false } : [])
  }
  const { page, calls: harnessCalls } = loadPage('miniprogram/pages/plan/plan.js', request)
  const task = { id: 11, completed: 0, pending: false }
  page.setData({
    planId: 3,
    phases: [],
    phaseTasks: { 1: [{ day: 1, tasks: [task] }] }
  })
  await page.completeTask({ currentTarget: { dataset: { id: 11 } } })
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(calls.post[0].url, '/api/checkin/do')
  assert.equal(task.checkinStatus, 'pending')
  assert.equal(task.completed, 0)
  assert.equal(task.pending, true)
  assert.match(harnessCalls.toast.at(-1).title, /等待审核/)
})

test('计划刷新同步历史审核状态且 today 缺失不会清空已有状态', async () => {
  const records = [
    { id: 21, dayNumber: 1, completed: -1, checkinStatus: 'pending' },
    { id: 22, dayNumber: 2, completed: -1, checkinStatus: 'rejected' },
    { id: 23, dayNumber: 3, completed: 1, checkinStatus: 'approved' }
  ]
  const { page } = loadPage('miniprogram/pages/plan/plan.js', {
    get: async url => success(url === '/api/checkin/today' ? [] : records)
  })
  page.setData({
    planId: 3,
    phases: [{ phase: 1, startDay: 1, endDay: 4 }],
    phaseTasks: { 1: [{ day: 1, tasks: [
      { id: 21, completed: -1 }, { id: 22, completed: -1 },
      { id: 23, completed: 0 }, { id: 24, completed: 0, checkinStatus: 'pending' }
    ] }] }
  })
  await page.refreshPlanProgress()
  const tasks = page.data.phaseTasks[1][0].tasks
  assert.equal(tasks[0].pending, true)
  assert.equal(tasks[0].completed, 0)
  assert.equal(tasks[1].rejected, true)
  assert.equal(tasks[1].completed, 0)
  assert.equal(tasks[2].completed, 1)
  assert.equal(tasks[3].checkinStatus, 'pending')
  assert.equal(page.data.phases[0].completedDays, 1)
  assert.equal(page.data.phases[0].progress, 25)
})

test('计划页历史 pending 阻止提交，rejected 断卡标记仍可重新提交', async () => {
  const posts = []
  const { page } = loadPage('miniprogram/pages/plan/plan.js', {
    post: async (url, body) => { posts.push(body.taskId); return success({ status: 'pending' }) },
    get: async url => success(url.endsWith('/review-required') ? { required: false } : [])
  })
  page.setData({ phases: [], phaseTasks: { 1: [{ day: 1, tasks: [
    { id: 31, completed: -1, pending: true, checkinStatus: 'pending' },
    { id: 32, completed: -1, rejected: true, checkinStatus: 'rejected' }
  ] }] } })
  await page.completeTask({ currentTarget: { dataset: { id: 31 } } })
  await page.completeTask({ currentTarget: { dataset: { id: 32 } } })
  assert.deepEqual(posts, [32])
  assert.equal(page.data.phaseTasks[1][0].tasks[1].pending, true)
})

test('阶段加载保留跨日审核状态并刷新计划进度', async () => {
  const records = [
    { id: 41, dayNumber: 1, completed: -1, checkinStatus: 'pending' },
    { id: 42, dayNumber: 2, completed: -1, checkinStatus: 'rejected' }
  ]
  const { page } = loadPage('miniprogram/pages/plan/plan.js', {
    post: async () => success(records)
  })
  let refreshed = false
  page.refreshPlanProgress = async () => { refreshed = true }
  page.setData({ planId: 3, phaseTasks: {}, isLoadingPhase: false })
  await page.loadPhaseTasks(1)
  const days = page.data.phaseTasks[1]
  assert.equal(days[0].tasks[0].pending, true)
  assert.equal(days[0].tasks[0].completed, 0)
  assert.equal(days[1].tasks[0].rejected, true)
  assert.equal(days[1].tasks[0].completed, 0)
  assert.equal(refreshed, true)
})
