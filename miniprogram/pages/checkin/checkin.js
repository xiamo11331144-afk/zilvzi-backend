const app = getApp()
const request = require('../../utils/request')
const { formatDate, getDaysInMonth } = require('../../utils/util')

Page({
  data: {
    currentYear: 2026,
    currentMonth: 1,
    weekdays: ['日', '一', '二', '三', '四', '五', '六'],
    calendarDays: [],
    stats: {
      totalDays: 0,
      streakDays: 0,
      completionRate: 0
    },
    todayTasks: [],
    hasUnchecked: false,
    showSuccess: false,
    checkedDates: [],
    planStartDate: '',
    planEndDate: '',
    planList: [],
    activeTab: 'tasks', // tasks | pending
    pendingReviews: []
  },

  async onLoad(options) {
    const now = new Date()
    this.setData({
      currentYear: now.getFullYear(),
      currentMonth: now.getMonth() + 1
    })
    this.initCalendar()
    this.loadCheckedDates()
    this.loadTodayTasks()
    // 先加载计划信息，再加载统计（完成率依赖计划数据）
    await this.loadPlanInfo()
    this.loadStats()
  },

  onShow() {
    this.loadTodayTasks()
    this.loadCheckedDates()
    this.loadStats()
    this.loadPendingReviews()
  },

  async loadPlanInfo() {
    try {
      const res = await request.get('/api/plan/list')
      if (res.code === 200 && res.data && res.data.length > 0) {
        const plan = res.data[0]
        const startDate = new Date(plan.createTime)
        const endDate = new Date(startDate)
        endDate.setDate(endDate.getDate() + plan.totalDays - 1)
        
        this.setData({
          planStartDate: formatDate(startDate, 'YYYY-MM-DD'),
          planEndDate: formatDate(endDate, 'YYYY-MM-DD'),
          planList: res.data
        })
      }
    } catch (err) {
      console.log('加载计划信息失败', err)
    }
  },

  initCalendar() {
    const { currentYear, currentMonth, checkedDates, planStartDate, planEndDate } = this.data
    const firstDay = new Date(currentYear, currentMonth - 1, 1).getDay()
    const daysInMonth = getDaysInMonth(currentYear, currentMonth)
    const daysInPrevMonth = getDaysInMonth(currentYear, currentMonth - 1)
    const today = new Date()
    const isCurrentMonth = today.getFullYear() === currentYear && today.getMonth() + 1 === currentMonth

    let days = []

    for (let i = firstDay - 1; i >= 0; i--) {
      days.push({
        day: daysInPrevMonth - i,
        isCurrentMonth: false,
        checked: false,
        isToday: false,
        isStart: false,
        isEnd: false
      })
    }

    for (let i = 1; i <= daysInMonth; i++) {
      const dateStr = `${currentYear}-${String(currentMonth).padStart(2, '0')}-${String(i).padStart(2, '0')}`
      days.push({
        day: i,
        isCurrentMonth: true,
        checked: checkedDates.includes(dateStr),
        isToday: isCurrentMonth && today.getDate() === i,
        isStart: dateStr === planStartDate,
        isEnd: dateStr === planEndDate,
        date: dateStr
      })
    }

    const remaining = 42 - days.length
    for (let i = 1; i <= remaining; i++) {
      days.push({
        day: i,
        isCurrentMonth: false,
        checked: false,
        isToday: false,
        isStart: false,
        isEnd: false
      })
    }

    this.setData({ calendarDays: days })
  },

  async loadCheckedDates() {
    try {
      const now = new Date()
      const res = await request.get('/api/checkin/dates', {
        year: now.getFullYear(),
        month: now.getMonth() + 1
      })
      if (res.code === 200 && res.data) {
        this.setData({ checkedDates: res.data })
        this.initCalendar()
      }
    } catch (err) {
      console.log('加载打卡日期失败', err)
    }
  },

  async loadTodayTasks() {
    try {
      const res = await request.get('/api/checkin/today')
      if (res.code === 200 && res.data) {
        const tasks = res.data.map(t => ({
          id: t.id,
          goalId: t.goalId,
          name: t.taskName || '未命名任务',
          type: t.taskType || '学习',
          description: t.taskDescription || '',
          duration: t.taskDuration || '',
          checked: t.completed === 1,
          streakBreak: t.completed === -1,
          checkTime: ''
        }))
        const hasUnchecked = tasks.some(t => !t.checked && !t.streakBreak)
        this.setData({ todayTasks: tasks, hasUnchecked })
      }
    } catch (err) {
      console.log('加载任务失败', err)
    }
  },

  async loadStats() {
    try {
      // 从后端获取累计打卡和连续打卡（支持跨月，数据准确）
      const statsRes = await request.get('/api/checkin/stats')
      
      // 从计划数据计算完成率
      let completionRate = 0
      const plans = this.data.planList || []
      if (plans.length > 0) {
        const plan = plans[0]
        const totalDays = plan.totalDays || 0
        const completedDays = plan.completedDays || 0
        completionRate = totalDays > 0 ? Math.round((completedDays / totalDays) * 100) : 0
      }

      if (statsRes.code === 200 && statsRes.data) {
        this.setData({
          stats: {
            totalDays: statsRes.data.total || 0,
            streakDays: statsRes.data.streak || 0,
            completionRate: completionRate
          }
        })
      } else {
        this.setData({
          stats: {
            totalDays: 0,
            streakDays: 0,
            completionRate: completionRate
          }
        })
      }
    } catch (err) {
      console.log('加载统计失败', err)
    }
  },

  prevMonth() {
    let { currentYear, currentMonth } = this.data
    currentMonth--
    if (currentMonth < 1) {
      currentMonth = 12
      currentYear--
    }
    this.setData({ currentYear, currentMonth })
    this.initCalendar()
    this.loadCheckedDates()
  },

  nextMonth() {
    let { currentYear, currentMonth } = this.data
    currentMonth++
    if (currentMonth > 12) {
      currentMonth = 1
      currentYear++
    }
    this.setData({ currentYear, currentMonth })
    this.initCalendar()
    this.loadCheckedDates()
  },

  selectDay(e) {
    const day = e.currentTarget.dataset.day
    if (!day.isCurrentMonth) return
  },

  async doCheckin(e) {
    const taskId = e.currentTarget.dataset.id
    try {
      const res = await request.post('/api/checkin/do', { taskId })
      if (res.code === 200) {
        wx.showToast({ title: '打卡成功', icon: 'success' })
        this.loadTodayTasks()
        this.loadCheckedDates()
        this.loadStats()
      }
    } catch (err) {
      wx.showToast({ title: err.message || '打卡失败', icon: 'none' })
    }
  },

  async batchCheckin() {
    const unchecked = this.data.todayTasks.filter(t => !t.checked && !t.streakBreak)
    if (unchecked.length === 0) {
      wx.showToast({ title: '没有可打卡的任务', icon: 'none' })
      return
    }
    const failed = []
    for (const task of unchecked) {
      try {
        await request.post('/api/checkin/do', { taskId: task.id })
      } catch (err) {
        failed.push(task.name)
      }
    }
    if (failed.length > 0) {
      wx.showToast({ title: `${failed.length}个任务打卡失败`, icon: 'none' })
    } else {
      wx.showToast({ title: '打卡完成', icon: 'success' })
      this.setData({ showSuccess: true })
    }
    this.loadTodayTasks()
    this.loadCheckedDates()
    this.loadStats()
  },

  hideSuccess() {
    this.setData({ showSuccess: false })
  },

  switchTab(e) {
    this.setData({ activeTab: e.currentTarget.dataset.tab })
  },

  async loadPendingReviews() {
    try {
      const res = await request.get('/api/checkin/pending')
      if (res.code === 200 && res.data) {
        this.setData({ pendingReviews: res.data })
      }
    } catch (err) {}
  },

  async approveCheckin(e) {
    const checkinId = e.currentTarget.dataset.id
    wx.showModal({
      title: '审核通过',
      content: '确认该打卡凭证有效？',
      success: async (res) => {
        if (res.confirm) {
          try {
            const r = await request.post('/api/checkin/review', { checkinId, approved: true })
            if (r.code === 200) {
              wx.showToast({ title: '已通过', icon: 'success' })
              this.loadPendingReviews()
            } else {
              wx.showToast({ title: r.message || '操作失败', icon: 'none' })
            }
          } catch (err) { wx.showToast({ title: '操作失败', icon: 'none' }) }
        }
      }
    })
  },

  rejectCheckin(e) {
    const checkinId = e.currentTarget.dataset.id
    wx.showModal({
      title: '驳回打卡',
      editable: true,
      placeholderText: '请输入驳回原因（可选）',
      success: async (res) => {
        if (res.confirm) {
          try {
            const r = await request.post('/api/checkin/review', { checkinId, approved: false, comment: res.content || '' })
            if (r.code === 200) {
              wx.showToast({ title: '已驳回', icon: 'success' })
              this.loadPendingReviews()
            } else {
              wx.showToast({ title: r.message || '操作失败', icon: 'none' })
            }
          } catch (err) { wx.showToast({ title: '操作失败', icon: 'none' }) }
        }
      }
    })
  }
})