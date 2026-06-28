const app = getApp()
const request = require('../../utils/request')

Page({
  data: {
    currentTab: 'my',
    myPartners: [],
    matchList: [],
    requests: [],
    blockedPartners: [],
    goalTypes: ['全部', '学习', '运动', '阅读', '早起', '饮食'],
    selectedType: '全部',
    searchKeyword: '',
    matchMode: 'mutual' // mutual=共勉型, lead=引领型
  },

  onLoad() {
    if (app.globalData.isLoggedIn) {
      this.loadMyPartners()
      this.loadRequests()
      this.loadBlockedPartners()
    }
  },

  onShow() {
    if (app.globalData.isLoggedIn) {
      this.loadMyPartners()
      this.loadRequests()
      this.loadBlockedPartners()
      this.loadAllBadges()
    }
  },

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
        this.setData({ myPartners: res.data })
      }
    } catch (err) {
      console.log('加载搭子失败', err)
    }
  },

  async loadMatchList() {
    try {
      const goalType = this.data.selectedType === '全部' ? '' : this.data.selectedType
      const keyword = this.data.searchKeyword || ''
      const matchMode = this.data.matchMode
      console.log('[Partner] loadMatchList:', { goalType, keyword, matchMode })
      wx.showLoading({ title: '匹配中...' })
      const res = await request.get('/api/partner/match', { goalType, keyword, matchMode })
      wx.hideLoading()
      if (res.code === 200 && res.data) {
        console.log('[Partner] 匹配结果:', res.data.length, '人')
        this.setData({ matchList: res.data })
      }
    } catch (err) {
      wx.hideLoading()
      console.log('加载推荐失败', err)
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
    try {
      const res = await request.get('/api/partner/requests')
      if (res.code === 200 && res.data) {
        this.setData({ requests: res.data })
        // 设置搭子TabBar角标（待处理申请数）
        const pendingCount = res.data.filter(r => r.status === 'pending').length
        if (pendingCount > 0) {
          wx.setTabBarBadge({ index: 1, text: String(pendingCount) })
        } else {
          wx.removeTabBarBadge({ index: 1 })
        }
      }
    } catch (err) {
      console.log('加载申请失败', err)
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
    wx.navigateTo({
      url: `/pages/partner-detail/partner-detail?id=${partnerId}`
    })
  },

  viewMatchDetail(e) {
    const userId = e.currentTarget.dataset.id
    wx.navigateTo({
      url: `/pages/partner-detail/partner-detail?id=${userId}&source=match`
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
    try {
      const res = await request.post('/api/partner/apply', { partnerId })
      if (res.code === 200) {
        wx.showToast({ title: '申请已发送', icon: 'success' })
        this.loadMatchList()
      } else {
        wx.showToast({ title: res.message || '发送失败', icon: 'none' })
      }
    } catch (err) {
      wx.showToast({ title: err.message || '发送失败', icon: 'none' })
    }
  },

  async acceptRequest(e) {
    const requestId = e.currentTarget.dataset.id
    try {
      const res = await request.put(`/api/partner/accept/${requestId}`)
      if (res.code === 200) {
        wx.showToast({ title: '已同意', icon: 'success' })
        this.loadRequests()
        this.loadMyPartners()
      }
    } catch (err) {
      wx.showToast({ title: err.message || '操作失败', icon: 'none' })
    }
  },

  async rejectRequest(e) {
    const requestId = e.currentTarget.dataset.id
    try {
      const res = await request.put(`/api/partner/reject/${requestId}`)
      if (res.code === 200) {
        wx.showToast({ title: '已拒绝', icon: 'success' })
        this.loadRequests()
      }
    } catch (err) {
      wx.showToast({ title: err.message || '操作失败', icon: 'none' })
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

  // 加载所有Tab角标
  async loadAllBadges() {
    try {
      const notiRes = await request.get('/api/notification/unread/count')
      if (notiRes.code === 200) {
        if (notiRes.data > 0) {
          wx.setTabBarBadge({ index: 0, text: String(notiRes.data > 99 ? '99+' : notiRes.data) })
        } else {
          wx.removeTabBarBadge({ index: 0 })
        }
      }
    } catch (err) {}
    try {
      const circleRes = await request.get('/api/notification/unread/count/comment')
      if (circleRes.code === 200) {
        if (circleRes.data > 0) {
          wx.setTabBarBadge({ index: 2, text: String(circleRes.data) })
        } else {
          wx.removeTabBarBadge({ index: 2 })
        }
      }
    } catch (err) {}
    try {
      const msgRes = await request.get('/api/notification/unread/count/private_message')
      if (msgRes.code === 200) {
        if (msgRes.data > 0) {
          wx.setTabBarBadge({ index: 3, text: String(msgRes.data) })
        } else {
          wx.removeTabBarBadge({ index: 3 })
        }
      }
    } catch (err) {}
  },

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