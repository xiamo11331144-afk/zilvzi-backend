/**
 * 环境配置
 */
const env = {
  // 开发环境：模拟器和手机共用电脑的局域网地址，手机与电脑需处于可互通网络。
  // 电脑IP变化时同步更新下方地址；真机不能使用localhost。
  development: {
    baseUrl: 'http://192.168.1.100:8081',
    apiBaseUrl: 'http://192.168.1.100:8081/api'
  },
  // 生产环境
  production: {
    baseUrl: 'https://your-domain.com',
    apiBaseUrl: 'https://your-domain.com/api'
  }
}

// 当前环境，开发时使用 development，发布时改为 production
const currentEnv = 'development'

const config = env[currentEnv]

module.exports = {
  baseUrl: config.baseUrl,
  apiBaseUrl: config.apiBaseUrl,
  uploadUrl: `${config.baseUrl}/api/file/upload`,
  scheduleUploadUrl: `${config.baseUrl}/api/file/upload-schedule`
}
