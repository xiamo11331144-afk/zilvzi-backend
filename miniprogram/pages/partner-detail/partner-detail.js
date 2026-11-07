const app = getApp()
const request = require('../../utils/request')
const { handleVipRequired } = require('../../utils/vip')

Page({
  data: {
    partnerId: null,
    partner: {},
    goals: [],
    totalDays: 0,
    checkinRate: 0,
    loading: true,
    detailError: '',
    isMatch: false,
    matchMode: 'mutual',
    relationMatchMode: 'legacy',
    relationStatus: '',
    mentorStatus: null,
    leadAvailable: null,
    leadUnavailableReason: '',
    leadDataComplete: false,
    applied: false,
    applyPending: false
  },

  onLoad(options) {
    if (options.id) {
      const isMatch = options.source === 'match'
      const matchMode = options.matchMode === 'lead' ? 'lead' : 'mutual'
      this.setData({
        partnerId: options.id,
        isMatch,
        matchMode: isMatch ? matchMode : 'mutual',
        relationMatchMode: options.relationMatchMode === 'lead' ? 'lead' : (options.relationMatchMode === 'mutual' ? 'mutual' : 'legacy')
      })
      this.loadPartnerDetail(options.id)
    }
  },

  openVip() { wx.navigateTo({ url: '/pages/vip/vip?from=match' }) },

  retryDetail() {
    if (this.data.partnerId) this.loadPartnerDetail(this.data.partnerId)
  },

  async loadPartnerDetail(partnerId) {
    this.setData({ loading: true, detailError: '' })
    try {
      const res = await request.get(`/api/user/detail/${partnerId}`)
      if (res.code !== 200 || !res.data || !res.data.user) throw new Error(res.message || '详情加载失败')
      const d = res.data
      const mentor = d.mentorStatus || null
      const hasNumber = (obj, key) => obj && obj[key] !== null && obj[key] !== undefined && obj[key] !== '' && Number.isFinite(Number(obj[key]))
      const hasFlag = (obj, key) => obj && (typeof obj[key] === 'boolean' || obj[key] === 0 || obj[key] === 1)
      const leadDataComplete = !!mentor
        && hasFlag(mentor, 'enabled') && hasFlag(mentor, 'eligible')
        && hasNumber(mentor, 'approvedDays') && hasNumber(mentor, 'checkinRate')
        && hasNumber(mentor, 'streak') && hasNumber(mentor, 'requiredDays')
        && hasNumber(mentor, 'requiredApprovedDays') && hasNumber(mentor, 'requiredRate')
        && hasNumber(mentor, 'capacity') && hasNumber(mentor, 'remainingSlots')
        && hasNumber(mentor, 'occupiedSlots') && hasNumber(mentor, 'pendingCount')
        && hasNumber(mentor, 'acceptedCount')
      const leadAvailable = d.leadAvailable === true
      const relationMatchMode = d.relationMatchMode === 'lead' || d.relationMatchMode === 'mutual'
        ? d.relationMatchMode : this.data.relationMatchMode
      const relationStatus = d.relationStatus
      this.setData({
        partner: {
          nickname: d.user.nickname,
          avatar: d.user.avatar,
          level: d.user.level || 1,
          bio: d.user.bio || '',
          province: d.user.province || '',
          city: d.user.city || '',
          district: d.user.district || ''
        },
        goals: d.goals || [],
        totalDays: d.totalDays || 0,
        checkinRate: d.checkinRate || 0,
        mentorStatus: mentor,
        leadDataComplete,
        leadAvailable: leadDataComplete && Object.prototype.hasOwnProperty.call(d, 'leadAvailable') ? leadAvailable : null,
        leadUnavailableReason: typeof d.leadUnavailableReason === 'string' && d.leadUnavailableReason
          ? d.leadUnavailableReason
          : (leadDataComplete ? (leadAvailable ? '' : '当前不满足引领申请条件或引领名额已满') : '引领状态暂不可用，请刷新重试'),
        relationMatchMode,
        relationStatus,
        applied: relationStatus === 'pending',
        isMatch: this.data.isMatch && relationStatus !== 'partner'
      })
    } catch (err) {
      this.setData({ detailError: err.message || '详情加载失败' })
      wx.showToast({ title: err.message || '详情加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  async applyPartner() {
    const { partnerId, matchMode, applied, applyPending, leadAvailable } = this.data
    if (!partnerId || applied || applyPending) return
    if (matchMode === 'lead' && leadAvailable !== true) {
      wx.showToast({ title: this.data.leadUnavailableReason || '引领申请条件尚未确认', icon: 'none' })
      return
    }
    this.setData({ applyPending: true })
    try {
      const res = await request.post('/api/partner/apply', { partnerId, matchMode })
      if (handleVipRequired(res)) return
      if (res.code !== 200) throw new Error(res.message || '发送失败')
      this.setData({ applied: true })
      wx.showToast({ title: '申请已发送', icon: 'success' })
      this.loadPartnerDetail(partnerId)
    } catch (err) {
      wx.showToast({ title: err.message || '发送失败，请刷新后重试', icon: 'none' })
      this.loadPartnerDetail(partnerId)
    } finally {
      this.setData({ applyPending: false })
    }
  },

  goMessage() {
    const { partnerId, partner } = this.data
    wx.navigateTo({
      url: `/pages/message/message?partnerId=${partnerId}&nickname=${encodeURIComponent(partner.nickname || '')}&avatar=${encodeURIComponent(partner.avatar || '')}`
    })
  },

  async remindPartner() {
    const { partnerId } = this.data
    try {
      const res = await request.post(`/api/partner/remind/${partnerId}`)
      if (res.code === 200) {
        wx.showToast({ title: '提醒已发送', icon: 'success' })
      }
    } catch (err) {
      wx.showToast({ title: '提醒失败', icon: 'none' })
    }
  },

  removePartner() {
    const { partnerId } = this.data
    wx.showModal({
      title: '确认解除',
      content: '确定要解除与该搭子的关系吗？',
      success: async (res) => {
        if (res.confirm) {
          try {
            const result = await request.del(`/api/partner/remove/${partnerId}`)
            if (result.code === 200) {
              wx.showToast({ title: '已解除', icon: 'success' })
              setTimeout(() => { wx.navigateBack() }, 1500)
            }
          } catch (err) {
            wx.showToast({ title: err.message || '操作失败', icon: 'none' })
          }
        }
      }
    })
  },

  blockPartner() {
    const { partnerId } = this.data
    wx.showModal({
      title: '确认拉黑',
      content: '拉黑后将无法收到该用户的搭子申请和消息',
      success: async (res) => {
        if (res.confirm) {
          try {
            const result = await request.post(`/api/partner/block/${partnerId}`)
            if (result.code === 200) {
              wx.showToast({ title: '已拉黑', icon: 'success' })
              setTimeout(() => { wx.navigateBack() }, 1500)
            }
          } catch (err) {
            wx.showToast({ title: err.message || '操作失败', icon: 'none' })
          }
        }
      }
    })
  }
})