const app = getApp()
const request = require('../../utils/request')

Page({
  data: {
    level: 1,
    levelTitle: '自律新手',
    currentExp: 0,
    nextLevelExp: 500,
    expPercent: 0,
    stats: {
      totalCheckins: 0,
      streakDays: 0,
      medals: 0
    },
    medals: [],
    allMedals: [],
    totalMedals: 12,
    ranking: [],
    loading: true
  },

  onLoad() {
    if (app.globalData.isLoggedIn) {
      this.loadAll()
    }
  },

  onShow() {
    if (app.globalData.isLoggedIn) {
      this.loadAll()
    }
  },

  async onPullDownRefresh() {
    if (app.globalData.isLoggedIn) {
      await this.loadAll()
    }
    wx.stopPullDownRefresh()
  },

  async loadAll() {
    this.setData({ loading: true })
    try {
      await Promise.all([
        this.loadLevelInfo(),
        this.loadAchievements(),
        this.loadRanking()
      ])
    } catch (err) {
      console.log('加载成就数据失败', err)
    } finally {
      this.setData({ loading: false })
    }
  },

  async loadLevelInfo() {
    try {
      const res = await request.get('/api/achievement/level')
      if (res.code === 200 && res.data) {
        this.setData({
          level: res.data.level || 1,
          levelTitle: res.data.levelTitle || '自律新手',
          currentExp: res.data.currentExp || 0,
          nextLevelExp: res.data.nextLevelExp || 500,
          expPercent: res.data.expPercent || 0,
          stats: {
            totalCheckins: res.data.totalCheckins || 0,
            streakDays: res.data.streakDays || 0,
            medals: res.data.medals || 0
          }
        })
      }
    } catch (err) {
      console.log('加载等级失败', err)
    }
  },

  async loadAchievements() {
    try {
      const res = await request.get('/api/achievement/list')
      if (res.code === 200 && res.data) {
        const unlockedMedals = res.data
        this.setData({
          medals: unlockedMedals,
          stats: {
            ...this.data.stats,
            medals: unlockedMedals.length
          }
        })

        // 构建所有勋章列表（包含未解锁的）
        this.buildAllMedals(unlockedMedals)
      }
    } catch (err) {
      console.log('加载成就失败', err)
    }
  },

  buildAllMedals(unlockedMedals) {
    // 与后端 MEDAL_DEFS 保持一致
    const medalDefs = [
      { key: 'first_checkin', name: '初次打卡', icon: '✓', color: '#4A90D9', condition: '完成第一次打卡' },
      { key: 'streak_3', name: '三日坚持', icon: '3', color: '#52C41A', condition: '连续打卡3天' },
      { key: 'streak_7', name: '一周达人', icon: '7', color: '#FAAD14', condition: '连续打卡7天' },
      { key: 'streak_21', name: '习惯养成', icon: '21', color: '#FF6B6B', condition: '连续打卡21天' },
      { key: 'streak_100', name: '百日坚持', icon: '100', color: '#722ED1', condition: '连续打卡100天' },
      { key: 'total_30', name: '月度之星', icon: '30', color: '#13C2C2', condition: '累计打卡30天' },
      { key: 'total_100', name: '百次打卡', icon: '百', color: '#FAAD14', condition: '累计打卡100次' },
      { key: 'total_365', name: '年度坚持', icon: '365', color: '#FF6B6B', condition: '累计打卡365天' },
      { key: 'early_bird', name: '早起鸟儿', icon: '早', color: '#52C41A', condition: '凌晨5点前打卡' },
      { key: 'night_owl', name: '夜猫子', icon: '夜', color: '#722ED1', condition: '晚上11点后打卡' },
      { key: 'partner_5', name: '社交达人', icon: '友', color: '#13C2C2', condition: '拥有5个搭子' },
      { key: 'circle_3', name: '圈子达人', icon: '圈', color: '#4A90D9', condition: '加入3个圈子' }
    ]

    const allMedals = medalDefs.map(def => {
      const unlocked = unlockedMedals.find(m => m.medalName === def.name)
      return {
        ...def,
        unlocked: !!unlocked,
        unlockTime: unlocked ? unlocked.unlockTime : null
      }
    })

    this.setData({ allMedals })
  },

  async loadRanking() {
    try {
      const res = await request.get('/api/achievement/ranking', { limit: 10 })
      if (res.code === 200 && res.data) {
        this.setData({ ranking: res.data })
      }
    } catch (err) {
      console.log('加载排行榜失败', err)
    }
  }
})