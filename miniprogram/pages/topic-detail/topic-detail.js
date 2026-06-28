const app = getApp()
const request = require('../../utils/request')

Page({
  data: {
    topicId: null,
    topic: {},
    replies: [],
    replyContent: ''
  },

  onLoad(options) {
    if (options.id) {
      this.setData({ topicId: options.id })
      this.loadTopic(options.id)
      this.loadReplies(options.id)
    }
  },

  async loadTopic(id) {
    try {
      const res = await request.get(`/api/topic/detail/${id}`)
      if (res.code === 200 && res.data) {
        this.setData({ topic: res.data })
      }
    } catch (err) {
      console.log('加载话题失败', err)
    }
  },

  async loadReplies(topicId) {
    try {
      const res = await request.get(`/api/topic/replies/${topicId}`)
      if (res.code === 200 && res.data) {
        this.setData({ replies: res.data })
      }
    } catch (err) {
      console.log('加载回复失败', err)
    }
  },

  onReplyInput(e) {
    this.setData({ replyContent: e.detail.value })
  },

  async sendReply() {
    const { topicId, replyContent } = this.data
    if (!replyContent.trim()) return

    try {
      const res = await request.post('/api/topic/reply', {
        topicId: String(topicId),
        content: replyContent
      })
      if (res.code === 200) {
        wx.showToast({ title: '回复成功', icon: 'success' })
        this.setData({ replyContent: '' })
        this.loadReplies(topicId)
        this.loadTopic(topicId)
      } else {
        wx.showToast({ title: res.message || '回复失败', icon: 'none' })
      }
    } catch (err) {
      wx.showToast({ title: err.message || '回复失败', icon: 'none' })
    }
  }
})
