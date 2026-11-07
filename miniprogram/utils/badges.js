// 所有导航和页内入口共用一份计数。私信使用 message 表，通知按类别计数。
const request = require('./request')

const emptyCounts = () => ({ notificationUnread: 0, circleUnread: 0, privateUnread: 0, pendingRequests: 0 })
let counts = emptyCounts()
let error = ''
let sessionKey = ''
let generation = 0
let inFlight = null
const listeners = new Set()

function currentSession() {
  const data = getApp().globalData
  return data.isLoggedIn && data.token ? `${data.userId || ''}:${data.token}` : ''
}

function applyTabBar() {
  const values = [0, counts.pendingRequests, counts.circleUnread, counts.notificationUnread + counts.privateUnread]
  values.forEach((value, index) => {
    const options = { index, fail() {} }
    if (value > 0) wx.setTabBarBadge({ ...options, text: value > 99 ? '99+' : String(value) })
    else wx.removeTabBarBadge(options)
  })
}

function publish() {
  applyTabBar()
  listeners.forEach(listener => listener({ ...counts }, error))
}

function reset() {
  generation++
  sessionKey = currentSession()
  inFlight = null
  counts = emptyCounts()
  error = ''
  publish()
}

function refresh(force = false) {
  const key = currentSession()
  if (key !== sessionKey) reset()
  if (!key) return Promise.resolve({ ...counts })
  if (inFlight && !force) return inFlight
  const version = ++generation
  const pending = request.get('/api/notification/badges').then(res => {
    if (version !== generation || currentSession() !== key) return { ...counts }
    if (res.code !== 200 || !res.data) throw new Error(res.message || '未读消息加载失败')
    const next = emptyCounts()
    Object.keys(next).forEach(field => {
      const value = res.data[field]
      if (typeof value !== 'number' || !Number.isFinite(value) || value < 0 || !Number.isInteger(value)) {
        throw new Error('未读消息数据不完整，请重试')
      }
      next[field] = value
    })
    counts = next
    error = ''
    publish()
    return { ...counts }
  }).catch(err => {
    if (version === generation && currentSession() === key) {
      error = err.message || '未读消息加载失败'
      publish() // 保留最后一次成功计数，失败不能伪装成全部已读。
    }
    return { ...counts }
  }).finally(() => {
    if (inFlight === pending) inFlight = null
  })
  inFlight = pending
  return pending
}

function unbind(page) {
  if (page._badgeListener) listeners.delete(page._badgeListener)
  page._badgeListener = null
}

function bind(page) {
  unbind(page)
  if (currentSession() !== sessionKey) reset()
  page._badgeListener = (badgeCounts, badgeError) => page.setData({ badgeCounts, badgeError })
  listeners.add(page._badgeListener)
  page._badgeListener({ ...counts }, error)
  return refresh()
}

module.exports = { emptyCounts, refresh, reset, bind, unbind }
