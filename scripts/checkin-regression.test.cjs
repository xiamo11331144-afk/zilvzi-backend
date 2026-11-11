const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')

const root = path.resolve(__dirname, '..')
const success = data => ({ code: 200, data })

function loadPage(relativePath, request) {
  let definition
  const calls = { toast: [], dates: [], posts: [], gets: [] }
  const wx = {
    showToast: options => calls.toast.push(options),
    showLoading() {},
    hideLoading() {},
    showModal() {}
  }
  const context = {
    Page: value => { definition = value },
    getApp: () => ({ globalData: {} }),
    wx,
    console,
    Date,
    request,
    require: name => {
      if (name.endsWith('/request')) return request
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
  return { page, calls }
}

function deferred() {
  let resolve
  const promise = new Promise(done => { resolve = done })
  return { promise, resolve }
}

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
    get: async url => success(url.endsWith('/stats') ? { total: 0, streak: 0 } : []),
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
    get: async url => success(url.endsWith('/stats') ? { total: 0, streak: 0 } : [])
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
    get: async () => success([])
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
    get: async () => success([])
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
