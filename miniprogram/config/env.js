/**
 * 环境配置
 */
const env = {
  // 开发环境
  development: {
    baseUrl: 'http://localhost:8081',
    apiBaseUrl: 'http://localhost:8081/api'
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