const app = getApp()
const request = require('../../utils/request')

Page({
  data: {
    partnerId: null,
    partner: {},
    goals: [],
    totalDays: 0,
    checkinRate: 0,
    loading: true,
    isMatch: false,
    applied: false
  },

  onLoad(options) {
    if (options.id) {
      this.setData({
        partnerId: options.id,
        isMatch: options.source === 'match'
      })
      this.loadPartnerDetail(options.id)
    }
  },

  async loadPartnerDetail(partnerId) {
    this.setData({ loading: true })
    try {
      const res = await request.get(`/api/user/detail/${partnerId}`)
      if (res.code === 200 && res.data) {
        const d = res.data
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
          applied: d.relationStatus === 'pending',
          isMatch: this.data.isMatch && d.relationStatus !== 'partner'
        })
      }
    } catch (err) {
      console.log('加载搭子信息失败', err)
      wx.showToast({ title: '加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  async applyPartner() {
    const { partnerId } = this.data
    try {
      const res = await request.post('/api/partner/apply', { partnerId })
      if (res.code === 200) {
        this.setData({ applied: true })
        wx.showToast({ title: '申请已发送', icon: 'success' })
      } else {
        wx.showToast({ title: res.message || '发送失败', icon: 'none' })
      }
    } catch (err) {
      wx.showToast({ title: err.message || '发送失败', icon: 'none' })
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