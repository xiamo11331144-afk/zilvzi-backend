// 只解释后端业务码，会员权限始终由服务端判定。
const VIP_REQUIRED = 40301

function handleVipRequired(result) {
  if (!result || result.code !== VIP_REQUIRED) return false
  wx.showModal({
    title: '引领申请需要 VIP',
    content: 'VIP 可发起新的引领型申请。当前尚未接入真实支付，会员页会明确展示可用的测试流程。返回后可继续当前匹配。',
    confirmText: '查看会员',
    cancelText: '继续浏览',
    success(res) {
      if (res.confirm) wx.navigateTo({ url: '/pages/vip/vip?from=match' })
    }
  })
  return true
}

function formatVipUntil(value) {
  if (!value) return ''
  // 服务端返回带时区的时间，统一按产品时区 Asia/Shanghai 展示。
  const timestamp = Date.parse(value)
  if (!Number.isFinite(timestamp)) return '有效期暂不可用'
  const date = new Date(timestamp + 8 * 60 * 60 * 1000)
  const pad = n => String(n).padStart(2, '0')
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())} ${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}:${pad(date.getUTCSeconds())}（北京时间）`
}

module.exports = { VIP_REQUIRED, handleVipRequired, formatVipUntil }
