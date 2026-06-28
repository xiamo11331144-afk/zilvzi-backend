const app = getApp()
const request = require('../../utils/request')
const { formatDate } = require('../../utils/util')

Page({
  data: {
    stats: {
      totalCheckins: 0,
      streakDays: 0,
      completionRate: 0
    },
    weekTrend: [],
    goals: [],
    ranking: []
  },

  onLoad() {
    if (app.globalData.isLoggedIn) {
      this.loadStats()
      this.loadWeekTrend()
      this.loadGoals()
      this.loadRanking()
    }
  },

  onShow() {
    if (app.globalData.isLoggedIn) {
      this.loadStats()
    }
  },

  // 加载统计数据
  async loadStats() {
    try {
      const res = await request.get('/api/stats/dashboard')
      if (res.code === 200 && res.data) {
        this.setData({
          stats: {
            totalCheckins: res.data.totalCheckins || 0,
            streakDays: res.data.streakDays || 0,
            completionRate: res.data.completionRate || 0
          }
        })
      }
    } catch (err) {
      console.log('加载统计失败', err)
    }
  },

  // 加载本周趋势（与 index.js 保持一致的逻辑）
  async loadWeekTrend() {
    try {
      const now = new Date()
      const year = now.getFullYear()
      const month = now.getMonth() + 1
      const res = await request.get('/api/checkin/dates', { year, month })
      
      if (res.code === 200 && res.data) {
        const checkinDates = res.data
        const today = now.getDay() // 0=周日, 1=周一, ..., 6=周六
        const weekDays = ['日', '一', '二', '三', '四', '五', '六']
        const weekData = []
        
        for (let i = 0; i < 7; i++) {
          const date = new Date(now)
          date.setDate(date.getDate() - today + i) // 从周日开始到周六
          const dateStr = formatDate(date, 'YYYY-MM-DD')
          
          weekData.push({
            day: weekDays[i],
            checked: checkinDates.includes(dateStr),
            height: checkinDates.includes(dateStr) ? 100 : 20
          })
        }
        
        this.setData({ weekTrend: weekData })
      }
    } catch (err) {
      console.log('加载趋势失败', err)
    }
  },

  // 加载计划完成率
  async loadGoals() {
    try {
      const res = await request.get('/api/plan/list')
      if (res.code === 200 && res.data) {
        const colors = ['#4A90D9', '#52C41A', '#FAAD14', '#FF6B6B', '#722ED1']
        const goals = res.data.slice(0, 5).map((p, i) => {
          const total = p.totalDays || 1
          const done = p.completedDays || 0
          const percent = Math.round(done / total * 100)
          return { id: p.id, name: p.name, percent: percent, color: colors[i % colors.length] }
        })
        this.setData({ goals })
      }
    } catch (err) {
      console.log('加载计划失败', err)
    }
  },

  // 加载排行榜
  async loadRanking() {
    try {
      const res = await request.get('/api/achievement/ranking', { limit: 10 })
      if (res.code === 200 && res.data) {
        this.setData({ ranking: res.data })
      }
    } catch (err) {
      console.log('加载排行榜失败', err)
    }
  },

  // 导出Excel
  exportExcel() {
    wx.showLoading({ title: '生成中...' })
    const userId = app.globalData.userInfo ? app.globalData.userInfo.id : ''
    const token = app.globalData.token || ''
    const filePath = wx.env.USER_DATA_PATH + '/checkin_' + Date.now() + '.csv'
    wx.downloadFile({
      url: request.BASE_URL + `/api/export/checkin/excel?userId=${userId}`,
      header: { 'Authorization': `Bearer ${token}` },
      filePath: filePath,
      success(res) {
        wx.hideLoading()
        if (res.statusCode === 200) {
          wx.showModal({
            title: '导出成功',
            content: '文件已保存，可在微信中转发或打开查看。',
            confirmText: '打开查看',
            success(modalRes) {
              if (modalRes.confirm) {
                wx.openDocument({ filePath: filePath, showMenu: true })
              }
            }
          })
        } else {
          wx.showToast({ title: '导出失败', icon: 'none' })
        }
      },
      fail(err) {
        wx.hideLoading()
        wx.showToast({ title: '导出失败，请重试', icon: 'none' })
      }
    })
  },

  // 导出PDF
  exportPdf() {
    wx.showLoading({ title: '生成中...' })
    const userId = app.globalData.userInfo ? app.globalData.userInfo.id : ''
    const token = app.globalData.token || ''
    const filePath = wx.env.USER_DATA_PATH + '/checkin_' + Date.now() + '.pdf'
    wx.downloadFile({
      url: request.BASE_URL + `/api/export/checkin/pdf?userId=${userId}`,
      header: { 'Authorization': `Bearer ${token}` },
      filePath: filePath,
      success(res) {
        wx.hideLoading()
        if (res.statusCode === 200) {
          wx.showModal({
            title: '导出成功',
            content: 'PDF文件已保存。',
            confirmText: '打开查看',
            success(modalRes) {
              if (modalRes.confirm) {
                wx.openDocument({ filePath: filePath, showMenu: true })
              }
            }
          })
        } else {
          wx.showToast({ title: '导出失败', icon: 'none' })
        }
      },
      fail(err) {
        wx.hideLoading()
        wx.showToast({ title: '导出失败，请重试', icon: 'none' })
      }
    })
  }
})