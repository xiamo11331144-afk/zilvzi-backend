const app = getApp()
const request = require('../../utils/request')
const badges = require('../../utils/badges')
const { handleVipRequired } = require('../../utils/vip')

Page({
  data: {
    badgeCounts: badges.emptyCounts(),
    badgeError: '',
    currentTab: 'my',
    myPartners: [],
    matchList: [],
    requests: [],
    blockedPartners: [],
    goalTypes: ['全部', '学习', '运动', '阅读', '早起', '饮食'],
    selectedType: '全部',
    searchKeyword: '',
    matchMode: 'mutual',
    matchLoading: false,
    matchError: '',
    requestsLoading: false,
    requestsError: '',
    applying: {},
    requestActions: {}
  },

  onLoad() {
    if (app.globalData.isLoggedIn) {
      this.loadMyPartners()
      this.loadRequests()
      this.loadBlockedPartners()
    }
  },

  onHide() { badges.unbind(this) },
  onUnload() { badges.unbind(this) },

  onShow() {
    badges.bind(this)
    if (app.globalData.isLoggedIn) {
      this.loadMyPartners()
      this.loadRequests()
      this.loadBlockedPartners()
    }
  },

  openVip() { wx.navigateTo({ url: '/pages/vip/vip?from=match' }) },

  switchTab(e) {
    const tab = e.currentTarget.dataset.tab
    this.setData({ currentTab: tab })
    if (tab === 'match') {
      this.loadMatchList()
    }
  },

  async loadMyPartners() {
    try {
      const res = await request.get('/api/partner/list')
      if (res.code === 200 && res.data) {
        this.setData({ myPartners: res.data.map(item => ({
          ...item,
          relationMatchMode: item.matchMode === 'lead' ? 'lead' : (item.matchMode === 'mutual' ? 'mutual' : 'legacy')
        })) })
      }
    } catch (err) {
      console.log('加载搭子失败', err)
    }
  },

  async loadMatchList() {
    const requestId = (this._matchRequestId || 0) + 1
    this._matchRequestId = requestId
    this.setData({ matchLoading: true, matchError: '', matchList: [] })
    const goalType = this.data.selectedType === '全部' ? '' : this.data.selectedType
    const keyword = this.data.searchKeyword || ''
    const matchMode = this.data.matchMode
    try {
      const res = await request.get('/api/partner/match', { goalType, keyword, matchMode })
      if (requestId !== this._matchRequestId) return
      if (res.code !== 200 || !Array.isArray(res.data)) throw new Error(res.message || '推荐列表加载失败')
      this.setData({ matchList: res.data.map(item => ({
        ...item,
        mentorEnabled: item.mentorEnabled === true || item.mentorEnabled === 1,
        mentorEligible: item.mentorEligible === true,
        leadDataComplete: (item.mentorEnabled === true || item.mentorEnabled === 1)
          && item.mentorEligible === true
          && item.approvedDays !== null && item.approvedDays !== undefined && Number.isFinite(Number(item.approvedDays))
          && item.checkinRate !== null && item.checkinRate !== undefined && Number.isFinite(Number(item.checkinRate))
          && item.streak !== null && item.streak !== undefined && Number.isFinite(Number(item.streak))
          && item.remainingSlots !== null && item.remainingSlots !== undefined && Number.isFinite(Number(item.remainingSlots)),
        leadCanApply: (item.mentorEnabled === true || item.mentorEnabled === 1)
          && item.mentorEligible === true
          && Number(item.approvedDays) >= 24 && Number(item.checkinRate) >= 80
          && Number(item.streak) >= 7 && Number(item.remainingSlots) > 0
          && item.checkinRate !== null && item.checkinRate !== undefined && Number.isFinite(Number(item.checkinRate))
      })) })
    } catch (err) {
      if (requestId !== this._matchRequestId) return
      this.setData({ matchError: err.message || '推荐列表加载失败' })
      wx.showToast({ title: err.message || '推荐列表加载失败', icon: 'none' })
    } finally {
      if (requestId === this._matchRequestId) this.setData({ matchLoading: false })
    }
  },

  onSearchInput(e) {
    this.setData({ searchKeyword: e.detail.value })
  },

  doSearch() {
    this.loadMatchList()
  },

  switchMatchMode(e) {
    const mode = e.currentTarget.dataset.mode
    console.log('[Partner] switchMatchMode:', mode)
    if (mode === this.data.matchMode) return
    this.setData({ matchMode: mode })
    wx.showToast({ title: mode === 'mutual' ? '已切换共勉型' : '已切换引领型', icon: 'none', duration: 1000 })
    this.loadMatchList()
  },

  async loadRequests() {
    this.setData({ requestsLoading: true, requestsError: '' })
    try {
      const res = await request.get('/api/partner/requests')
      if (res.code !== 200 || !Array.isArray(res.data)) throw new Error(res.message || '申请列表加载失败')
      const requests = res.data.map(item => ({
        ...item,
        matchMode: item.matchMode === 'lead' ? 'lead' : (item.matchMode === 'mutual' ? 'mutual' : 'legacy')
      }))
      this.setData({ requests })
      badges.refresh(true)
    } catch (err) {
      this.setData({ requestsError: err.message || '申请列表加载失败' })
      wx.showToast({ title: err.message || '申请列表加载失败', icon: 'none' })
    } finally {
      this.setData({ requestsLoading: false })
    }
  },

  selectType(e) {
    this.setData({ selectedType: e.currentTarget.dataset.type })
    this.loadMatchList()
  },

  viewPartner(e) {
    const id = e.currentTarget.dataset.id
    const partner = this.data.myPartners.find(p => p.id === id)
    const partnerId = partner ? partner.partnerId : id
    const mode = partner ? partner.relationMatchMode : 'legacy'
    wx.navigateTo({
      url: `/pages/partner-detail/partner-detail?id=${partnerId}&source=relation&relationMatchMode=${mode}`
    })
  },

  viewMatchDetail(e) {
    const userId = e.currentTarget.dataset.id
    wx.navigateTo({
      url: `/pages/partner-detail/partner-detail?id=${userId}&source=match&matchMode=${this.data.matchMode}`
    })
  },

  async remindPartner(e) {
    const partnerId = e.currentTarget.dataset.id
    try {
      const res = await request.post(`/api/partner/remind/${partnerId}`)
      if (res.code === 200) {
        wx.showToast({ title: '提醒已发送', icon: 'success' })
      }
    } catch (err) {
      wx.showToast({ title: err.message || '发送失败', icon: 'none' })
    }
  },

  async sendRequest(e) {
    const partnerId = e.currentTarget.dataset.id
    const matchMode = e.currentTarget.dataset.mode || this.data.matchMode
    const candidate = this.data.matchList.find(item => String(item.id) === String(partnerId))
    if (this.data.applying[partnerId]) return
    if (matchMode === 'lead' && (!candidate || candidate.leadCanApply !== true)) {
      wx.showToast({ title: '引领资格或名额数据未确认，请刷新后重试', icon: 'none' })
      return
    }
    this.setData({ [`applying.${partnerId}`]: true, matchList: this.data.matchList.map(item => String(item.id) === String(partnerId) ? { ...item, applying: true } : item) })
    try {
      const res = await request.post('/api/partner/apply', { partnerId, matchMode })
      if (handleVipRequired(res)) return
      if (res.code !== 200) throw new Error(res.message || '发送失败')
      wx.showToast({ title: '申请已发送', icon: 'success' })
      await Promise.all([this.loadMatchList(), this.loadRequests()])
    } catch (err) {
      wx.showToast({ title: err.message || '发送失败', icon: 'none' })
      if (matchMode === 'lead') this.loadMatchList()
    } finally {
      this.setData({ [`applying.${partnerId}`]: false, matchList: this.data.matchList.map(item => String(item.id) === String(partnerId) ? { ...item, applying: false } : item) })
    }
  },

  setRequestBusy(requestId, busy) {
    this.setData({
      [`requestActions.${requestId}`]: busy,
      requests: this.data.requests.map(item => String(item.id) === String(requestId) ? { ...item, actionBusy: busy } : item)
    })
  },

  async acceptRequest(e) {
    const requestId = e.currentTarget.dataset.id
    if (this.data.requestActions[requestId]) return
    this.setRequestBusy(requestId, true)
    try {
      const res = await request.put(`/api/partner/accept/${requestId}`)
      if (res.code !== 200) throw new Error(res.message || '操作失败')
      wx.showToast({ title: '已同意', icon: 'success' })
      await Promise.all([this.loadRequests(), this.loadMyPartners()])
    } catch (err) {
      wx.showToast({ title: err.message || '操作失败，请刷新后重试', icon: 'none' })
      this.loadRequests()
    } finally {
      this.setRequestBusy(requestId, false)
    }
  },

  async rejectRequest(e) {
    const requestId = e.currentTarget.dataset.id
    if (this.data.requestActions[requestId]) return
    this.setRequestBusy(requestId, true)
    try {
      const res = await request.put(`/api/partner/reject/${requestId}`)
      if (res.code !== 200) throw new Error(res.message || '操作失败')
      wx.showToast({ title: '已拒绝', icon: 'success' })
      await this.loadRequests()
    } catch (err) {
      wx.showToast({ title: err.message || '操作失败，请刷新后重试', icon: 'none' })
      this.loadRequests()
    } finally {
      this.setRequestBusy(requestId, false)
    }
  },

  // 拉黑用户
  blockUser(e) {
    const partnerId = e.currentTarget.dataset.id
    wx.showModal({
      title: '确认拉黑',
      content: '拉黑后将无法收到该用户的搭子申请和消息',
      success: async (res) => {
        if (res.confirm) {
          try {
            const result = await request.post(`/api/partner/block/${partnerId}`)
            if (result.code === 200) {
              wx.showToast({ title: '已拉黑', icon: 'success' })
              this.loadRequests()
              this.loadMyPartners()
            }
          } catch (err) {
            wx.showToast({ title: err.message || '操作失败', icon: 'none' })
          }
        }
      }
    })
  },

  sendMessage(e) {
    const { id, nickname, avatar } = e.currentTarget.dataset
    wx.navigateTo({
      url: `/pages/message/message?partnerId=${id}&nickname=${encodeURIComponent(nickname)}&avatar=${encodeURIComponent(avatar || '')}`
    })
  },

  retryBadges() { return badges.refresh(true) },

  // 加载拉黑列表
  async loadBlockedPartners() {
    try {
      const res = await request.get('/api/partner/blocked')
      if (res.code === 200 && res.data) {
        this.setData({ blockedPartners: res.data })
      }
    } catch (err) {}
  },

  // 解除拉黑
  unblockUser(e) {
    const partnerId = e.currentTarget.dataset.id
    wx.showModal({
      title: '解除拉黑',
      content: '确定要解除拉黑吗？',
      success: async (res) => {
        if (res.confirm) {
          try {
            const result = await request.post(`/api/partner/unblock/${partnerId}`)
            if (result.code === 200) {
              wx.showToast({ title: '已解除', icon: 'success' })
              this.loadBlockedPartners()
              this.loadRequests()
            }
          } catch (err) {
            wx.showToast({ title: err.message || '操作失败', icon: 'none' })
          }
        }
      }
    })
  }
})