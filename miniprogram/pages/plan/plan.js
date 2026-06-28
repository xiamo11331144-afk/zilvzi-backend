const app = getApp()
const request = require('../../utils/request')
const { formatDate } = require('../../utils/util')

Page({
  data: {
    currentStep: 1,
    today: '',
    
    // 步骤1：目标信息
    goalTypes: [
      { value: 'exam', name: '考试备考', icon: '📝', color: '#4A90D9' },
      { value: 'study', name: '学习提升', icon: '📚', color: '#52C41A' },
      { value: 'fitness', name: '健身运动', icon: '💪', color: '#FAAD14' },
      { value: 'reading', name: '阅读计划', icon: '📖', color: '#722ED1' },
      { value: 'skill', name: '技能学习', icon: '🎯', color: '#13C2C2' },
      { value: 'habit', name: '习惯养成', icon: '⭐', color: '#FF6B6B' }
    ],
    selectedGoalType: '',
    goalName: '',
    startDate: '',
    examDate: '',
    remark: '',
    
    // 步骤2：时间安排
    isStudent: true,
    weekDays: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'],
    timeSlots: ['上午1-2节', '上午3-4节', '下午1-2节', '下午3-4节', '晚上'],
    schedule: {},
    expandedDays: [false, false, false, false, false, false, false],
    availableTimeSlots: ['08:00-10:00', '10:00-12:00', '14:00-16:00', '16:00-18:00', '19:00-21:00'],
    freeTime: [[], [], [], [], [], [], []],
    scheduleFile: null, // 上传的课表/安排表文件
    
    // 步骤3：AI生成
    isGenerating: false,
    planGenerated: false,
    
    // 计划数据
    planId: null,
    planOutline: null,  // 大纲（L1）
    phases: [],         // 阶段列表
    currentPhase: 1,    // 当前查看的阶段
    phaseTasks: {},     // 已加载的阶段任务 {phase: tasks}
    isLoadingPhase: false,
    
    // 完整计划弹窗
    showFullPlan: false,
    fullPlanTasks: []
  },

  onLoad(options) {
    const today = formatDate(new Date(), 'YYYY-MM-DD')
    this.setData({ today })
    this.initSchedule()
    
    // 如果传入了计划ID，加载已有计划
    if (options.id) {
      this.loadExistingPlan(options.id)
    }
  },

  // 加载已有计划
  async loadExistingPlan(planId) {
    wx.showLoading({ title: '加载计划...' })
    try {
      const res = await request.get(`/api/plan/detail/${planId}`)
      if (res.code === 200 && res.data) {
        const plan = res.data
        
        // 解析大纲
        let phases = []
        try {
          const outline = JSON.parse(plan.outline)
          phases = outline.phases || []
        } catch (e) {
          console.log('解析大纲失败', e)
        }

        // 获取该计划所有已完成的任务天数
        let completedDaysMap = {}
        try {
          const tasksRes = await request.get(`/api/plan/${planId}/tasks`)
          if (tasksRes.code === 200 && tasksRes.data) {
            tasksRes.data.forEach(task => {
              if (task.completed === 1) {
                if (!completedDaysMap[task.dayNumber]) {
                  completedDaysMap[task.dayNumber] = true
                }
              }
            })
          }
        } catch (e) {
          console.log('获取任务失败', e)
        }

        // 计算每个阶段的状态（基于实际打卡天数）
        phases = phases.map(phase => {
          const phaseStartDay = phase.startDay
          const phaseEndDay = phase.endDay
          const phaseTotalDays = phaseEndDay - phaseStartDay + 1
          
          // 计算该阶段已完成的天数
          let completedDays = 0
          for (let day = phaseStartDay; day <= phaseEndDay; day++) {
            if (completedDaysMap[day]) {
              completedDays++
            }
          }
          
          let phaseStatus = 'pending'
          let phaseStatusText = '未开始'
          let progress = 0
          
          if (completedDays >= phaseTotalDays) {
            phaseStatus = 'completed'
            phaseStatusText = '已完成'
            progress = 100
          } else if (completedDays > 0) {
            phaseStatus = 'active'
            phaseStatusText = '进行中'
            progress = Math.round((completedDays / phaseTotalDays) * 100)
          }
          
          return {
            ...phase,
            phaseStatus,
            phaseStatusText,
            completedDays,
            totalDays: phaseTotalDays,
            progress
          }
        })

        this.setData({
          currentStep: 3,
          planGenerated: true,
          planId: plan.id,
          planOutline: plan.outline,
          phases: phases,
          currentPhase: plan.currentPhase || 1,
          goalName: plan.name.replace('学习计划', '')
        })

        // 加载当前阶段的任务
        this.loadPhaseTasks(plan.currentPhase || 1)
      }
    } catch (err) {
      wx.showToast({ title: '加载失败', icon: 'none' })
    }
    wx.hideLoading()
  },

  initSchedule() {
    const schedule = {}
    this.data.timeSlots.forEach(slot => {
      schedule[slot] = [false, false, false, false, false, false, false]
    })
    this.setData({ schedule })
  },

  // 步骤1相关方法
  selectGoalType(e) {
    this.setData({ selectedGoalType: e.currentTarget.dataset.type })
  },

  onGoalNameInput(e) {
    this.setData({ goalName: e.detail.value })
  },

  onStartDateChange(e) {
    this.setData({ startDate: e.detail.value })
  },

  onDateChange(e) {
    this.setData({ examDate: e.detail.value })
  },

  onRemarkInput(e) {
    this.setData({ remark: e.detail.value })
  },

  // 步骤2相关方法
  setStudentStatus(e) {
    const isStudent = e.currentTarget.dataset.status === 'true'
    this.setData({ isStudent })
  },

  // 上传课表/安排表
  uploadSchedule() {
    wx.chooseMessageFile({
      count: 1,
      type: 'file',
      extension: ['pdf', 'jpg', 'jpeg', 'png'],
      success: (res) => {
        const file = res.tempFiles[0]
        wx.showLoading({ title: '上传中...' })

        // 上传到后端OSS
        wx.uploadFile({
          url: request.SCHEDULE_UPLOAD_URL,
          filePath: file.path,
          name: 'file',
          header: {
            'Authorization': 'Bearer ' + app.globalData.token
          },
          success: (uploadRes) => {
            wx.hideLoading()
            try {
              const data = JSON.parse(uploadRes.data)
              if (data.code === 200) {
                const recognizedText = data.data.recognizedText
                this.setData({
                  scheduleFile: {
                    name: file.name,
                    path: file.path,
                    url: data.data.url,
                    recognizedText: recognizedText
                  }
                })
                if (recognizedText) {
                  wx.showToast({ title: '上传成功，已识别课表内容', icon: 'success' })
                } else {
                  wx.showModal({
                    title: '上传成功',
                    content: '文件已上传，但未能识别出文字内容。可能原因：图片模糊、格式不支持。你可以手动填写课程安排，或重新上传更清晰的图片。',
                    showCancel: false
                  })
                }
              } else {
                wx.showToast({ title: data.message || '上传失败', icon: 'none' })
              }
            } catch (e) {
              wx.showToast({ title: '解析失败', icon: 'none' })
            }
          },
          fail: () => {
            wx.hideLoading()
            wx.showToast({ title: '上传失败', icon: 'none' })
          }
        })
      }
    })
  },

  // 删除上传的文件
  removeScheduleFile() {
    this.setData({ scheduleFile: null })
  },

  // 构建时间安排描述
  buildScheduleDescription() {
    const { isStudent, schedule, freeTime, scheduleFile, weekDays, startDate, examDate } = this.data
    let desc = ''

    // 获取每周的课程/空闲安排
    let weeklySchedule = {}
    if (scheduleFile && scheduleFile.recognizedText) {
      desc += '用户已上传课表，内容如下：' + scheduleFile.recognizedText + '。'
    }

    if (isStudent) {
      // 学生模式：构建每周课程安排
      desc += '用户是学生，每周课程安排如下：'
      weekDays.forEach((day, dayIndex) => {
        const classes = []
        Object.keys(schedule).forEach(time => {
          if (schedule[time][dayIndex]) {
            classes.push(time)
          }
        })
        if (classes.length > 0) {
          desc += day + '有课（' + classes.join('、') + '），不安排学习任务。'
        } else {
          desc += day + '无课，可以安排学习。'
        }
      })
    } else {
      // 非学生模式：构建每周空闲时间
      desc += '用户不是学生，每周空闲时间如下：'
      weekDays.forEach((day, dayIndex) => {
        if (freeTime[dayIndex].length > 0) {
          desc += day + '空闲（' + freeTime[dayIndex].join('、') + '），可以安排学习。'
        } else {
          desc += day + '无空闲时间，不安排任务。'
        }
      })
    }

    // 添加日期范围信息
    if (startDate && examDate) {
      const start = new Date(startDate)
      const end = new Date(examDate)
      const totalDays = Math.ceil((end - start) / (1000 * 60 * 60 * 24)) + 1
      desc += '计划时间从' + startDate + '到' + examDate + '，共' + totalDays + '天。'
      desc += '请根据每周安排，推算出具体哪些日期可以安排任务。'
    }

    return desc
  },

  toggleSchedule(e) {
    const { time, day } = e.currentTarget.dataset
    const dayIndex = parseInt(day)
    const schedule = this.data.schedule
    if (schedule[time] && schedule[time][dayIndex] !== undefined) {
      schedule[time][dayIndex] = !schedule[time][dayIndex]
      this.setData({ schedule })
    }
  },

  toggleDayExpand(e) {
    const day = e.currentTarget.dataset.day
    const expandedDays = this.data.expandedDays
    expandedDays[day] = !expandedDays[day]
    this.setData({ expandedDays })
  },

  toggleFreeTime(e) {
    const { day, time } = e.currentTarget.dataset
    const freeTime = this.data.freeTime
    const index = freeTime[day].indexOf(time)
    if (index > -1) {
      freeTime[day].splice(index, 1)
    } else {
      freeTime[day].push(time)
    }
    this.setData({ freeTime })
  },

  // 步骤控制
  nextStep() {
    const { currentStep } = this.data
    
    if (currentStep === 1) {
      if (!this.data.selectedGoalType || !this.data.goalName) {
        wx.showToast({ title: '请填写目标信息', icon: 'none' })
        return
      }
    }
    
    if (currentStep === 2) {
      this.setData({ currentStep: 3, isGenerating: true })
      this.generatePlan()
      return
    }
    
    this.setData({ currentStep: currentStep + 1 })
  },

  prevStep() {
    if (this.data.currentStep > 1) {
      this.setData({ currentStep: this.data.currentStep - 1 })
    }
  },

  // ========== 第一步：生成计划大纲 ==========
  async generatePlan() {
    const { selectedGoalType, goalName, examDate, remark, startDate } = this.data
    
    let totalDays = 30
    if (examDate && startDate) {
      // 计算开始日期到结束日期的天数（包含首尾）
      const start = new Date(startDate)
      const end = new Date(examDate)
      totalDays = Math.ceil((end - start) / (1000 * 60 * 60 * 24)) + 1
      if (totalDays < 1) totalDays = 1
      if (totalDays > 365) totalDays = 365
    } else if (examDate) {
      const today = new Date()
      const exam = new Date(examDate)
      totalDays = Math.ceil((exam - today) / (1000 * 60 * 60 * 24)) + 1
      if (totalDays < 1) totalDays = 1
      if (totalDays > 365) totalDays = 365
    }

    console.log('生成计划:', { startDate, examDate, totalDays })

    // 构建时间安排描述
    const scheduleDescription = this.buildScheduleDescription()
    console.log('时间安排描述:', scheduleDescription)

    try {
      const res = await request.post('/api/plan/generate', {
        goalType: selectedGoalType,
        goalName,
        examDate,
        startDate,
        remark,
        totalDays,
        scheduleDescription: scheduleDescription
      })

      if (res.code === 200 && res.data) {
        const plan = res.data
        
        // 解析大纲
        let phases = []
        try {
          const outline = JSON.parse(plan.outline)
          phases = outline.phases || []
        } catch (e) {
          console.log('解析大纲失败', e)
          // 使用默认大纲
          phases = this.getDefaultPhases(totalDays)
        }

        this.setData({
          isGenerating: false,
          planGenerated: true,
          planId: plan.id,
          planOutline: plan.outline,
          phases: phases,
          currentPhase: 1
        })

        // 自动加载第一阶段的任务
        this.loadPhaseTasks(1)
      }
    } catch (err) {
      wx.showToast({ title: '生成失败，请重试', icon: 'none' })
      this.setData({ isGenerating: false })
    }
  },

  getDefaultPhases(totalDays) {
    const phaseDays = Math.floor(totalDays / 3)
    return [
      { phase: 1, name: '基础阶段', startDay: 1, endDay: phaseDays, theme: '掌握基础知识' },
      { phase: 2, name: '进阶阶段', startDay: phaseDays + 1, endDay: phaseDays * 2, theme: '深入学习' },
      { phase: 3, name: '冲刺阶段', startDay: phaseDays * 2 + 1, endDay: totalDays, theme: '强化冲刺' }
    ]
  },

  // ========== 第二步：按需加载阶段任务 ==========
  async loadPhaseTasks(phase) {
    const { planId, phaseTasks, isLoadingPhase } = this.data
    
    // 如果正在加载中，提示用户等待
    if (isLoadingPhase) {
      wx.showToast({ title: '请等待当前任务生成完成', icon: 'none' })
      return
    }
    
    const isFirstLoad = !phaseTasks[phase] || phaseTasks[phase].length === 0

    this.setData({ isLoadingPhase: true, currentPhase: phase })
    if (isFirstLoad) {
      wx.showLoading({ title: '生成阶段' + phase + '任务中...' })
    }

    try {
      const res = await request.post(`/api/plan/${planId}/phase/${phase}/generate`)
      
      if (res.code === 200 && res.data) {
        const tasks = res.data.map(t => ({
          id: t.id,
          dayNumber: t.dayNumber,
          taskDate: t.taskDate,
          name: t.taskName,
          type: t.taskType,
          description: t.taskDescription,
          duration: t.taskDuration,
          icon: t.taskIcon || '📚',
          color: t.taskColor || '#4A90D9',
          completed: t.completed || 0
        }))

        // 按天分组
        const groupedTasks = this.groupTasksByDay(tasks)
        
        phaseTasks[phase] = groupedTasks
        this.setData({ 
          phaseTasks,
          isLoadingPhase: false 
        })
        if (isFirstLoad) {
          wx.hideLoading()
          wx.showToast({ title: '生成完成', icon: 'success' })
        }
      }
    } catch (err) {
      if (isFirstLoad) wx.hideLoading()
      wx.showToast({ title: '加载失败', icon: 'none' })
      this.setData({ isLoadingPhase: false })
    }
  },

  // 按天分组
  groupTasksByDay(tasks) {
    const dayMap = {}
    tasks.forEach(task => {
      if (!dayMap[task.dayNumber]) {
        dayMap[task.dayNumber] = {
          day: task.dayNumber,
          date: task.taskDate,
          tasks: []
        }
      }
      dayMap[task.dayNumber].tasks.push(task)
    })
    
    return Object.values(dayMap).sort((a, b) => a.day - b.day)
  },

  // 切换阶段
  switchPhase(e) {
    const phase = e.currentTarget.dataset.phase
    this.loadPhaseTasks(phase)
  },

  // 完成任务
  async completeTask(e) {
    const taskId = e.currentTarget.dataset.id
    const phase = this.data.currentPhase
    
    try {
      const res = await request.put(`/api/plan/task/complete/${taskId}`)
      if (res.code === 200) {
        wx.showToast({ title: '完成！', icon: 'success' })
        // 更新本地数据
        const phaseTasks = this.data.phaseTasks
        if (phaseTasks[phase]) {
          phaseTasks[phase].forEach(day => {
            day.tasks.forEach(task => {
              if (task.id === taskId) {
                task.completed = 1
              }
            })
          })
          this.setData({ phaseTasks })
        }
      }
    } catch (err) {
      wx.showToast({ title: '操作失败', icon: 'none' })
    }
  },

  // 查看完整计划
  viewFullPlan() {
    const { phases, phaseTasks } = this.data
    let allTasks = []
    
    // 收集所有已加载的任务
    phases.forEach(phase => {
      if (phaseTasks[phase.phase]) {
        allTasks = allTasks.concat(phaseTasks[phase.phase])
      }
    })
    
    this.setData({ 
      showFullPlan: true,
      fullPlanTasks: allTasks
    })
  },

  hideFullPlan() {
    this.setData({ showFullPlan: false })
  },

  // 重新生成
  regeneratePlan() {
    this.setData({ 
      isGenerating: true, 
      planGenerated: false,
      phases: [],
      phaseTasks: {}
    })
    this.generatePlan()
  },

  // 删除计划
  deletePlan() {
    const { planId } = this.data
    wx.showModal({
      title: '确认删除',
      content: '确定要删除这个学习计划吗？删除后无法恢复。',
      success: async (res) => {
        if (res.confirm) {
          try {
            const result = await request.del(`/api/plan/delete/${planId}`)
            if (result.code === 200) {
              wx.showToast({ title: '删除成功', icon: 'success' })
              setTimeout(() => {
                wx.navigateBack()
              }, 1500)
            }
          } catch (err) {
            wx.showToast({ title: '删除失败', icon: 'none' })
          }
        }
      }
    })
  }
})