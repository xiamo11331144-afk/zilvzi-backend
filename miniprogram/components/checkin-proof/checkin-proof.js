const request = require('../../utils/request')

Component({
  data: { visible: false, taskId: null, taskName: '', image: '', content: '', uploading: false, submitting: false },
  methods: {
    open(task) {
      if (this.data.submitting || this.data.uploading) return
      this.setData({ visible: true, taskId: task.id, taskName: task.name || task.taskName || '任务打卡', image: '', content: '' })
    },
    close() { if (!this.data.submitting && !this.data.uploading) this.setData({ visible: false }) },
    stopTap() {},
    inputContent(e) { this.setData({ content: e.detail.value }) },
    preview() { if (this.data.image) wx.previewImage({ urls: [this.data.image] }) },
    chooseImage() {
      if (this.data.uploading || this.data.submitting) return
      wx.chooseMedia({ count: 1, mediaType: ['image'], success: res => {
        const file = res.tempFiles && res.tempFiles[0]
        if (!file) return
        this.setData({ uploading: true })
        wx.uploadFile({
          url: request.UPLOAD_URL, filePath: file.tempFilePath, name: 'file',
          header: { Authorization: `Bearer ${getApp().globalData.token || ''}` },
          success: response => {
            try {
              const result = JSON.parse(response.data)
              if (response.statusCode !== 200 || result.code !== 200 || !result.data || !result.data.url) {
                throw new Error('照片上传失败，请重试')
              }
              this.setData({ image: result.data.url })
            } catch (err) { wx.showToast({ title: err.message || '照片上传失败', icon: 'none' }) }
          },
          fail: () => wx.showToast({ title: '照片上传失败，请重试', icon: 'none' }),
          complete: () => this.setData({ uploading: false })
        })
      } })
    },
    async submit() {
      if (this.data.submitting || this.data.uploading) return
      if (!this.data.image) { wx.showToast({ title: '请先上传凭证照片', icon: 'none' }); return }
      this.setData({ submitting: true })
      try {
        const result = await request.post('/api/checkin/do', {
          taskId: this.data.taskId, proofImage: this.data.image, proofContent: this.data.content.trim()
        })
        if (result.code !== 200) throw new Error(result.message || '提交失败')
        const status = result.data && result.data.status
        if (status !== 'pending' && status !== 'approved') throw new Error('打卡状态未确认，请刷新后查看')
        this.setData({ visible: false })
        wx.showToast({ title: status === 'pending' ? '已提交，等待搭子审核' : '打卡已通过', icon: status === 'pending' ? 'none' : 'success' })
        this.triggerEvent('submitted', { taskId: this.data.taskId, status })
      } catch (err) { wx.showToast({ title: err.message || '提交失败，请重试', icon: 'none' }) }
      finally { this.setData({ submitting: false }) }
    }
  }
})
