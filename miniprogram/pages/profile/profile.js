const app = getApp()
const request = require('../../utils/request')
const badges = require('../../utils/badges')
const { formatVipUntil } = require('../../utils/vip')

Page({
  data: {
    badgeCounts: badges.emptyCounts(),
    badgeError: '',
    isLoggedIn: false,
    userInfo: {},
    stats: { checkinDays: 0, partners: 0, medals: 0, circles: 0 },
    showInfoModal: false,
    regionValue: [],
    mentorStatus: null,
    mentorStatusLoading: false,
    mentorStatusError: '',
    mentorSaving: false,
    vipSummary: '会员状态待查询'
  },

  onLoad() { this.checkLoginStatus() },
  onHide() { badges.unbind(this) },
  onUnload() { badges.unbind(this) },

  onShow() {
    badges.bind(this)
    this.checkLoginStatus()
    if (this.data.isLoggedIn) { this.loadUserInfo(); this.loadStats(); this.loadMentorStatus(); this.loadVipStatus() }
    else this.setData({ vipSummary: '登录后查看会员状态' })
  },

  openVip() {
    wx.navigateTo({ url: this.data.isLoggedIn ? '/pages/vip/vip' : '/pages/login/login' })
  },

  async loadVipStatus() {
    this.setData({ vipSummary: '正在查询会员状态…' })
    try {
      const res = await request.get('/api/vip/status')
      if (res.code !== 200 || !res.data) throw new Error(res.message || '会员状态查询失败')
      this.setData({ vipSummary: res.data.isVip === true
        ? `VIP 有效至 ${formatVipUntil(res.data.vipUntil)}` : '当前非 VIP · 共勉匹配免费' })
    } catch (err) {
      this.setData({ vipSummary: '会员状态查询失败，点击进入重试' })
    }
  },

  retryBadges() { return badges.refresh(true) },

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

  async loadMentorStatus() {
    if (this.data.mentorStatusLoading) return
    this.setData({ mentorStatusLoading: true, mentorStatusError: '', mentorStatus: null })
    try {
      const res = await request.get('/api/user/mentor/status')
      if (res.code !== 200 || !res.data) throw new Error(res.message || '认证状态暂不可用')
      const d = res.data
      const asNumber = value => value !== null && value !== undefined && value !== '' && Number.isFinite(Number(value)) ? Number(value) : null
      const requiredDays = asNumber(d.requiredDays) || 7
      const requiredApprovedDays = asNumber(d.requiredApprovedDays) || 24
      const requiredRate = asNumber(d.requiredRate) || 80
      const approvedDays = asNumber(d.approvedDays)
      const checkinRate = asNumber(d.checkinRate)
      const streak = asNumber(d.streak)
      const eligible = (d.eligible === true || d.eligible === 1)
        && approvedDays !== null && approvedDays >= requiredApprovedDays
        && checkinRate !== null && checkinRate >= requiredRate
        && streak !== null && streak >= requiredDays
      const status = {
        enabled: d.enabled === true || d.enabled === 1,
        eligible,
        approvedDays,
        checkinRate,
        streak,
        requiredDays,
        requiredApprovedDays,
        requiredRate,
        capacity: asNumber(d.capacity),
        occupiedSlots: asNumber(d.occupiedSlots),
        remainingSlots: asNumber(d.remainingSlots),
        pendingCount: asNumber(d.pendingCount),
        acceptedCount: asNumber(d.acceptedCount)
      }
      this.setData({ mentorStatus: status })
    } catch (err) {
      this.setData({ mentorStatusError: err.message || '认证状态加载失败' })
      wx.showToast({ title: err.message || '认证状态加载失败', icon: 'none' })
    } finally {
      this.setData({ mentorStatusLoading: false })
    }
  },

  async onMentorSwitch(e) {
    const current = this.data.mentorStatus
    if (this.data.mentorSaving || !current) return
    const enabled = !!e.detail.value
    if (enabled && current.eligible !== true) {
      this.setData({ mentorStatus: { ...current, enabled: current.enabled === true } })
      wx.showToast({ title: '暂不符合开启条件', icon: 'none' })
      return
    }
    this.setData({ mentorSaving: true, mentorStatus: { ...current, enabled } })
    try {
      const res = await request.put('/api/user/mentor', { enabled })
      if (res.code !== 200 || !res.data) throw new Error(res.message || '设置失败')
      const d = res.data
      this.setData({ mentorStatus: {
        ...current,
        ...d,
        enabled: d.enabled === true || d.enabled === 1,
        eligible: d.eligible === true || d.eligible === 1
      } })
      wx.showToast({ title: enabled ? '已开启引领' : '已关闭引领', icon: 'success' })
    } catch (err) {
      this.setData({ mentorStatus: current })
      wx.showToast({ title: err.message || '设置失败', icon: 'none' })
    } finally {
      this.setData({ mentorSaving: false })
    }
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
          app.globalData.isLoggedIn = false; app.globalData.token = null; app.globalData.userInfo = null; app.globalData.userId = null
          badges.reset()
          wx.removeStorageSync('token'); wx.removeStorageSync('userInfo')
          this.setData({ isLoggedIn: false, userInfo: {} })
          wx.showToast({ title: '已退出', icon: 'success' })
        }
      }
    })
  }
})