const app = getApp()
const request = require('../../utils/request')

Page({
  data: {
    isLoggedIn: false,
    userInfo: {},
    stats: { checkinDays: 0, partners: 0, medals: 0, circles: 0 },
    showInfoModal: false,
    regionValue: []
  },

  onLoad() { this.checkLoginStatus() },
  onShow() {
    this.checkLoginStatus()
    if (this.data.isLoggedIn) { this.loadUserInfo(); this.loadStats(); this.loadUnreadCount(); this.loadAllBadges() }
  },

  async loadUnreadCount() {
    try {
      const res = await request.get('/api/notification/unread/count/private_message')
      if (res.code === 200 && res.data !== undefined) {
        if (res.data > 0) {
          wx.setTabBarBadge({ index: 3, text: String(res.data) })
        } else {
          wx.removeTabBarBadge({ index: 3 })
        }
      }
    } catch (err) {}
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
      const partnerRes = await request.get('/api/partner/requests')
      if (partnerRes.code === 200 && partnerRes.data) {
        const pendingCount = partnerRes.data.filter(r => r.status === 'pending').length
        if (pendingCount > 0) {
          wx.setTabBarBadge({ index: 1, text: String(pendingCount) })
        } else {
          wx.removeTabBarBadge({ index: 1 })
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
  },

  checkLoginStatus() {
    const isLoggedIn = app.globalData.isLoggedIn
    const userInfo = app.globalData.userInfo || {}
    this.setData({ isLoggedIn, userInfo })
  },

  async loadUserInfo() {
    try {
      const res = await request.get('/api/user/info')
      if (res.code === 200 && res.data) {
        this.setData({ userInfo: { ...this.data.userInfo, ...res.data } })
      }
    } catch (err) {}
  },

  async loadStats() {
    try {
      const results = await Promise.allSettled([
        request.get('/api/checkin/stats'),
        request.get('/api/partner/list'),
        request.get('/api/achievement/list'),
        request.get('/api/circle/my')
      ])
      const getValue = (r) => r.status === 'fulfilled' && r.value && r.value.data ? r.value.data : []
      const statsData = results[0].status === 'fulfilled' && results[0].value ? results[0].value.data : {}
      this.setData({ stats: {
        checkinDays: (statsData && statsData.total) || 0,
        partners: getValue(results[1]).length,
        medals: getValue(results[2]).length,
        circles: getValue(results[3]).length
      }})
    } catch (err) {}
  },

  editProfile() {
    wx.showActionSheet({
      itemList: ['修改昵称', '修改头像'],
      success: (res) => {
        if (res.tapIndex === 0) this.changeNickname()
        else if (res.tapIndex === 1) this.changeAvatar()
      }
    })
  },

  changeNickname() {
    wx.showModal({
      title: '修改昵称', editable: true, placeholderText: '请输入新昵称',
      success: async (res) => {
        if (res.confirm && res.content) {
          try {
            await request.put('/api/user/info', { nickname: res.content })
            this.setData({ 'userInfo.nickname': res.content })
            app.globalData.userInfo.nickname = res.content
            wx.setStorageSync('userInfo', app.globalData.userInfo)
            wx.showToast({ title: '修改成功', icon: 'success' })
          } catch (err) { wx.showToast({ title: '修改失败', icon: 'none' }) }
        }
      }
    })
  },

  changeAvatar() {
    wx.showActionSheet({
      itemList: ['从相册选择', '使用微信头像'],
      success: (res) => {
        if (res.tapIndex === 0) {
          wx.chooseMedia({ count: 1, mediaType: ['image'], success: async (res) => {
            wx.showLoading({ title: '上传中...' })
            wx.uploadFile({
              url: request.UPLOAD_URL, filePath: res.tempFiles[0].tempFilePath, name: 'file',
              header: { 'Authorization': 'Bearer ' + app.globalData.token },
              success: async (uploadRes) => {
                const d = JSON.parse(uploadRes.data)
                if (d.code === 200) {
                  await request.put('/api/user/info', { avatar: d.data.url })
                  this.setData({ 'userInfo.avatar': d.data.url })
                  app.globalData.userInfo.avatar = d.data.url
                  wx.setStorageSync('userInfo', app.globalData.userInfo)
                  wx.showToast({ title: '修改成功', icon: 'success' })
                }
                wx.hideLoading()
              }
            })
          }})
        } else {
          wx.chooseAvatar({ success: async (res) => {
            await request.put('/api/user/info', { avatar: res.avatarUrl })
            this.setData({ 'userInfo.avatar': res.avatarUrl })
            app.globalData.userInfo.avatar = res.avatarUrl
            wx.setStorageSync('userInfo', app.globalData.userInfo)
            wx.showToast({ title: '修改成功', icon: 'success' })
          }})
        }
      }
    })
  },

  changeLocation() {
    // 已改为弹窗内 picker，此方法保留兼容
  },

  async onRegionChange(e) {
    const region = e.detail.value
    if (!region || region.length < 2) return
    const province = region[0]
    const city = region[1]
    const district = region[2] || ''
    try {
      const res = await request.put('/api/user/info', { province, city, district })
      this.setData({
        'userInfo.province': province,
        'userInfo.city': city,
        'userInfo.district': district,
        regionValue: [province, city, district]
      })
      Object.assign(app.globalData.userInfo, { province, city, district })
      wx.setStorageSync('userInfo', app.globalData.userInfo)
      wx.showToast({ title: '地区已更新', icon: 'success' })
    } catch (err) {
      wx.showToast({ title: '修改失败', icon: 'none' })
    }
  },

  goToPage(e) {
    if (!this.data.isLoggedIn) { wx.showToast({ title: '请先登录', icon: 'none' }); return }
    wx.navigateTo({ url: e.currentTarget.dataset.url })
  },

  showInfo() {
    const u = this.data.userInfo
    this.setData({
      showInfoModal: true,
      regionValue: u.province ? [u.province, u.city || '', u.district || ''] : []
    })
  },

  hideInfo() {
    this.setData({ showInfoModal: false })
  },

  showAbout() {
    wx.showModal({ title: '关于自律搭子', content: '版本：1.0.0\n让习惯养成更简单', showCancel: false })
  },

  goLogin() { wx.navigateTo({ url: '/pages/login/login' }) },

  logout() {
    wx.showModal({
      title: '提示', content: '确定要退出登录吗？',
      success: (res) => {
        if (res.confirm) {
          app.globalData.isLoggedIn = false; app.globalData.token = null; app.globalData.userInfo = null
          wx.removeStorageSync('token'); wx.removeStorageSync('userInfo')
          this.setData({ isLoggedIn: false, userInfo: {} })
          wx.showToast({ title: '已退出', icon: 'success' })
        }
      }
    })
  }
})