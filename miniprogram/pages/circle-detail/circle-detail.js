const app = getApp()
const request = require('../../utils/request')

Page({
  data: {
    circleId: null,
    circle: {},
    isMember: false,
    isCreator: false,
    posts: [],
    showPublish: false,
    publishContent: '',
    publishImages: [],
    showCommentModal: false,
    currentPostId: null,
    currentPostIndex: -1,
    comments: [],
    commentContent: ''
  },

  onLoad(options) {
    if (options.id) {
      this.setData({ circleId: options.id })
      this.loadCircleDetail(options.id)
      this.loadPosts(options.id)
      this.checkMembership(options.id)
    }
  },

  onShow() {
    if (this.data.circleId) {
      this.loadPosts(this.data.circleId)
    }
  },

  // 检查是否是圈子成员/创建者
  async checkMembership(circleId) {
    if (!app.globalData.isLoggedIn) return
    try {
      const res = await request.get(`/api/circle/${circleId}/members`)
      if (res.code === 200 && res.data) {
        const currentUserId = app.globalData.userInfo.id
        const member = res.data.find(m => m.userId === currentUserId)
        const isMember = !!member
        const isCreator = isMember && member.role === 1
        this.setData({ isMember, isCreator })
      }
    } catch (err) {
      console.log('检查成员状态失败', err)
    }
  },

  // 加载圈子详情
  async loadCircleDetail(circleId) {
    try {
      const res = await request.get(`/api/circle/detail/${circleId}`)
      if (res.code === 200 && res.data) {
        const circle = res.data
        try {
          circle.tags = circle.tags ? JSON.parse(circle.tags) : []
        } catch (e) {
          circle.tags = []
        }
        this.setData({ circle })
      }
    } catch (err) {
      console.log('加载圈子详情失败', err)
    }
  },

  // 加载动态列表
  async loadPosts(circleId) {
    try {
      const res = await request.get(`/api/circle/${circleId}/posts`, { page: 1, size: 20 })
      if (res.code === 200 && res.data) {
        const posts = res.data.map(p => {
          let images = []
          try {
            images = p.images ? JSON.parse(p.images) : []
          } catch (e) {
            images = []
          }
          return {
            id: p.id,
            userId: p.userId,
            nickname: p.nickname || '匿名用户',
            avatar: p.avatar || '',
            content: p.content,
            images: images,
            likeCount: p.likeCount || 0,
            commentCount: p.commentCount || 0,
            isLiked: p.isLiked || false,
            createTime: p.createTime || ''
          }
        })
        this.setData({ posts })
      }
    } catch (err) {
      console.log('加载动态失败', err)
    }
  },

  // 加入圈子
  async joinCircle() {
    if (!app.globalData.isLoggedIn) {
      wx.showToast({ title: '请先登录', icon: 'none' })
      return
    }

    const { circleId } = this.data
    try {
      await request.post(`/api/circle/join/${circleId}`)
      wx.showToast({ title: '加入成功', icon: 'success' })
      this.setData({ isMember: true })
      this.loadCircleDetail(circleId)
    } catch (err) {
      wx.showToast({ title: err.message || '操作失败', icon: 'none' })
    }
  },

  // 退出圈子（带确认）
  leaveCircle() {
    const { circleId, isCreator } = this.data
    if (isCreator) {
      wx.showToast({ title: '创建者不能退出圈子', icon: 'none' })
      return
    }
    wx.showModal({
      title: '退出圈子',
      content: '确定要退出该圈子吗？',
      success: async (res) => {
        if (res.confirm) {
          try {
            await request.post(`/api/circle/leave/${circleId}`)
            wx.showToast({ title: '已退出', icon: 'success' })
            this.setData({ isMember: false })
            this.loadCircleDetail(circleId)
          } catch (err) {
            wx.showToast({ title: err.message || '操作失败', icon: 'none' })
          }
        }
      }
    })
  },

  // 显示发布弹窗
  showPublishModal() {
    if (!this.data.isMember) {
      wx.showToast({ title: '请先加入圈子', icon: 'none' })
      return
    }
    this.setData({ showPublish: true })
  },

  hidePublishModal() {
    this.setData({ showPublish: false })
  },

  onPublishInput(e) {
    this.setData({ publishContent: e.detail.value })
  },

  // 选择图片
  chooseImage() {
    wx.chooseMedia({
      count: 9 - this.data.publishImages.length,
      mediaType: ['image'],
      success: (res) => {
        const newImages = res.tempFiles.map(f => f.tempFilePath)
        this.setData({
          publishImages: [...this.data.publishImages, ...newImages]
        })
      }
    })
  },

  removeImage(e) {
    const index = e.currentTarget.dataset.index
    const publishImages = this.data.publishImages.filter((_, i) => i !== index)
    this.setData({ publishImages })
  },

  // 发布动态
  async publishPost() {
    const { circleId, publishContent, publishImages } = this.data
    if (!publishContent || !publishContent.trim()) {
      wx.showToast({ title: '请输入内容', icon: 'none' })
      return
    }

    wx.showLoading({ title: '发布中...' })
    try {
      // 上传图片（如果有）
      let imageUrls = []
      for (const img of publishImages) {
        const uploadRes = await new Promise((resolve, reject) => {
          wx.uploadFile({
            url: request.UPLOAD_URL,
            filePath: img,
            name: 'file',
            header: { 'Authorization': 'Bearer ' + app.globalData.token },
            success: (res) => resolve(JSON.parse(res.data)),
            fail: reject
          })
        })
        if (uploadRes.code === 200) {
          imageUrls.push(uploadRes.data.url)
        }
      }

      const res = await request.post(`/api/circle/${circleId}/post`, {
        content: publishContent,
        images: JSON.stringify(imageUrls)
      })

      wx.hideLoading()
      if (res.code === 200) {
        wx.showToast({ title: '发布成功', icon: 'success' })
        this.setData({ showPublish: false, publishContent: '', publishImages: [] })
        this.loadPosts(circleId)
        this.loadCircleDetail(circleId)
      } else {
        wx.showToast({ title: res.message || '发布失败', icon: 'none', duration: 3000 })
      }
    } catch (err) {
      wx.hideLoading()
      wx.showToast({ title: err.message || '发布失败', icon: 'none', duration: 3000 })
    }
  },

  // 点赞
  async toggleLike(e) {
    if (!app.globalData.isLoggedIn) {
      wx.showToast({ title: '请先登录', icon: 'none' })
      return
    }

    const postId = e.currentTarget.dataset.id
    const index = e.currentTarget.dataset.index
    const posts = this.data.posts
    
    try {
      await request.post(`/api/circle/post/${postId}/like`)
      posts[index].isLiked = !posts[index].isLiked
      posts[index].likeCount = posts[index].isLiked 
        ? (posts[index].likeCount || 0) + 1 
        : Math.max((posts[index].likeCount || 0) - 1, 0)
      this.setData({ posts })
    } catch (err) {
      wx.showToast({ title: '操作失败', icon: 'none' })
    }
  },

  // 预览图片
  previewImage(e) {
    const { url, urls } = e.currentTarget.dataset
    wx.previewImage({ current: url, urls })
  },

  // 显示评论
  async showComments(e) {
    const postId = e.currentTarget.dataset.id
    const index = e.currentTarget.dataset.index
    this.setData({ currentPostId: postId, currentPostIndex: index, showCommentModal: true })
    
    try {
      const res = await request.get(`/api/circle/post/${postId}/comments`)
      if (res.code === 200 && res.data) {
        this.setData({ comments: res.data })
      }
    } catch (err) {
      console.log('加载评论失败', err)
    }
  },

  hideCommentModal() {
    this.setData({ showCommentModal: false, comments: [], commentContent: '' })
  },

  onCommentInput(e) {
    this.setData({ commentContent: e.detail.value })
  },

  // 发送评论
  async sendComment() {
    const { currentPostId, currentPostIndex, commentContent } = this.data
    if (!commentContent.trim()) return

    try {
      const res = await request.post(`/api/circle/post/${currentPostId}/comment`, {
        content: commentContent
      })
      if (res.code === 200) {
        wx.showToast({ title: '评论成功', icon: 'success' })
        
        const comments = this.data.comments
        comments.push({
          ...res.data,
          nickname: app.globalData.userInfo.nickname,
          avatar: app.globalData.userInfo.avatar
        })
        
        const posts = this.data.posts
        posts[currentPostIndex].commentCount = (posts[currentPostIndex].commentCount || 0) + 1
        
        this.setData({ comments, posts, commentContent: '' })
      } else {
        wx.showToast({ title: res.message || '评论失败', icon: 'none', duration: 3000 })
      }
    } catch (err) {
      wx.showToast({ title: err.message || '评论失败', icon: 'none', duration: 3000 })
    }
  }
})