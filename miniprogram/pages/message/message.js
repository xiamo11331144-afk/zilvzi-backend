const app = getApp()
const request = require('../../utils/request')

Page({
  data: {
    partnerId: null,
    partnerNickname: '',
    partnerAvatar: '',
    messages: [],
    inputMsg: '',
    loading: true,
    // 对话列表模式
    conversations: [],
    viewMode: 'list', // 'list' | 'chat'
    scrollToView: ''
  },

  onLoad(options) {
    if (options.partnerId) {
      // 直接进入聊天
      this.setData({
        partnerId: Number(options.partnerId),
        partnerNickname: decodeURIComponent(options.nickname || ''),
        partnerAvatar: decodeURIComponent(options.avatar || ''),
        viewMode: 'chat'
      })
      wx.setNavigationBarTitle({ title: this.data.partnerNickname || '私信' })
      this.loadConversation(options.partnerId)
    } else {
      // 对话列表模式
      wx.setNavigationBarTitle({ title: '私信' })
      this.loadConversations()
    }
  },

  onShow() {
    if (this.data.viewMode === 'chat' && this.data.partnerId) {
      this.loadConversation(this.data.partnerId)
    } else if (this.data.viewMode === 'list') {
      this.loadConversations()
    }
  },

  // ========== 对话列表模式 ==========

  async loadConversations() {
    this.setData({ loading: true })
    try {
      const res = await request.get('/api/message/conversations', { page: 1, size: 50 })
      if (res.code === 200 && res.data) {
        const list = res.data.map(item => ({
          partnerId: item.partnerId,
          nickname: item.partnerNickname || '用户',
          avatar: item.partnerAvatar || '',
          lastMessage: item.content || '',
          lastTime: item.createTime || '',
          unreadCount: item.unreadCount || 0
        }))
        this.setData({ conversations: list })
      }
    } catch (err) {
      console.log('加载对话列表失败', err)
    } finally {
      this.setData({ loading: false })
    }
  },

  enterChat(e) {
    const { id, nickname, avatar } = e.currentTarget.dataset
    this.setData({
      partnerId: Number(id),
      partnerNickname: nickname || '',
      partnerAvatar: avatar || '',
      viewMode: 'chat',
      messages: []
    })
    wx.setNavigationBarTitle({ title: nickname || '私信' })
    this.loadConversation(id)
  },

  goBackToList() {
    this.setData({
      viewMode: 'list',
      partnerId: null,
      messages: []
    })
    wx.setNavigationBarTitle({ title: '私信' })
    this.loadConversations()
  },

  // ========== 聊天模式 ==========

  async loadConversation(partnerId) {
    this.setData({ loading: true })
    try {
      const res = await request.get(`/api/message/conversation/${partnerId}`, { page: 1, size: 50 })
      if (res.code === 200 && res.data) {
        const userId = Number(app.globalData.userId)
        console.log('[Message] current userId:', userId, typeof app.globalData.userId)
        const messages = res.data.map(msg => {
          const isMine = Number(msg.fromUserId) === userId
          let proofImage = ''
          let displayContent = msg.content || ''
          if (msg.type === 'checkin_proof' || msg.type === 'checkin_confirmed') {
            const imgMatch = displayContent.match(/\[IMG\](.*?)\[\/IMG\]/)
            if (imgMatch) {
              proofImage = imgMatch[1]
              displayContent = displayContent.replace(/\n?\[IMG\].*?\[\/IMG\]/, '')
            }
          }
          return {
            ...msg,
            isMine,
            proofImage,
            displayContent,
            isCheckinProof: msg.type === 'checkin_proof' && !isMine,
            isMyCheckinProof: msg.type === 'checkin_proof' && isMine,
            isCheckinConfirmed: msg.type === 'checkin_confirmed'
          }
        })
        this.setData({ messages, scrollToView: 'msg-bottom' })
        this.scrollToBottom()
      }
    } catch (err) {
      console.log('加载对话失败', err)
    } finally {
      this.setData({ loading: false })
    }
  },

  onInputMsg(e) {
    this.setData({ inputMsg: e.detail.value })
  },

  onConfirmSend(e) {
    // 键盘确认键发送
    this.setData({ inputMsg: e.detail.value })
    this.sendMsg()
  },

  async sendMsg() {
    const content = this.data.inputMsg.trim()
    if (!content) return

    const { partnerId } = this.data
    if (!partnerId) {
      wx.showToast({ title: '无法发送', icon: 'none' })
      return
    }

    try {
      const res = await request.post('/api/message/send', {
        toUserId: partnerId,
        content: content
      })
      if (res.code === 200 && res.data) {
        const userId = app.globalData.userId
        const newMsg = {
          ...res.data,
          isMine: true,
          fromUserId: userId
        }
        this.setData({
          messages: [...this.data.messages, newMsg],
          inputMsg: ''
        })
        this.scrollToBottom()
      }
    } catch (err) {
      wx.showToast({ title: err.message || '发送失败', icon: 'none' })
    }
  },

  scrollToBottom() {
    this.setData({ scrollToView: 'msg-bottom' })
  },

  previewImage(e) {
    const url = e.currentTarget.dataset.url
    wx.previewImage({ urls: [url] })
  },

  // 确认搭子打卡
  async confirmCheckin(e) {
    const checkinId = e.currentTarget.dataset.checkinid
    const msgId = e.currentTarget.dataset.msgid
    if (!checkinId) return

    wx.showModal({
      title: '确认打卡',
      content: '确认对方已完成打卡？',
      success: async (res) => {
        if (!res.confirm) return
        wx.showLoading({ title: '确认中...' })
        try {
          const result = await request.post('/api/checkin/review', {
            checkinId: Number(checkinId),
            approved: true
          })
          wx.hideLoading()
          if (result.code === 200) {
            wx.showToast({ title: '已确认打卡', icon: 'success' })
            this.loadConversation(this.data.partnerId)
          } else {
            wx.showToast({ title: result.message || '确认失败', icon: 'none' })
          }
        } catch (err) {
          wx.hideLoading()
          wx.showToast({ title: '确认失败', icon: 'none' })
        }
      }
    })
  }
})