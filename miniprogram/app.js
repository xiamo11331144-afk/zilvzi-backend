const envConfig = require('./config/env')

App({
  onLaunch() {
    // 检查登录状态
    const token = wx.getStorageSync('token')
    const userInfo = wx.getStorageSync('userInfo')
    if (token) {
      this.globalData.isLoggedIn = true
      this.globalData.token = token
    }
    if (userInfo && userInfo.id) {
      this.globalData.userId = userInfo.id
      this.globalData.userInfo = userInfo
    }
  },

  globalData: {
    userInfo: null,
    isLoggedIn: false,
    token: null,
    userId: null,
    baseUrl: envConfig.apiBaseUrl
  }
})