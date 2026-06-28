const app = getApp()
const request = require('../../utils/request')

Page({
  data: {
    hotTags: ['全部', '考研', '健身', '早起', '阅读', '学习', '减脂', '冥想'],
    selectedTag: '全部',
    circles: [],
    myCircles: [],
    searchKeyword: '',
    showCreateModal: false,
    newCircle: {
      name: '',
      desc: '',
      tags: []
    },
    selectedTags: {}, // 记录选中的标签
    allTags: ['考研', '健身', '早起', '阅读', '学习', '减脂', '冥想', '英语', '编程', '其他']
  },

  onLoad() {
    this.loadCircles()
    this.loadMyCircles()
  },

  onShow() {
    if (app.globalData.isLoggedIn) {
      this.loadMyCircles()
      this.loadUnreadCount()
      this.loadAllBadges()
    }
  },

  async loadUnreadCount() {
    try {
      const res = await request.get('/api/notification/unread/count/comment')
      if (res.code === 200 && res.data !== undefined) {
        if (res.data > 0) {
          wx.setTabBarBadge({ index: 2, text: String(res.data) })
        } else {
          wx.removeTabBarBadge({ index: 2 })
        }
      }
    } catch (err) {}
  },

  async markCircleRead() {
    try {
      await request.put('/api/notification/read/type/comment')
      wx.removeTabBarBadge({ index: 2 })
    } catch (err) {}
  },

  // 加载所有Tab角标
  async loadAllBadges() {
    try {
      const notiRes = await request.get('/api/notification/unread/count')
      if (notiRes.code === 200) {
        if (notiRes.data > 0) {
          wx.setTabBarBadge({ index: 0, text: String(notiRes.data > 99 ? '99+' : notiRes.data) })
        } else {
          wx.removeTabBarBadge({ index: 0 })
        }
      }
    } catch (err) {}
    try {
      const partnerRes = await request.get('/api/partner/requests')
      if (partnerRes.code === 200 && partnerRes.data) {
        const pendingCount = partnerRes.data.filter(r => r.status === 'pending').length
        if (pendingCount > 0) {
          wx.setTabBarBadge({ index: 1, text: String(pendingCount) })
        } else {
          wx.removeTabBarBadge({ index: 1 })
        }
      }
    } catch (err) {}
    try {
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

  async loadCircles() {
    try {
      const tag = this.data.selectedTag === '全部' ? '' : this.data.selectedTag
      const keyword = this.data.searchKeyword || ''
      const res = await request.get('/api/circle/list', { tag, keyword, page: 1, size: 10 })
      if (res.code === 200 && res.data) {
        const circles = res.data.map(c => ({
          id: c.id,
          name: c.name,
          description: c.description,
          cover: c.cover || '',
          memberCount: c.memberCount || 0,
          postCount: c.postCount || 0,
          tags: c.tags ? JSON.parse(c.tags) : []
        }))
        this.setData({ circles })
      }
    } catch (err) {
      console.log('加载圈子失败', err)
    }
  },

  async loadMyCircles() {
    if (!app.globalData.isLoggedIn) return
    try {
      const res = await request.get('/api/circle/my')
      if (res.code === 200 && res.data) {
        const myCircles = res.data.map(c => ({
          id: c.id,
          name: c.name,
          cover: c.cover || ''
        }))
        this.setData({ myCircles })
      }
    } catch (err) {
      console.log('加载我的圈子失败', err)
    }
  },

  selectTag(e) {
    this.setData({ selectedTag: e.currentTarget.dataset.tag })
    this.loadCircles()
  },

  onSearch(e) {
    this.setData({ searchKeyword: e.detail.value })
  },

  searchCircle() {
    this.setData({ searched: true })
    this.loadCircles()
  },

  enterCircle(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({
      url: `/pages/circle-detail/circle-detail?id=${id}`
    })
  },

  createCircle() {
    if (!app.globalData.isLoggedIn) {
      wx.showToast({ title: '请先登录', icon: 'none' })
      return
    }
    this.setData({ showCreateModal: true })
  },

  hideCreateModal() {
    this.setData({ showCreateModal: false })
  },

  onNameInput(e) {
    this.setData({ 'newCircle.name': e.detail.value })
  },

  onDescInput(e) {
    this.setData({ 'newCircle.desc': e.detail.value })
  },

  // 选择封面图片
  chooseCover() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      success: (res) => {
        this.setData({ 'newCircle.cover': res.tempFiles[0].tempFilePath })
      }
    })
  },

  toggleTag(e) {
    const tag = e.currentTarget.dataset.tag
    let tags = [...this.data.newCircle.tags]
    let selectedTags = { ...this.data.selectedTags }
    
    if (tags.includes(tag)) {
      tags = tags.filter(t => t !== tag)
      selectedTags[tag] = false
    } else {
      tags.push(tag)
      selectedTags[tag] = true
    }
    
    this.setData({ 
      'newCircle.tags': tags,
      selectedTags: selectedTags
    })
  },

  async createCircleConfirm() {
    const { name, desc, tags, cover } = this.data.newCircle
    if (!name.trim()) {
      wx.showToast({ title: '请输入圈子名称', icon: 'none' })
      return
    }

    wx.showLoading({ title: '创建中...' })

    try {
      let coverUrl = ''
      
      // 上传封面图片
      if (cover) {
        const uploadRes = await new Promise((resolve, reject) => {
          wx.uploadFile({
            url: request.UPLOAD_URL,
            filePath: cover,
            name: 'file',
            header: { 'Authorization': 'Bearer ' + app.globalData.token },
            success: (res) => resolve(JSON.parse(res.data)),
            fail: reject
          })
        })
        if (uploadRes.code === 200) {
          coverUrl = uploadRes.data.url
        }
      }

      const res = await request.post('/api/circle/create', {
        name,
        description: desc,
        tags: JSON.stringify(tags),
        cover: coverUrl
      })

      wx.hideLoading()
      
      if (res.code === 200) {
        wx.showToast({ title: '创建成功', icon: 'success' })
        this.setData({ 
          showCreateModal: false,
          newCircle: { name: '', desc: '', tags: [], cover: '' },
          selectedTags: {}
        })
        this.loadCircles()
        this.loadMyCircles()
      }
    } catch (err) {
      wx.hideLoading()
      wx.showToast({ title: err.message || '创建失败', icon: 'none' })
    }
  }
})