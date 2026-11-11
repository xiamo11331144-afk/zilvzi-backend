const app = getApp()
const request = require('../../utils/request')

Page({
  data: {
    mode: 'login', // login 或 register
    loginType: 'phone', // phone, email, username
    phone: '',
    email: '',
    username: '',
    password: '',
    nickname: '',
    avatar: '',
    code: '',
    codeSent: false,
    sendingCode: false,
    submitting: false,
    wechatLoggingIn: false,
    countdown: 60,
    agreed: false,
    timer: null,
    region: [],
    regionText: '',
    province: '',
    city: '',
    district: ''
  },

  switchMode(e) {
    this.setData({ mode: e.currentTarget.dataset.mode })
  },

  switchLoginType(e) {
    this.setData({
      loginType: e.currentTarget.dataset.type,
      code: ''
    })
  },

  onPhoneInput(e) {
    this.setData({ phone: e.detail.value })
  },

  onEmailInput(e) {
    this.setData({ email: e.detail.value })
  },

  onUsernameInput(e) {
    this.setData({ username: e.detail.value })
  },

  onPasswordInput(e) {
    this.setData({ password: e.detail.value })
  },

  onNicknameInput(e) {
    this.setData({ nickname: e.detail.value })
  },

  onRegionChange(e) {
    const region = e.detail.value
    this.setData({
      region: region,
      regionText: region.join(' '),
      province: region[0] || '',
      city: region[1] || '',
      district: region[2] || ''
    })
  },

  onCodeInput(e) {
    this.setData({ code: e.detail.value })
  },

  onAgreementChange(e) {
    this.setData({ agreed: e.detail.value.length > 0 })
  },

  // 选择头像
  chooseAvatar() {
    wx.showActionSheet({
      itemList: ['从相册选择', '使用微信头像'],
      success: (res) => {
        if (res.tapIndex === 0) {
          // 从相册选择
          wx.chooseMedia({
            count: 1,
            mediaType: ['image'],
            success: (res) => {
              this.setData({ avatar: res.tempFiles[0].tempFilePath })
            }
          })
        } else {
          // 使用微信头像（新API）
          wx.chooseAvatar({
            success: (res) => {
              this.setData({ avatar: res.avatarUrl })
            }
          })
        }
      }
    })
  },

  ensureBusinessSuccess(res, fallback) {
    if (!res || res.code !== 200) {
      throw new Error((res && res.message) || fallback)
    }
    return res
  },

  clearCodeTimer() {
    if (this.data.timer) clearInterval(this.data.timer)
    this.setData({ timer: null, codeSent: false, countdown: 60 })
  },

  async sendCode() {
    if (this.data.codeSent || this.data.sendingCode) return

    const { loginType, phone, email } = this.data
    
    if (loginType === 'phone' && !/^1[3-9]\d{9}$/.test(phone)) {
      wx.showToast({ title: '请输入正确的手机号', icon: 'none' })
      return
    }

    if (loginType === 'email' && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      wx.showToast({ title: '请输入正确的邮箱', icon: 'none' })
      return
    }

    this.setData({ sendingCode: true })
    try {
      const target = loginType === 'phone' ? phone : email
      const res = await request.post('/api/user/sendCode', { target, type: loginType })
      this.ensureBusinessSuccess(res, '验证码发送失败')
      wx.showToast({ title: res.message || '验证码已发送', icon: 'success' })
      this.setData({ codeSent: true })
      this.startCountdown()
    } catch (err) {
      wx.showToast({ title: err.message || '发送失败', icon: 'none' })
    } finally {
      this.setData({ sendingCode: false })
    }
  },

  startCountdown() {
    if (this.data.timer) clearInterval(this.data.timer)
    let countdown = 60
    const timer = setInterval(() => {
      countdown--
      if (countdown <= 0) {
        clearInterval(timer)
        this.setData({ timer: null, codeSent: false, countdown: 60 })
      } else {
        this.setData({ countdown })
      }
    }, 1000)
    this.setData({ timer })
  },

  async wechatLogin() {
    if (this.data.wechatLoggingIn) return
    this.setData({ wechatLoggingIn: true })
    try {
      const loginRes = await wx.login()
      const res = await request.post('/api/user/login/wechat', { code: loginRes.code, province: this.data.province, city: this.data.city, district: this.data.district })
      this.ensureBusinessSuccess(res, '微信登录失败')
      if (!res.data || !res.data.token || !res.data.userInfo) throw new Error('登录信息不完整，请重试')
      this.saveLoginInfo(res.data)
    } catch (err) {
      wx.showToast({ title: err.message || '登录失败', icon: 'none' })
    } finally {
      this.setData({ wechatLoggingIn: false })
    }
  },

  async submit() {
    const { mode, loginType, phone, email, username, password, nickname, code, agreed } = this.data

    if (this.data.submitting) return
    if (!agreed) {
      wx.showToast({ title: '请同意用户协议', icon: 'none' })
      return
    }

    this.setData({ submitting: true })
    wx.showLoading({ title: mode === 'login' ? '登录中...' : '注册中...' })

    try {
      let res

      if (mode === 'register') {
        // 注册
        res = await request.post('/api/user/register', {
          username: username || phone || email,
          password,
          phone,
          email,
          nickname,
          province: this.data.province,
          city: this.data.city,
          district: this.data.district
        })
      } else {
        // 登录
        let params = {}
        if (loginType === 'phone') {
          params = { loginType: 'phone', phone, code }
        } else if (loginType === 'email') {
          params = { loginType: 'email', email, code }
        } else {
          params = { username, password }
        }
        res = await request.post('/api/user/login', params)
      }

      this.ensureBusinessSuccess(res, mode === 'login' ? '登录失败' : '注册失败')
      if (!res.data || !res.data.token || !res.data.userInfo) throw new Error('登录信息不完整，请重试')
      wx.showToast({ title: mode === 'login' ? '登录成功' : '注册成功', icon: 'success' })
      this.saveLoginInfo(res.data)
    } catch (err) {
      wx.showToast({ title: err.message || '操作失败', icon: 'none' })
    } finally {
      wx.hideLoading()
      this.setData({ submitting: false })
    }
  },

  saveLoginInfo(data) {
    app.globalData.userInfo = data.userInfo
    app.globalData.isLoggedIn = true
    app.globalData.token = data.token
    if (data.userInfo && data.userInfo.id) {
      app.globalData.userId = data.userInfo.id
    }
    wx.setStorageSync('token', data.token)
    wx.setStorageSync('userInfo', data.userInfo)
    
    setTimeout(() => {
      if (getCurrentPages().length > 1) {
        wx.navigateBack()
      } else {
        wx.switchTab({ url: '/pages/index/index' })
      }
    }, 1500)
  },

  onUnload() {
    if (this.data.timer) {
      clearInterval(this.data.timer)
    }
  }
})