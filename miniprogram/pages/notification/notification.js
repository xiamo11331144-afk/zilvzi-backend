const app = getApp()
const request = require('../../utils/request')

Page({
  data: {
    notifications: [],
    unreadCount: 0,
    page: 1,
    size: 20,
    hasMore: true
  },

  onLoad() {
    this.loadNotifications()
  },

  onShow() {
    this.loadNotifications()
  },

  // 通知类型对应的动作文本
  getActionText(type) {
    const map = {
      'partner_apply': '想成为你的搭子',
      'partner_accept': '同意了你的搭子申请',
      'checkin_remind': '提醒你记得打卡哦~',
      'partner_remove': '已解除与你的搭子关系',
      'private_message': '给你发来一条消息',
      'comment': '评论了你的帖子',
      'streak_break': '断卡了，快去打卡吧'  // 默认文本，会被 loadNotifications 中按 title 覆盖
    }
    return map[type] || '发送了一条通知'
  },

  // 加载通知列表
  async loadNotifications() {
    try {
      const res = await request.get('/api/notification/list', {
        page: this.data.page,
        size: this.data.size
      })
      if (res.code === 200 && res.data) {
        let list = res.data.list || []
        // 为每条通知生成正确的显示文本（用fromNickname替代旧content中的用户名）
        list = list.map(n => {
          let displayText
          if (n.type === 'comment') {
            displayText = n.content || this.getActionText(n.type)
          } else if (n.type === 'streak_break') {
            // 搭子断卡用后端实际内容，自己断卡用默认文案
            displayText = (n.title === '搭子断卡' && n.content)
              ? n.content
              : this.getActionText(n.type)
          } else {
            displayText = this.getActionText(n.type)
          }
          return { ...n, displayText }
        })
        this.setData({
          notifications: list,
          unreadCount: res.data.unreadCount || 0
        })
      }
    } catch (err) {
      console.log('加载通知失败', err)
    }
  },

  // 标记单条已读
  async readNotification(e) {
    const id = e.currentTarget.dataset.id
    const index = e.currentTarget.dataset.index
    const notifications = this.data.notifications

    if (notifications[index].isRead === 0) {
      try {
        await request.put(`/api/notification/read/${id}`)
        notifications[index].isRead = 1
        const newCount = Math.max(0, this.data.unreadCount - 1)
        this.setData({ notifications, unreadCount: newCount })
        this.updateTabBadge()
      } catch (err) {
        console.log('标记已读失败', err)
      }
    }
  },

  // 全部标记已读
  async markAllAsRead() {
    try {
      await request.put('/api/notification/read/all')
      const notifications = this.data.notifications.map(n => ({ ...n, isRead: 1 }))
      this.setData({ notifications, unreadCount: 0 })
      this.updateTabBadge()
      wx.showToast({ title: '已全部标记', icon: 'success' })
    } catch (err) {
      wx.showToast({ title: '操作失败', icon: 'none' })
    }
  },

  // 清除全部通知
  async clearAll() {
    wx.showModal({
      title: '清除通知',
      content: '确定要清除所有通知吗？',
      success: async (res) => {
        if (res.confirm) {
          try {
            await request.del('/api/notification/clear')
            this.setData({ notifications: [], unreadCount: 0 })
            this.updateTabBadge()
            wx.showToast({ title: '已清除', icon: 'success' })
          } catch (err) {
            wx.showToast({ title: '操作失败', icon: 'none' })
          }
        }
      }
    })
  },

  // 更新TabBar角标
  async updateTabBadge() {
    try {
      const res = await request.get('/api/notification/unread/count')
      if (res.code === 200) {
        if (res.data > 0) {
          wx.setTabBarBadge({ index: 0, text: String(res.data > 99 ? '99+' : res.data) })
        } else {
          wx.removeTabBarBadge({ index: 0 })
        }
      }
      // 更新圈子角标
      const circleRes = await request.get('/api/notification/unread/count/comment')
      if (circleRes.code === 200) {
        if (circleRes.data > 0) {
          wx.setTabBarBadge({ index: 2, text: String(circleRes.data) })
        } else {
          wx.removeTabBarBadge({ index: 2 })
        }
      }
      // 更新我的角标
      const msgRes = await request.get('/api/notification/unread/count/private_message')
      if (msgRes.code === 200) {
        if (msgRes.data > 0) {
          wx.setTabBarBadge({ index: 3, text: String(msgRes.data) })
        } else {
          wx.removeTabBarBadge({ index: 3 })
        }
      }
    } catch (err) {}
  },

  // 删除单条通知
  async deleteOne(e) {
    const id = e.currentTarget.dataset.id
    const index = e.currentTarget.dataset.index
    const notifications = this.data.notifications
    const wasUnread = notifications[index].isRead === 0

    try {
      await request.del(`/api/notification/${id}`)
      notifications.splice(index, 1)
      const newCount = wasUnread ? Math.max(0, this.data.unreadCount - 1) : this.data.unreadCount
      this.setData({ notifications, unreadCount: newCount })
      this.updateTabBadge()
    } catch (err) {
      wx.showToast({ title: '删除失败', icon: 'none' })
    }
  }
})