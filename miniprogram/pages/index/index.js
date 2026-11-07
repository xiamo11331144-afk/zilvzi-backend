const app = getApp()
const request = require('../../utils/request')
const badges = require('../../utils/badges')
const { formatDate, getWeekDay } = require('../../utils/util')

// 标准化目标类型为统一中文
function normalizeGoalType(type) {
  if (!type) return ''
  const map = {
    'exam': '考试', '考试': '考试', '考试备考': '考试',
    'study': '学习', '学习': '学习', '学习提升': '学习',
    'fitness': '运动', '运动': '运动', '健身': '运动', '健身运动': '运动', '锻炼': '运动', '体育': '运动',
    'reading': '阅读', '阅读': '阅读', '阅读计划': '阅读',
    'skill': '技能', '技能': '技能', '技能学习': '技能',
    'habit': '习惯', '习惯': '习惯', '习惯养成': '习惯'
  }
  return map[type] || type
}

Page({
  data: {
    badgeCounts: badges.emptyCounts(),
    badgeError: '',
    isLoggedIn: false,
    userInfo: {},
    todayDate: '',
    weekDay: '',
    checkinDays: 0,
    todayGoals: [],
    allChecked: false,
    uncheckedCount: 0,
    weekTrend: [],
    plans: [], // 计划列表
    showCreateModal: false,
    showTaskModal: false,
    showProofModal: false,
    selectedTask: {},
    proofTaskId: null,
    proofTaskName: '',
    proofTaskType: '',
    proofImage: '',
    proofContent: '',
    uploadedTasks: {},
    partners: [],
    newGoal: {
      name: '',
      type: '学习'
    },
    goalTypes: ['学习', '运动', '阅读', '早起', '饮食', '其他'],
    cycleOptions: ['每天', '工作日', '周末', '自定义'],
    cycleIndex: 0
  },

  onLoad() {
    this.initData()
  },

  onHide() { badges.unbind(this) },
  onUnload() { badges.unbind(this) },

  onShow() {
    badges.bind(this)
    this.checkLoginStatus()
    if (this.data.isLoggedIn) {
      this.loadUserInfo()
      this.loadGoals()
      this.loadWeekTrend()
      this.loadPlans()
      this.loadStreakDays()
      this.checkStreakBreak()
      this.loadPartners()
    }
  },

  async loadUserInfo() {
    try {
      const res = await request.get('/api/user/info')
      if (res.code === 200 && res.data) {
        app.globalData.userInfo = res.data
        wx.setStorageSync('userInfo', res.data)
        this.setData({ userInfo: res.data })
      }
    } catch (err) {}
  },

  async loadPartners() {
    try {
      const res = await request.get('/api/partner/list')
      if (res.code === 200 && res.data) {
        this.setData({ partners: res.data })
      }
    } catch (err) {}
  },

  async loadStreakDays() {
    try {
      const res = await request.get('/api/stats/dashboard')
      if (res.code === 200 && res.data) {
        this.setData({ checkinDays: res.data.streakDays || 0 })
      }
    } catch (err) {}
  },

  initData() {
    const now = new Date()
    this.setData({
      todayDate: formatDate(now, 'MM月DD日'),
      weekDay: getWeekDay(now)
    })
  },

  checkLoginStatus() {
    const token = wx.getStorageSync('token')
    const userInfo = wx.getStorageSync('userInfo') || app.globalData.userInfo || {}
    const isLoggedIn = !!token || app.globalData.isLoggedIn
    
    app.globalData.isLoggedIn = isLoggedIn
    app.globalData.token = token || app.globalData.token
    app.globalData.userInfo = userInfo
    
    this.setData({ isLoggedIn, userInfo })
  },

  async loadGoals() {
    try {
      // 获取今日计划任务（包括过期任务）
      const res = await request.get('/api/checkin/today')
      if (res.code === 200 && res.data && res.data.length > 0) {
        const goals = res.data.map(t => {
          const isRejected = t.checkinStatus === 'rejected'
          const isPending = t.checkinStatus === 'pending'
          const isApproved = t.checkinStatus === 'approved' || (t.completed === 1 && !t.checkinStatus)
          return {
            id: t.id,
            goalId: t.goalId,
            name: t.taskName || t.name || '未命名任务',
            type: t.taskType || '学习',
            checked: isApproved,
            pending: isPending,
            rejected: isRejected,
            expired: t.expired === 1,
            streak: 0,
            duration: t.taskDuration || '',
            description: t.taskDescription || '',
            taskDate: t.taskDate || ''
          }
        })
        const uncheckedCount = goals.filter(g => !g.checked && !g.pending && !g.expired).length
        this.setData({
          todayGoals: goals,
          uncheckedCount,
          allChecked: uncheckedCount === 0
        })
      } else {
        this.setData({ todayGoals: [], uncheckedCount: 0, allChecked: true })
      }
    } catch (err) {
      console.log('加载目标失败', err)
    }
  },

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
            height: checkinDates.includes(dateStr) ? 100 : 0
          })
        }
        
        this.setData({ weekTrend: weekData })
      }
    } catch (err) {
      console.log('加载趋势失败', err)
    }
  },

  async loadPlans() {
    try {
      const res = await request.get('/api/plan/list')
      if (res.code === 200 && res.data) {
        const now = new Date()
        now.setHours(0, 0, 0, 0)
        
        console.log('当前时间:', now.toISOString())
        
        const plans = res.data.map(plan => {
          // 解析createTime（可能是字符串格式）
          let createDate
          if (typeof plan.createTime === 'string') {
            // 处理 "2026-05-14 10:05:49" 格式
            createDate = new Date(plan.createTime.replace(' ', 'T'))
          } else {
            createDate = new Date(plan.createTime)
          }
          createDate.setHours(0, 0, 0, 0)
          
          // 计算结束日期
          const endDate = new Date(createDate)
          endDate.setDate(endDate.getDate() + plan.totalDays - 1)
          endDate.setHours(23, 59, 59, 999)
          
          console.log('计划:', plan.name, {
            createDate: createDate.toISOString(),
            totalDays: plan.totalDays,
            endDate: endDate.toISOString(),
            now: now.toISOString(),
            isExpired: now > endDate
          })
          
          let status = 'active'
          let statusText = '进行中'
          
          if (plan.completedDays >= plan.totalDays) {
            status = 'completed'
            statusText = '已完成'
          } else if (now > endDate) {
            status = 'expired'
            statusText = '已过期'
          }
          
          return {
            ...plan,
            status,
            statusText
          }
        })
        // 按名称去重，保留最新的计划（同名只显示一个）
        const seen = new Set()
        const uniquePlans = []
        for (const plan of plans) {
          if (!seen.has(plan.name)) {
            seen.add(plan.name)
            uniquePlans.push(plan)
          }
        }
        this.setData({ plans: uniquePlans })
      }
    } catch (err) {
      console.log('加载计划失败', err)
    }
  },

  retryBadges() { return badges.refresh(true) },

  // 断卡检测（进入小程序时执行，每天只弹窗一次，但后端补卡逻辑始终执行）
  async checkStreakBreak() {
    try {
      console.log('[StreakBreak] 开始断卡检测...')
      const res = await request.post('/api/checkin/streak-break/check')
      console.log('[StreakBreak] 后端返回:', JSON.stringify(res))

      if (res.code === 200 && res.data) {
        const hasBreak = res.data.hasStreakBreak
        console.log('[StreakBreak] hasStreakBreak:', hasBreak)

        if (hasBreak) {
          badges.refresh(true)
          // 检查今天是否已提示过，避免反复弹窗
          const today = formatDate(new Date(), 'YYYY-MM-DD')
          const lastDate = wx.getStorageSync('lastStreakBreakNotifyDate')
          if (lastDate === today) {
            console.log('[StreakBreak] 今天已提示过，跳过弹窗')
            return
          }

          const plans = res.data.affectedPlans || []
          const supplementTasks = res.data.supplementTasks || []
          console.log('[StreakBreak] 受影响计划:', JSON.stringify(plans))
          console.log('[StreakBreak] 补发任务:', JSON.stringify(supplementTasks))

          const names = plans.map(p => p.planName).join('、')
          wx.showModal({
            title: '断卡提醒',
            content: `${names}有断卡情况，已自动补发${supplementTasks.length}个任务到后续天数`,
            showCancel: false
          })

          // 记录今天已提示
          wx.setStorageSync('lastStreakBreakNotifyDate', today)
        } else {
          console.log('[StreakBreak] 无断卡情况')
        }
      }
    } catch (err) {
      console.error('[StreakBreak] 检测失败:', err)
    }
  },

  async toggleCheckin(e) {
    const taskId = e.currentTarget.dataset.id
    const todayGoals = this.data.todayGoals
    const goalIndex = todayGoals.findIndex(g => g.id === taskId)
    if (goalIndex === -1) return

    if (todayGoals[goalIndex].checked) {
      wx.showToast({ title: '该任务已打卡', icon: 'none' }); return
    }
    if (todayGoals[goalIndex].pending) {
      wx.showToast({ title: '该任务已提交，等待审核', icon: 'none' }); return
    }
    if (todayGoals[goalIndex].expired) {
      wx.showToast({ title: '该任务已过期', icon: 'none' }); return
    }

    // 检查是否有同类型目标的搭子
    const taskType = normalizeGoalType(todayGoals[goalIndex].type)
    const hasPartner = this.data.partners.some(p =>
      p.status === 'accepted' && p.goalTypes && p.goalTypes.includes(taskType)
    )
    if (hasPartner) {
      // 有搭子 → 弹出凭证上传弹窗
      this.setData({
        showProofModal: true,
        proofTaskId: taskId,
        proofTaskName: todayGoals[goalIndex].name,
        proofTaskType: todayGoals[goalIndex].type,
        proofImage: '',
        proofContent: ''
      })
      return
    }

    // 无搭子 → 直接打卡
    todayGoals[goalIndex].checked = true
    const uncheckedCount = todayGoals.filter(g => !g.checked && !g.expired).length
    this.setData({ todayGoals, uncheckedCount, allChecked: uncheckedCount === 0 })

    try {
      const res = await request.post('/api/checkin/do', { taskId })
      if (res.code === 200) {
        wx.showToast({ title: '打卡成功', icon: 'success' })
      } else {
        todayGoals[goalIndex].checked = false
        const rollbackCount = todayGoals.filter(g => !g.checked && !g.expired).length
        this.setData({ todayGoals, uncheckedCount: rollbackCount, allChecked: rollbackCount === 0 })
        wx.showToast({ title: res.message || '打卡失败', icon: 'none' })
      }
    } catch (err) {
      todayGoals[goalIndex].checked = false
      const rollbackCount = todayGoals.filter(g => !g.checked && !g.expired).length
      this.setData({ todayGoals, uncheckedCount: rollbackCount, allChecked: rollbackCount === 0 })
      wx.showToast({ title: '打卡失败，请重试', icon: 'none' })
    }
  },

  // 凭证上传弹窗相关
  hideProofModal() {
    this.setData({ showProofModal: false, proofTaskId: null, proofTaskName: '', proofTaskType: '', proofImage: '', proofContent: '' })
  },

  onProofContentInput(e) {
    this.setData({ proofContent: e.detail.value })
  },

  onProofContentBlur(e) {
    if (e.detail && e.detail.value !== undefined) {
      this.setData({ proofContent: e.detail.value })
    }
  },

  previewProofImage() {
    if (this.data.proofImage) {
      wx.previewImage({ urls: [this.data.proofImage] })
    }
  },

  chooseProofImage() {
    const self = this
    wx.chooseMedia({
      count: 1, mediaType: ['image'], sourceType: ['album', 'camera'],
      success(res) {
        wx.showLoading({ title: '上传中...' })
        wx.uploadFile({
          url: request.UPLOAD_URL, filePath: res.tempFiles[0].tempFilePath, name: 'file',
          header: { 'Authorization': 'Bearer ' + app.globalData.token },
          success(uploadRes) {
            console.log('[chooseProofImage] 上传返回:', uploadRes.statusCode, uploadRes.data)
            wx.hideLoading()
            try {
              const d = JSON.parse(uploadRes.data)
              if (d.code === 200 && d.data && d.data.url) {
                self.setData({ proofImage: d.data.url })
                console.log('[chooseProofImage] proofImage:', d.data.url)
              } else {
                wx.showToast({ title: d.message || '上传失败', icon: 'none' })
              }
            } catch (e) {
              wx.showToast({ title: '上传失败', icon: 'none' })
            }
          },
          fail() { wx.hideLoading(); wx.showToast({ title: '上传失败', icon: 'none' }) }
        })
      }
    })
  },

  async submitProof() {
    const { proofTaskId, proofImage, proofContent } = this.data
    console.log('[submitProof] taskId:', proofTaskId, 'image:', proofImage, 'content:', proofContent)
    if (!proofImage) { wx.showToast({ title: '请上传凭证照片', icon: 'none' }); return }

    wx.showLoading({ title: '提交中...' })
    try {
      const res = await request.post('/api/checkin/do', {
        taskId: proofTaskId, proofImage, proofContent
      })
      console.log('[submitProof] 后端返回:', JSON.stringify(res))
      wx.hideLoading()
      if (res.code === 200) {
        wx.showToast({ title: '已提交，等待搭子审核', icon: 'success' })
        // 清除该任务的上传状态
        const uploadedTasks = { ...this.data.uploadedTasks }
        delete uploadedTasks[proofTaskId]
        this.setData({ showProofModal: false, proofTaskId: null, proofTaskName: '', proofTaskType: '', proofImage: '', proofContent: '', uploadedTasks })
        this.loadGoals()
      } else {
        wx.showToast({ title: res.message || '提交失败', icon: 'none' })
      }
    } catch (err) {
      wx.hideLoading()
      wx.showToast({ title: '提交失败，请重试', icon: 'none' })
    }
  },

  // 拍照打卡：上传照片后打开凭证弹窗
  photoCheckin(e) {
    const taskId = e.currentTarget.dataset.id
    const todayGoals = this.data.todayGoals
    const goalIndex = todayGoals.findIndex(g => g.id === taskId)
    if (goalIndex === -1 || todayGoals[goalIndex].checked || todayGoals[goalIndex].pending) return

    const self = this
    const taskName = todayGoals[goalIndex].name
    const taskType = todayGoals[goalIndex].type
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      success(res) {
        const photoPath = res.tempFiles[0].tempFilePath
        wx.showLoading({ title: '上传中...' })
        wx.uploadFile({
          url: request.UPLOAD_URL,
          filePath: photoPath,
          name: 'file',
          header: { 'Authorization': 'Bearer ' + app.globalData.token },
          success(uploadRes) {
            console.log('[photoCheckin] 上传返回:', uploadRes.statusCode, uploadRes.data)
            wx.hideLoading()
            try {
              const d = JSON.parse(uploadRes.data)
              console.log('[photoCheckin] 解析:', JSON.stringify(d))
              if (d.code === 200 && d.data && d.data.url) {
                const imageUrl = d.data.url
                console.log('[photoCheckin] 图片URL:', imageUrl)
                // 存储到 uploadedTasks
                const ut = {}
                ut[taskId] = imageUrl
                self.setData({ uploadedTasks: Object.assign({}, self.data.uploadedTasks, ut) })
                // 打开凭证弹窗
                self.setData({
                  showProofModal: true,
                  proofTaskId: taskId,
                  proofTaskName: taskName,
                  proofTaskType: taskType,
                  proofImage: imageUrl,
                  proofContent: ''
                })
              } else {
                wx.showToast({ title: d.message || '上传失败', icon: 'none' })
              }
            } catch (err) {
              console.error('[photoCheckin] 解析失败:', err)
              wx.showToast({ title: '上传结果解析失败', icon: 'none' })
            }
          },
          fail(err) {
            wx.hideLoading()
            console.error('[photoCheckin] 上传请求失败:', err)
            wx.showToast({ title: '图片上传失败', icon: 'none' })
          }
        })
      }
    })
  },

  // 重新上传照片
  reuploadPhoto(e) {
    this.photoCheckin(e)
  },

  goCheckin(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({
      url: `/pages/checkin/checkin?goalId=${id}`
    })
  },

  goLogin() {
    wx.navigateTo({ url: '/pages/login/login' })
  },

  goToPage(e) {
    const url = e.currentTarget.dataset.url
    wx.navigateTo({ url })
  },

  // 查看计划详情
  viewPlan(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({
      url: `/pages/plan/plan?id=${id}`
    })
  },

  // 删除计划
  deletePlan(e) {
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '确认删除',
      content: '确定要删除这个学习计划吗？',
      success: async (res) => {
        if (res.confirm) {
          try {
            const result = await request.del(`/api/plan/delete/${id}`)
            if (result.code === 200) {
              wx.showToast({ title: '删除成功', icon: 'success' })
              this.loadPlans()
            }
          } catch (err) {
            wx.showToast({ title: '删除失败', icon: 'none' })
          }
        }
      }
    })
  },

  createGoal() {
    if (!this.data.isLoggedIn) {
      wx.showToast({ title: '请先登录', icon: 'none' })
      return
    }
    this.setData({ showCreateModal: true })
  },

  hideCreateModal() {
    this.setData({ showCreateModal: false })
  },

  showTaskDetail(e) {
    const index = e.currentTarget.dataset.index
    const task = this.data.todayGoals[index]
    this.setData({ 
      showTaskModal: true,
      selectedTask: task
    })
  },

  hideTaskDetail() {
    this.setData({ showTaskModal: false })
  },

  async toggleCheckinFromModal(e) {
    // 关闭弹窗后复用 toggleCheckin 的搭子检测逻辑
    this.setData({ showTaskModal: false })
    await this.toggleCheckin(e)
  },

  onGoalNameInput(e) {
    this.setData({ 'newGoal.name': e.detail.value })
  },

  selectGoalType(e) {
    this.setData({ 'newGoal.type': e.currentTarget.dataset.type })
  },

  onCycleChange(e) {
    this.setData({ cycleIndex: e.detail.value })
  },

  async createGoalConfirm() {
    const { name, type } = this.data.newGoal
    if (!name.trim()) {
      wx.showToast({ title: '请输入目标名称', icon: 'none' })
      return
    }

    try {
      const res = await request.post('/api/goal/create', { name, type })
      if (res.code === 200) {
        wx.showToast({ title: '创建成功', icon: 'success' })
        this.setData({ showCreateModal: false, 'newGoal.name': '' })
        this.loadGoals()
      }
    } catch (err) {
      wx.showToast({ title: err.message || '创建失败', icon: 'none' })
    }
  }
})