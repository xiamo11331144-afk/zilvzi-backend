const app = getApp()
const request = require('../../utils/request')

Page({
  data: {
    suggestions: [],
    messages: [
      { id: 1, content: '你好！我是你的AI习惯养成助手，有什么可以帮你的吗？', isUser: false }
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
    predictions: [],
    userContext: null
  },

  onLoad() {
    this.loadUserContext()
    this.loadChatHistory()
    this.loadSuggestions()
  },

  // 加载用户上下文数据
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
        const planNames = planRes.data
          .filter(p => p.status === 'active')
          .map(p => p.name)
          .join('、')
        context.plans = planNames || '暂无进行中的计划'
      }

      this.setData({ userContext: context })
      console.log('[AI] 用户上下文加载完成:', context)
    } catch (err) {
      console.log('[AI] 加载用户上下文失败:', err)
    }
  },

  // 加载对话历史
  async loadChatHistory() {
    try {
      const res = await request.get('/api/ai/history', { limit: 50 })
      if (res.code === 200 && res.data && res.data.length > 0) {
        const historyMessages = res.data.map((item, index) => ({
          id: item.id || (Date.now() + index),
          content: item.content,
          isUser: item.role === 'user'
        }))
        // 保留欢迎语 + 追加历史
        const welcome = this.data.messages[0]
        const messages = [welcome, ...historyMessages]
        this.setData({
          messages,
          scrollToView: `msg-${historyMessages[historyMessages.length - 1].id}`
        })
        console.log('[AI] 加载对话历史:', historyMessages.length, '条')
      }
    } catch (err) {
      console.log('[AI] 加载对话历史失败:', err)
    }
  },

  async loadSuggestions() {
    try {
      const context = this.data.userContext || {}
      const res = await request.post('/api/ai/advice', {
        totalCheckins: context.totalCheckins || 0,
        streakDays: context.streakDays || 0,
        level: context.level || 1,
        plans: context.plans || ''
      })
      if (res.code === 200 && res.data) {
        this.setData({
          suggestions: [
            { id: 1, title: 'AI个性化建议', icon: '🤖', color: '#4A90D9', time: '刚刚', content: res.data }
          ]
        })
      }
    } catch (err) {
      console.log('加载AI建议失败', err)
    }
  },

  onInput(e) {
    this.setData({ inputValue: e.detail.value })
  },

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
      // 后端自动保存对话历史，无需前端额外处理
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

  // 清除对话历史
  async clearHistory() {
    wx.showModal({
      title: '确认清除',
      content: '确定要清除所有对话历史吗？',
      success: async (res) => {
        if (res.confirm) {
          try {
            await request.del('/api/ai/history')
            this.setData({
              messages: [
                { id: 1, content: '你好！我是你的AI习惯养成助手，有什么可以帮你的吗？', isUser: false }
              ]
            })
            wx.showToast({ title: '已清除', icon: 'success' })
          } catch (err) {
            wx.showToast({ title: '清除失败', icon: 'none' })
          }
        }
      }
    })
  },

  askQuestion(e) {
    const question = e.currentTarget.dataset.question
    this.setData({ inputValue: question })
    this.sendMessage()
  },

  feedback(e) {
    const { id, type } = e.currentTarget.dataset
    wx.showToast({
      title: type === 'helpful' ? '感谢反馈' : '会继续改进',
      icon: 'none'
    })
  }
})