const app = getApp()
const request = require('../../utils/request')
const { formatVipUntil } = require('../../utils/vip')

Page({
  data: {
    loading: true,
    busy: false,
    error: '',
    status: null,
    untilText: '',
    plan: null,
    priceText: '',
    order: null,
    notice: '',
    fromMatch: false
  },

  onLoad(options) {
    this.setData({ fromMatch: options.from === 'match' })
  },

  onShow() {
    this._active = true
    if (!app.globalData.isLoggedIn) {
      this.setData({ loading: false, busy: false, status: null, plan: null, order: null, notice: '', error: '请先登录后查看会员状态' })
      return
    }
    const identity = app.globalData.userId || app.globalData.token || ''
    if (this._identity !== undefined && this._identity !== identity) {
      this.setData({ order: null, notice: '', status: null, plan: null })
    }
    this._identity = identity
    this.loadMembership()
  },

  onUnload() { this._active = false },

  async loadMembership() {
    if (this._loading) return
    this._loading = true
    this.setData({ loading: true, error: '', plan: null, status: null })
    try {
      const [status, plan] = await Promise.all([
        request.get('/api/vip/status'),
        request.get('/api/vip/plan')
      ])
      if (status.code !== 200 || !status.data || plan.code !== 200 || !plan.data) {
        throw new Error(status.code !== 200 ? status.message : (plan.message || '会员信息加载失败'))
      }
      if (!this._active) return
      const p = plan.data
      const usablePlan = Number.isInteger(p.durationDays) && p.durationDays > 0
        && Number.isInteger(p.priceFen) && p.priceFen >= 0 && p.currency === 'CNY'
      if (!usablePlan) throw new Error('会员套餐暂不可用，请稍后重试')
      this.setData({
        status: status.data,
        untilText: formatVipUntil(status.data.vipUntil),
        plan: { ...p, mockEnabled: p.mockEnabled === true && p.paymentMode === 'mock' },
        priceText: (p.priceFen / 100).toFixed(2)
      })
      if (this.data.order) await this.refreshOrder()
    } catch (err) {
      if (this._active) this.setData({ error: err.message || '会员信息加载失败' })
    } finally {
      this._loading = false
      if (this._active) this.setData({ loading: false })
    }
  },

  async refreshOrder() {
    const orderNo = this.data.order && this.data.order.orderNo
    if (!orderNo) return
    const res = await request.get(`/api/vip/orders/${encodeURIComponent(orderNo)}`)
    if (res.code !== 200 || !res.data) throw new Error(res.message || '订单状态查询失败')
    if (this._active) this.setData({ order: res.data })
  },

  async createTestOrder() {
    if (this.data.busy || this.data.loading || !this.data.plan || !this.data.plan.mockEnabled) return
    // 创建和确认分两步，重复点击不会创建当前页面的多笔待确认订单。
    if (this.data.order) return
    this.setData({ busy: true, error: '', notice: '' })
    try {
      const res = await request.post('/api/vip/orders')
      if (res.code !== 200 || !res.data) throw new Error(res.message || '测试订单创建失败')
      if (this._active) this.setData({ order: res.data, notice: '测试订单已创建，尚未确认；不会扣款。' })
    } catch (err) {
      if (this._active) this.setData({ error: err.message || '测试订单创建失败' })
    } finally {
      if (this._active) this.setData({ busy: false })
    }
  },

  confirmTestOrder() {
    if (this.data.busy || !this.data.order || this.data.order.status !== 'PENDING'
      || !this.data.plan || !this.data.plan.mockEnabled) return
    // 在弹窗期间也锁住按钮，避免出现多次确认。
    this.setData({ busy: true })
    wx.showModal({
      title: '确认模拟开通',
      content: '这是开发测试：不发起真实付款、不扣款。服务器会写入测试会员有效期，仅用于验证引领申请权限。',
      confirmText: '模拟开通',
      success: res => {
        if (res.confirm) this.submitTestConfirmation()
        else if (this._active) this.setData({ busy: false })
      },
      fail: () => { if (this._active) this.setData({ busy: false }) }
    })
  },

  async submitTestConfirmation() {
    const orderNo = this.data.order.orderNo
    this.setData({ error: '', notice: '' })
    try {
      const res = await request.post(`/api/vip/orders/${encodeURIComponent(orderNo)}/mock-confirm`)
      if (res.code !== 200 || !res.data || res.data.status !== 'TEST_CONFIRMED') {
        throw new Error(res.message || '模拟确认失败，请查询订单状态后重试')
      }
      if (!this._active) return
      this.setData({ order: res.data, notice: '模拟开通已确认，未发生真实付款。会员状态以服务器查询结果为准。' })
      await this.loadMembership()
    } catch (err) {
      if (this._active) this.setData({ error: err.message || '模拟确认结果未知，请刷新查询后重试' })
    } finally {
      if (this._active) this.setData({ busy: false })
    }
  },

  goLogin() { wx.navigateTo({ url: '/pages/login/login' }) },

  goBack() {
    if (getCurrentPages().length > 1) wx.navigateBack()
    else wx.switchTab({ url: '/pages/partner/partner' })
  }
})
