const app = getApp()
const request = require('../../utils/request')
const badges = require('../../utils/badges')

Page({
  data: {
    badgeCounts: badges.emptyCounts(),
    badgeError: '',
    partnerId: null,
    partnerNickname: '',
    partnerAvatar: '',
    messages: [],
    inputMsg: '',
    reviewingCheckinId: null,
    loading: true,
    loadError: '',
    conversationPage: 0, conversationsHasMore: true,
    messagePage: 0, messagesHasMore: true,
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
    } else {
      // 对话列表模式
      wx.setNavigationBarTitle({ title: '私信' })
    }
  },

  onHide() {
    this._visible = false
    this._loadSequence = (this._loadSequence || 0) + 1
    badges.unbind(this)
  },
  onUnload() { this.onHide() },
  onReachBottom() { if (this.data.viewMode === 'list') this.loadMoreConversations() },

  onShow() {
    this._visible = true
    badges.bind(this)
    if (this.data.viewMode === 'chat' && this.data.partnerId) {
      this.loadConversation(this.data.partnerId)
    } else if (this.data.viewMode === 'list') {
      this.loadConversations()
    }
  },

  // ========== 对话列表模式 ==========

  async loadConversations(append = false) {
    const sequence = this._loadSequence = (this._loadSequence || 0) + 1
    const page = append ? this.data.conversationPage + 1 : 1
    this._retryAppend = append
    this.setData({ loading: true, loadError: '' })
    try {
      const res = await request.get('/api/message/conversations', { page, size: 50 })
      if (!this._visible || sequence !== this._loadSequence) return
      if (res.code !== 200 || !Array.isArray(res.data)) throw new Error(res.message || '私信列表加载失败')
      const list = res.data.map(item => ({
        partnerId: item.partnerId, nickname: item.partnerNickname || '用户',
        avatar: item.partnerAvatar || '', lastMessage: item.content || '',
        lastTime: item.createTime || '', unreadCount: Number(item.unreadCount) || 0
      }))
      this.setData({
        conversations: append ? this.data.conversations.concat(list) : list,
        conversationPage: page, conversationsHasMore: res.data.length === 50
      })
    } catch (err) {
      if (this._visible && sequence === this._loadSequence) this.setData({ loadError: err.message || '私信列表加载失败' })
    } finally {
      if (this._visible && sequence === this._loadSequence) this.setData({ loading: false })
    }
  },

  loadMoreConversations() {
    if (!this.data.loading && this.data.conversationsHasMore) return this.loadConversations(true)
  },
  loadOlderMessages() {
    if (!this.data.loading && this.data.messagesHasMore) return this.loadConversation(this.data.partnerId, true)
  },
  retryLoad() {
    return this.data.viewMode === 'list' ? this.loadConversations(this._retryAppend) : this.loadConversation(this.data.partnerId, this._retryAppend)
  },
  retryBadges() { return badges.refresh(true) },

  enterChat(e) {
    const { id, nickname, avatar } = e.currentTarget.dataset
    this.setData({
      partnerId: Number(id),
      partnerNickname: nickname || '',
      partnerAvatar: avatar || '',
      viewMode: 'chat',
      messages: [], messagePage: 0, messagesHasMore: true, loadError: '', scrollToView: ''
    })
    wx.setNavigationBarTitle({ title: nickname || '私信' })
    this.loadConversation(id)
  },

  goBackToList() {
    this.setData({
      viewMode: 'list',
      partnerId: null,
      messages: [], messagePage: 0, messagesHasMore: true, loadError: '', scrollToView: ''
    })
    wx.setNavigationBarTitle({ title: '私信' })
    this.loadConversations()
  },

  // ========== 聊天模式 ==========

  async loadConversation(partnerId, append = false) {
    const sequence = this._loadSequence = (this._loadSequence || 0) + 1
    const page = append ? this.data.messagePage + 1 : 1
    this._retryAppend = append
    this.setData({ loading: true, loadError: '' })
    try {
      const res = await request.get(`/api/message/conversation/${partnerId}`, { page, size: 50 })
      if (res.code === 200) badges.refresh(true) // GET成功后，服务端已标记本页收到的消息。
      if (!this._visible || sequence !== this._loadSequence) return
      if (res.code !== 200 || !Array.isArray(res.data)) throw new Error(res.message || '对话加载失败')
      {
        const userId = Number(app.globalData.userId)
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
        this.setData({
          messages: append ? messages.concat(this.data.messages) : messages,
          messagePage: page, messagesHasMore: res.data.length === 50,
          scrollToView: append ? this.data.scrollToView : ''
        })
        if (!append) this.scrollToBottom()
      }
    } catch (err) {
      if (this._visible && sequence === this._loadSequence) this.setData({ loadError: err.message || '对话加载失败' })
    } finally {
      if (this._visible && sequence === this._loadSequence) this.setData({ loading: false })
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

  // 审核结果由服务端校验权限和当前打卡状态。
  confirmCheckin(e) {
    this.reviewCheckin(e, true)
  },

  rejectCheckin(e) {
    this.reviewCheckin(e, false)
  },

  reviewCheckin(e, approved) {
    const checkinId = Number(e.currentTarget.dataset.checkinid)
    if (!Number.isSafeInteger(checkinId) || checkinId <= 0 || this.data.reviewingCheckinId !== null) return
    const partnerId = this.data.partnerId
    this.setData({ reviewingCheckinId: checkinId })

    wx.showModal({
      title: approved ? '确认打卡' : '驳回打卡',
      content: approved ? '确认对方已完成打卡？' : '',
      editable: !approved,
      placeholderText: approved ? '' : '驳回原因（选填），对方可补充凭证后重提',
      confirmText: approved ? '确认通过' : '确认驳回',
      confirmColor: approved ? '#52C41A' : '#D93025',
      success: async (res) => {
        if (!res.confirm) {
          this.setData({ reviewingCheckinId: null })
          return
        }
        wx.showLoading({ title: approved ? '确认中...' : '驳回中...' })
        try {
          const params = { checkinId, approved }
          if (!approved) params.comment = (res.content || '').trim()
          const result = await request.post('/api/checkin/review', params)
          if (result.code === 200) {
            wx.showToast({ title: approved ? '已确认打卡' : '已驳回打卡', icon: 'success' })
            if (this._visible && this.data.viewMode === 'chat' && this.data.partnerId === partnerId) {
              await this.loadConversation(partnerId)
            }
          } else {
            wx.showToast({ title: result.message || '审核失败', icon: 'none' })
          }
        } catch (err) {
          wx.showToast({ title: err.message || '审核失败，请重试', icon: 'none' })
        } finally {
          wx.hideLoading()
          this.setData({ reviewingCheckinId: null })
        }
      },
      fail: () => {
        this.setData({ reviewingCheckinId: null })
        wx.showToast({ title: '无法打开审核窗口，请重试', icon: 'none' })
      }
    })
  }
})
