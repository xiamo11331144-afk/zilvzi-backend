const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const root = path.resolve(__dirname, '..')
const vipHelper = require('../miniprogram/utils/vip')

function pageHarness(pagePath, request = {}, loggedIn = true) {
  let definition
  const calls = { modal: [], navigate: [], toast: [], back: 0 }
  const wx = {
    showModal: options => calls.modal.push(options),
    navigateTo: options => calls.navigate.push(options),
    showToast: options => calls.toast.push(options),
    navigateBack: () => calls.back++, switchTab: options => calls.navigate.push(options)
  }
  const helperContext = { wx, module: { exports: {} }, Date, Number, String }
  vm.runInNewContext(fs.readFileSync(path.join(root, 'miniprogram/utils/vip.js'), 'utf8'), helperContext)
  const context = {
    Page: value => { definition = value },
    getApp: () => ({ globalData: { isLoggedIn: loggedIn } }),
    getCurrentPages: () => [{}, {}], wx, console,
    require: name => {
      if (name.endsWith('/request')) return request
      if (name.endsWith('/vip')) return helperContext.module.exports
      if (name.endsWith('/badges')) return { emptyCounts: () => ({}) }
      throw new Error(`Unexpected module ${name}`)
    }
  }
  vm.runInNewContext(fs.readFileSync(path.join(root, pagePath), 'utf8'), context)
  const page = { ...definition, data: JSON.parse(JSON.stringify(definition.data)) }
  page.setData = changes => {
    for (const [key, value] of Object.entries(changes)) {
      const parts = key.split('.')
      let object = page.data
      for (const part of parts.slice(0, -1)) object = object[part] ||= {}
      object[parts.at(-1)] = value
    }
  }
  return { page, calls, helper: helperContext.module.exports }
}
const vipPath = 'miniprogram/pages/vip/vip.js'
const goodPlan = { durationDays: 30, priceFen: 990, currency: 'CNY', paymentMode: 'mock', mockEnabled: true }
const success = data => ({ code: 200, data })
const settle = () => new Promise(resolve => setImmediate(resolve))

test('有效期将带时区的服务端时间转换为北京时间并保留秒', () => {
  assert.equal(vipHelper.formatVipUntil('2026-11-07T00:00:01Z'), '2026-11-07 08:00:01（北京时间）')
  assert.equal(vipHelper.formatVipUntil(null), '')
  assert.equal(vipHelper.formatVipUntil('invalid'), '有效期暂不可用')
})

test('只有稳定会员业务码弹出引导，取消不导航', () => {
  const { helper, calls } = pageHarness(vipPath)
  assert.equal(helper.handleVipRequired({ code: 500 }), false)
  assert.equal(helper.handleVipRequired({ code: 40301 }), true)
  calls.modal[0].success({ confirm: false })
  assert.equal(calls.navigate.length, 0)
  calls.modal[0].success({ confirm: true })
  assert.equal(calls.navigate[0].url, '/pages/vip/vip?from=match')
})

test('未登录不发会员请求', () => {
  const { page } = pageHarness(vipPath, { get: () => { throw new Error('must not request') } }, false)
  page.setData({ status: { isVip: true }, order: { orderNo: 'old' }, plan: goodPlan })
  page.onShow()
  assert.match(page.data.error, /登录/)
  assert.equal(page.data.loading, false)
  assert.equal(page.data.status, null)
  assert.equal(page.data.order, null)
  assert.equal(page.data.plan, null)
})

test('页面加载只采信后端会员状态和可用模拟配置', async () => {
  const { page } = pageHarness(vipPath, { get: async url => success(url.endsWith('status')
    ? { isVip: false, vipUntil: null } : { ...goodPlan, paymentMode: 'disabled' }) })
  page.onShow(); await settle()
  assert.equal(page.data.status.isVip, false)
  assert.equal(page.data.plan.mockEnabled, false)
  assert.equal(page.data.priceText, '9.90')
})

test('套餐加载失败清除旧开通能力并可重试', async () => {
  let failed = true
  const { page } = pageHarness(vipPath, { get: async url => {
    if (failed) throw new Error('离线')
    return success(url.endsWith('status') ? { isVip: false } : goodPlan)
  } })
  page.onShow(); await settle()
  assert.equal(page.data.plan, null)
  assert.equal(page.data.error, '离线')
  failed = false
  await page.loadMembership()
  assert.equal(page.data.error, '')
  assert.equal(page.data.plan.mockEnabled, true)
})

test('异常套餐数据不显示模拟开通入口', async () => {
  const { page } = pageHarness(vipPath, { get: async url => success(url.endsWith('status')
    ? { isVip: false } : { ...goodPlan, priceFen: -1 }) })
  page.onShow(); await settle()
  assert.equal(page.data.plan, null)
  assert.match(page.data.error, /套餐暂不可用/)
})

test('禁用环境不发送创建或确认请求', async () => {
  const { page } = pageHarness(vipPath, { post: () => { throw new Error('must not post') } })
  page.setData({ loading: false, plan: { ...goodPlan, mockEnabled: false }, order: { orderNo: 'test', status: 'PENDING' } })
  await page.createTestOrder()
  page.confirmTestOrder()
})

test('创建过程中重复点击只发送一次，不提交价格身份或会员字段', async () => {
  let resolvePost
  const posted = []
  const { page } = pageHarness(vipPath, { post: (...args) => { posted.push(args); return new Promise(resolve => { resolvePost = resolve }) } })
  page._active = true
  page.setData({ loading: false, plan: goodPlan })
  const first = page.createTestOrder()
  await page.createTestOrder()
  assert.equal(posted.length, 1)
  assert.deepEqual(posted[0], ['/api/vip/orders'])
  resolvePost(success({ orderNo: 'test', status: 'PENDING' }))
  await first
  await page.createTestOrder()
  assert.equal(posted.length, 1)
  assert.equal(page.data.status, null)
})

test('创建失败不写本地VIP并释放按钮供重试', async () => {
  const { page } = pageHarness(vipPath, { post: async () => { throw new Error('网络失败') } })
  page._active = true
  page.setData({ loading: false, plan: goodPlan })
  await page.createTestOrder()
  assert.equal(page.data.busy, false)
  assert.equal(page.data.order, null)
  assert.equal(page.data.status, null)
  assert.equal(page.data.error, '网络失败')
})

test('取消模拟确认不发请求且弹窗重复点击有保护', () => {
  const { page, calls } = pageHarness(vipPath, { post: () => { throw new Error('must not post') } })
  page._active = true
  page.setData({ loading: false, plan: goodPlan, order: { orderNo: 'test', status: 'PENDING' } })
  page.confirmTestOrder(); page.confirmTestOrder()
  assert.equal(calls.modal.length, 1)
  assert.match(calls.modal[0].content, /不扣款/)
  calls.modal[0].success({ confirm: false })
  assert.equal(page.data.busy, false)
})

test('模拟确认成功后再查订单和会员，不自动提交搭子申请', async () => {
  const urls = []
  const order = { orderNo: 'test', status: 'TEST_CONFIRMED', durationDays: 30 }
  const { page } = pageHarness(vipPath, {
    post: async url => { urls.push(url); return success(order) },
    get: async url => { urls.push(url); return success(url.endsWith('/status')
      ? { isVip: true, vipUntil: '2026-12-07T00:00:00Z' } : url.endsWith('/plan') ? goodPlan : order) }
  })
  page._active = true
  page.setData({ loading: false, plan: goodPlan, order: { orderNo: 'test', status: 'PENDING' } })
  await page.submitTestConfirmation()
  assert.equal(page.data.status.isVip, true)
  assert.equal(page.data.order.status, 'TEST_CONFIRMED')
  assert.ok(urls.includes('/api/vip/status'))
  assert.ok(urls.includes('/api/vip/orders/test'))
  assert.ok(urls.every(url => !url.includes('/partner/')))
})

test('确认结果未知时不授予本地VIP，保留订单供幂等重试', async () => {
  const { page } = pageHarness(vipPath, { post: async () => { throw new Error('超时') } })
  page._active = true
  page.setData({ order: { orderNo: 'test', status: 'PENDING' } })
  await page.submitTestConfirmation()
  assert.equal(page.data.status, null)
  assert.equal(page.data.order.orderNo, 'test')
  assert.equal(page.data.busy, false)
  assert.equal(page.data.error, '超时')
})

for (const [label, pagePath] of [['列表', 'miniprogram/pages/partner/partner.js'], ['详情', 'miniprogram/pages/partner-detail/partner-detail.js']]) {
  test(`${label}的VIP业务码保留当前场景且释放申请按钮`, async () => {
    const posted = []
    const { page, calls } = pageHarness(pagePath, { post: async (url, data) => { posted.push({ url, data }); return { code: 40301 } } })
    page.setData({ partnerId: 20, matchMode: 'lead', leadAvailable: true, matchList: [{ id: 20, leadCanApply: true }] })
    if (label === '列表') await page.sendRequest({ currentTarget: { dataset: { id: 20, mode: 'lead' } } })
    else await page.applyPartner()
    assert.equal(calls.modal.length, 1)
    assert.equal(calls.toast.length, 0)
    assert.equal(page.data.matchMode, 'lead')
    assert.equal(label === '列表' ? page.data.applying[20] : page.data.applyPending, false)
    assert.equal(posted[0].url, '/api/partner/apply')
    assert.deepEqual(Object.keys(posted[0].data).sort(), ['matchMode', 'partnerId'])
  })
}

test('引领列表在80%边界采信服务端资格，缺失数据或无名额不放行', async () => {
  const base = { id: 20, mentorEnabled: 1, mentorEligible: true, approvedDays: 24, checkinRate: 80, streak: 7, remainingSlots: 1 }
  for (const [patch, expected] of [
    [{}, true], [{ mentorEligible: false }, false], [{ mentorEnabled: 0 }, false],
    [{ remainingSlots: 0 }, false], [{ checkinRate: null }, false], [{ streak: '' }, false],
    [{ mentorEligible: undefined }, false],
    // 客户端不复制资格阈值；异常统计的资格判定和最终提交由服务端负责。
    [{ approvedDays: 23, checkinRate: 76.7 }, true]
  ]) {
    const { page } = pageHarness('miniprogram/pages/partner/partner.js', { get: async () => success([{ ...base, ...patch }]) })
    await page.loadMatchList()
    assert.equal(page.data.matchList[0].leadCanApply, expected, JSON.stringify(patch))
  }
})

test('个人页与列表一样使用服务端eligible，保留资格阈值供展示', async () => {
  for (const eligible of [true, false]) {
    const { page } = pageHarness('miniprogram/pages/profile/profile.js', { get: async () => success({
      eligible, enabled: false, approvedDays: 24, checkinRate: 80, streak: 7,
      requiredApprovedDays: 24, requiredRate: 80, requiredDays: 7
    }) })
    await page.loadMentorStatus()
    assert.equal(page.data.mentorStatus.eligible, eligible)
    assert.equal(page.data.mentorStatus.requiredRate, 80)
  }
})

test('个人页区分开启、暂停和名额状态，并在刷新后恢复推荐', async () => {
  let data = { enabled: true, eligible: false, approvedDays: 24, checkinRate: 80, streak: 0, remainingSlots: 2, capacity: 3, unmetRequirements: ['当前连续有效打卡不足7天'], recommendationState: 'ineligible' }
  const { page } = pageHarness('miniprogram/pages/profile/profile.js', { get: async () => success(data) })
  await page.loadMentorStatus()
  assert.equal(page.data.mentorStatus.enabled, true)
  assert.equal(page.data.mentorStatus.stateLabel, '资格不足，暂停推荐')
  assert.match(page.data.mentorStatus.unmetRequirements.join(), /连续/)
  data = { ...data, eligible: true, streak: 7, unmetRequirements: [], recommendationState: 'available' }
  await page.loadMentorStatus()
  assert.equal(page.data.mentorStatus.stateLabel, '可被引领推荐')
  data = { ...data, remainingSlots: 0, recommendationState: 'full' }
  await page.loadMentorStatus()
  assert.equal(page.data.mentorStatus.stateLabel, '名额已满，暂停推荐')
  data = { ...data, remainingSlots: null, recommendationState: undefined }
  await page.loadMentorStatus()
  assert.equal(page.data.mentorStatus.stateLabel, '推荐状态待确认')
})

test('开关使用服务端最新资格结果，保存失败恢复原状态', async () => {
  let fail = false
  const initial = { enabled: false, eligible: true, approvedDays: 24, checkinRate: 80, streak: 7, remainingSlots: 2 }
  const { page } = pageHarness('miniprogram/pages/profile/profile.js', {
    get: async () => success(initial),
    put: async () => { if (fail) throw new Error('网络失败'); return success({ ...initial, enabled: true, eligible: false, streak: 0, recommendationState: 'ineligible' }) }
  })
  await page.loadMentorStatus()
  await page.onMentorSwitch({ detail: { value: true } })
  assert.equal(page.data.mentorStatus.stateLabel, '资格不足，暂停推荐')
  fail = true
  await page.onMentorSwitch({ detail: { value: false } })
  assert.equal(page.data.mentorStatus.enabled, true)
  assert.equal(page.data.mentorStatus.stateLabel, '资格不足，暂停推荐')
  assert.equal(page.data.mentorSaving, false)
})

test('状态刷新失败清除旧推荐结论，匹配页可直接进入个人页', async () => {
  const { page } = pageHarness('miniprogram/pages/profile/profile.js', { get: async () => { throw new Error('网络失败') } })
  page.setData({ mentorStatus: { stateLabel: '可被引领推荐' } })
  await page.loadMentorStatus()
  assert.equal(page.data.mentorStatus, null)
  assert.equal(page.data.mentorStatusError, '网络失败')
  const match = pageHarness('miniprogram/pages/partner/partner.js')
  match.page.openMentorSettings()
  assert.equal(match.calls.navigate[0].url, '/pages/profile/profile')
})

test('会员页返回原页面，不重建匹配参数', () => {
  const { page, calls } = pageHarness(vipPath)
  page.goBack()
  assert.equal(calls.back, 1)
  assert.equal(calls.navigate.length, 0)
})
