/**
 * 网络请求工具类
 */
const envConfig = require('../config/env')

const BASE_URL = envConfig.baseUrl
const UPLOAD_URL = envConfig.uploadUrl
const SCHEDULE_UPLOAD_URL = envConfig.scheduleUploadUrl

let requestCount = 0
let isRedirectingToLogin = false

const getAppSafe = () => {
  try { return getApp() } catch (e) { return { globalData: { token: null, isLoggedIn: false } } }
}

const handleUnauthorized = () => {
  if (isRedirectingToLogin) return
  isRedirectingToLogin = true

  const app = getAppSafe()
  wx.removeStorageSync('token')
  wx.removeStorageSync('userInfo')
  app.globalData.isLoggedIn = false
  app.globalData.token = null
  app.globalData.userInfo = null

  const pages = getCurrentPages()
  const currentPage = pages[pages.length - 1]
  if (currentPage && currentPage.route === 'pages/login/login') {
    isRedirectingToLogin = false
    return
  }

  wx.navigateTo({ url: '/pages/login/login' })
  setTimeout(() => { isRedirectingToLogin = false }, 2000)
}

const request = (url, method = 'GET', data = {}) => {
  return new Promise((resolve, reject) => {
    const app = getAppSafe()
    const token = app.globalData.token
    const fullUrl = `${BASE_URL}${url}`
    const requestId = ++requestCount

    console.log(`[Request #${requestId}] ${method} ${fullUrl}`)
    console.log(`[Request #${requestId}] Token:`, token ? '已设置' : '未设置')

    const startTime = Date.now()

    wx.request({
      url: fullUrl,
      method,
      data,
      timeout: 30000,
      header: {
        'Content-Type': 'application/json',
        'Authorization': token ? `Bearer ${token}` : ''
      },
      success(res) {
        const duration = Date.now() - startTime
        console.log(`[Response #${requestId}] Status: ${res.statusCode}, Duration: ${duration}ms`)

        if (res.statusCode === 200) {
          resolve(res.data)
        } else if (res.statusCode === 401) {
          console.log(`[Response #${requestId}] 401未授权`)
          handleUnauthorized()
          reject(new Error('登录已过期，请重新登录'))
        } else {
          console.log(`[Response #${requestId}] 请求失败:`, res.statusCode)
          reject(new Error((res.data && res.data.message) || `请求失败: ${res.statusCode}`))
        }
      },
      fail(err) {
        const duration = Date.now() - startTime
        console.error(`[Request #${requestId}] FAILED after ${duration}ms:`, err.errMsg)

        let errorMsg = '网络请求失败'
        if (err.errMsg) {
          if (err.errMsg.includes('timeout')) {
            errorMsg = '请求超时，请检查网络'
          } else if (err.errMsg.includes('fail')) {
            errorMsg = '网络连接失败，请检查后端是否启动'
          } else {
            errorMsg = err.errMsg
          }
        }

        reject(new Error(errorMsg))
      }
    })
  })
}

module.exports = {
  get: (url, data) => request(url, 'GET', data),
  post: (url, data) => request(url, 'POST', data),
  put: (url, data) => request(url, 'PUT', data),
  del: (url, data) => request(url, 'DELETE', data),
  UPLOAD_URL,
  BASE_URL,
  SCHEDULE_UPLOAD_URL
}