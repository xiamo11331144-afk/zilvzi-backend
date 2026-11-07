const request = require('../../utils/request')
const badges = require('../../utils/badges')

const actionTexts = {
  partner_apply: '想成为你的搭子', partner_accept: '同意了你的搭子申请',
  partner_reject: '暂未同意你的搭子申请', partner_remove: '已解除与你的搭子关系',
  checkin_remind: '提醒你记得打卡哦~', private_message: '给你发来一条消息',
  comment: '评论了你的帖子', like: '赞了你的帖子', topic_reply: '回复了你的话题',
  checkin_review: '提交了打卡，等待你确认', checkin_approved: '你的打卡已通过审核',
  checkin_rejected: '你的打卡未通过审核', streak_break: '断卡了，快去打卡吧'
}

Page({
  data: {
    badgeCounts: badges.emptyCounts(), badgeError: '',
    category: 'notice', notifications: [], unreadCount: 0,
    page: 0, size: 20, hasMore: true, loading: false, loadError: '', actionBusy: false
  },

  onLoad(options) {
    this.setData({ category: options.category === 'circle' ? 'circle' : 'notice' })
  },
  onShow() {
    this._visible = true
    badges.bind(this)
    this.loadNotifications()
  },
  onHide() {
    this._visible = false
    this._loadSequence = (this._loadSequence || 0) + 1
    badges.unbind(this)
  },
  onUnload() { this.onHide() },
  onReachBottom() { this.loadMore() },

  selectCategory(e) {
    const category = e.currentTarget.dataset.category
    if (category === this.data.category || this.data.actionBusy) return
    this.setData({ category, notifications: [], unreadCount: category === 'circle' ? this.data.badgeCounts.circleUnread : this.data.badgeCounts.notificationUnread, page: 0, hasMore: true, loadError: '' })
    this.loadNotifications()
  },

  async loadNotifications(append = false) {
    const category = this.data.category
    const page = append ? this.data.page + 1 : 1
    const sequence = this._loadSequence = (this._loadSequence || 0) + 1
    this.setData({ loading: true, loadError: '' })
    try {
      const res = await request.get('/api/notification/list', { category, page, size: this.data.size })
      if (!this._visible || sequence !== this._loadSequence) return
      if (res.code !== 200 || !res.data || !Array.isArray(res.data.list)) {
        throw new Error(res.message || '通知加载失败')
      }
      const list = res.data.list.map(n => ({
        ...n,
        displayText: n.content || actionTexts[n.type] || '发送了一条通知'
      }))
      this.setData({
        notifications: append ? this.data.notifications.concat(list) : list,
        unreadCount: res.data.unreadCount || 0, page, hasMore: res.data.hasMore === true
      })
    } catch (err) {
      if (this._visible && sequence === this._loadSequence) {
        this._failedAppend = append
        this.setData({ loadError: err.message || '通知加载失败' })
      }
    } finally {
      if (this._visible && sequence === this._loadSequence) this.setData({ loading: false })
    }
  },

  retryLoad() { return this.loadNotifications(this._failedAppend === true) },
  beginAction() {
    this._loadSequence = (this._loadSequence || 0) + 1
    this.setData({ actionBusy: true, loading: false })
  },
  loadMore() {
    if (!this.data.loading && this.data.hasMore && !this.data.actionBusy) return this.loadNotifications(true)
  },
  retryBadges() { return badges.refresh(true) },

  async refreshUnread() {
    const counts = await badges.refresh(true)
    if (this._visible) this.setData({ unreadCount: this.data.category === 'circle' ? counts.circleUnread : counts.notificationUnread })
  },

  // 只在服务端成功后更新已读状态；点击内容同时打开关联页面。
  async readNotification(e) {
    if (this.data.actionBusy) return
    const id = Number(e.currentTarget.dataset.id)
    const notification = this.data.notifications.find(n => Number(n.id) === id)
    if (!notification) return
    this.beginAction()
    try {
      if (Number(notification.isRead) === 0) {
        const res = await request.put(`/api/notification/read/${id}`)
        if (res.code !== 200) throw new Error(res.message || '标记已读失败')
        this.setData({ notifications: this.data.notifications.map(n => Number(n.id) === id ? { ...n, isRead: 1 } : n) })
        await this.refreshUnread()
      }
      this.openRelated(notification)
    } catch (err) {
      wx.showToast({ title: err.message || '操作失败', icon: 'none' })
    } finally {
      if (this._visible) this.setData({ actionBusy: false })
      else this.data.actionBusy = false
    }
  },

  openRelated(n) {
    if (n.relatedId && n.relatedType === 'post') {
      // 帖子在所属圈子内展示，当前通知没有圈子ID时保留通知内容。
      return
    }
    if (n.relatedId && n.relatedType === 'topic') {
      wx.navigateTo({ url: `/pages/topic-detail/topic-detail?id=${n.relatedId}` })
    } else if ((n.type === 'private_message' || n.type === 'checkin_review') && n.fromUserId) {
      wx.navigateTo({ url: `/pages/message/message?partnerId=${n.fromUserId}&nickname=${encodeURIComponent(n.fromNickname || '')}` })
    } else if (String(n.type || '').indexOf('partner_') === 0) {
      wx.switchTab({ url: '/pages/partner/partner' })
    } else if (String(n.type || '').indexOf('checkin_') === 0 || n.type === 'streak_break') {
      wx.switchTab({ url: '/pages/index/index' })
    }
  },

  async markAllAsRead() {
    if (this.data.actionBusy || !this.data.unreadCount) return
    this.beginAction()
    try {
      const res = await request.put(`/api/notification/read/all?category=${this.data.category}`)
      if (res.code !== 200) throw new Error(res.message || '标记已读失败')
      this.setData({ notifications: this.data.notifications.map(n => ({ ...n, isRead: 1 })), unreadCount: 0 })
      await this.refreshUnread()
      wx.showToast({ title: '当前分类已读', icon: 'success' })
    } catch (err) {
      wx.showToast({ title: err.message || '操作失败', icon: 'none' })
    } finally {
      if (this._visible) this.setData({ actionBusy: false })
      else this.data.actionBusy = false
    }
  },

  clearAll() {
    if (this.data.actionBusy) return
    const category = this.data.category
    wx.showModal({
      title: '清除当前分类', content: '确定清除当前分类的所有通知吗？其他分类和私信不会被清除。',
      success: async result => {
        if (!result.confirm || this.data.actionBusy || category !== this.data.category || !this._visible) return
        this.beginAction()
        try {
          const res = await request.del(`/api/notification/clear?category=${category}`)
          if (res.code !== 200) throw new Error(res.message || '清除失败')
          this.setData({ notifications: [], unreadCount: 0, page: 0, hasMore: false })
          await this.refreshUnread()
          wx.showToast({ title: '已清除当前分类', icon: 'success' })
        } catch (err) {
          wx.showToast({ title: err.message || '操作失败', icon: 'none' })
        } finally {
          if (this._visible) this.setData({ actionBusy: false })
          else this.data.actionBusy = false
        }
      }
    })
  },

  async deleteOne(e) {
    if (this.data.actionBusy) return
    const id = Number(e.currentTarget.dataset.id)
    this.beginAction()
    try {
      const res = await request.del(`/api/notification/${id}`)
      if (res.code !== 200) throw new Error(res.message || '删除失败')
      await this.loadNotifications()
      await this.refreshUnread()
    } catch (err) {
      wx.showToast({ title: err.message || '删除失败', icon: 'none' })
    } finally {
      if (this._visible) this.setData({ actionBusy: false })
      else this.data.actionBusy = false
    }
  }
})
