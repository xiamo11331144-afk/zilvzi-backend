const request = require('../../utils/request')

Component({
  data: {
    ballX: 30,
    ballY: 200,
    isDragging: false,
    isOpen: false,
    startX: 0,
    startY: 0,
    startBallX: 0,
    startBallY: 0,
    messages: [
      { id: 1, content: '你好！我是AI习惯养成助手，有什么可以帮你的吗？', isUser: false }
    ],
    inputValue: '',
    isTyping: false,
    scrollToView: '',
    quickQuestions: [
      '如何克服拖延症？',
      '怎样养成早起习惯？',
      '坚持不下去怎么办？',
      '如何制定合理的目标？'
    ],
    userContext: null
  },

  lifetimes: {
    attached() {
      this.loadUserContext()
    }
  },

  methods: {
    // 加载用户上下文
    async loadUserContext() {
      try {
        const [dashboardRes, planRes] = await Promise.all([
          request.get('/api/stats/dashboard'),
          request.get('/api/plan/list')
        ])
        const context = {}
        if (dashboardRes.code === 200 && dashboardRes.data) {
          context.streakDays = dashboardRes.data.streakDays || 0
          context.totalCheckins = dashboardRes.data.totalCheckins || 0
          context.level = dashboardRes.data.level || 1
        }
        if (planRes.code === 200 && planRes.data && planRes.data.length > 0) {
          context.plans = planRes.data.filter(p => p.status === 'active').map(p => p.name).join('、') || '暂无'
        }
        this.setData({ userContext: context })
      } catch (err) {
        console.log('[AiBall] 加载用户上下文失败:', err)
      }
    },

    // 触摸开始
    onTouchStart(e) {
      const touch = e.touches[0]
      this.setData({
        isDragging: false,
        startX: touch.clientX,
        startY: touch.clientY,
        startBallX: this.data.ballX,
        startBallY: this.data.ballY
      })
    },

    // 触摸移动
    onTouchMove(e) {
      const touch = e.touches[0]
      const deltaX = touch.clientX - this.data.startX
      const deltaY = touch.clientY - this.data.startY

      // 超过 5px 才算拖拽
      if (Math.abs(deltaX) > 5 || Math.abs(deltaY) > 5) {
        const systemInfo = wx.getSystemInfoSync()
        const screenWidth = systemInfo.windowWidth
        const screenHeight = systemInfo.windowHeight

        // 将 px 转换为 rpx
        const ratio = 750 / screenWidth
        let newX = this.data.startBallX - deltaX * ratio
        let newY = this.data.startBallY - deltaY * ratio

        // 边界限制
        newX = Math.max(0, Math.min(newX, 750 - 110))
        newY = Math.max(100, Math.min(newY, screenHeight * ratio - 200))

        this.setData({
          isDragging: true,
          ballX: newX,
          ballY: newY
        })
      }
    },

    // 触摸结束
    onTouchEnd() {
      if (this.data.isDragging) {
        // 吸附到最近的边缘
        const midScreen = 375
        const targetX = this.data.ballX < midScreen ? 20 : 750 - 130
        this.setData({
          isDragging: false,
          ballX: targetX
        })
        return
      }
    },

    // 点击悬浮球
    onBallTap() {
      if (this.data.isDragging) return
      this.setData({ isOpen: true })
      // 每次打开时刷新上下文和历史
      this.loadUserContext()
      this.loadChatHistory()
    },

    // 加载对话历史
    async loadChatHistory() {
      try {
        const res = await request.get('/api/ai/history', { limit: 20 })
        if (res.code === 200 && res.data && res.data.length > 0) {
          const historyMessages = res.data.map((item, index) => ({
            id: item.id || (Date.now() + index),
            content: item.content,
            isUser: item.role === 'user'
          }))
          const welcome = this.data.messages[0]
          const messages = [welcome, ...historyMessages]
          this.setData({
            messages,
            scrollToView: `msg-${historyMessages[historyMessages.length - 1].id}`
          })
        }
      } catch (err) {
        console.log('[AiBall] 加载对话历史失败:', err)
      }
    },

    // 关闭面板
    closePanel() {
      this.setData({ isOpen: false })
    },

    // 输入事件
    onInput(e) {
      this.setData({ inputValue: e.detail.value })
    },

    // 发送消息
    async sendMessage() {
      const content = this.data.inputValue.trim()
      if (!content) return

      const userMsg = {
        id: Date.now(),
        content,
        isUser: true
      }

      const messages = [...this.data.messages, userMsg]
      this.setData({
        messages,
        inputValue: '',
        scrollToView: `msg-${userMsg.id}`,
        isTyping: true
      })

      try {
        const res = await request.post('/api/ai/chat', {
          message: content,
          context: this.data.userContext || {}
        })
        const aiMsg = {
          id: Date.now() + 1,
          content: res.code === 200 ? res.data : '抱歉，暂时无法回答。',
          isUser: false
        }
        this.setData({
          messages: [...this.data.messages, aiMsg],
          isTyping: false,
          scrollToView: `msg-${aiMsg.id}`
        })
      } catch (err) {
        const aiMsg = {
          id: Date.now() + 1,
          content: '网络错误，请稍后再试。',
          isUser: false
        }
        this.setData({
          messages: [...this.data.messages, aiMsg],
          isTyping: false
        })
      }
    },

    // 快捷问题
    askQuickQuestion(e) {
      const question = e.currentTarget.dataset.question
      this.setData({ inputValue: question })
      this.sendMessage()
    },

    // 跳转到完整 AI 页面
    goToAiPage() {
      wx.navigateTo({ url: '/pages/ai/ai' })
    }
  }
})