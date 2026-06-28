const app = getApp()
const request = require('../../utils/request')

Page({
  data: {
    goals: [],
    showModal: false,
    editMode: false,
    goalForm: { id: '', name: '', type: '学习', description: '' },
    types: ['学习', '运动', '阅读', '早起', '饮食', '其他'],
    typeIcons: { '学习': '📚', '运动': '🏃', '阅读': '📖', '早起': '🌅', '饮食': '🍎', '其他': '✨' }
  },

  onLoad() { this.loadGoals() },
  onShow() { if (app.globalData.isLoggedIn) this.loadGoals() },

  async loadGoals() {
    try {
      const res = await request.get('/api/goal/list')
      if (res.code === 200 && res.data) {
        const icons = { '学习': '📚', '运动': '🏃', '阅读': '📖', '早起': '🌅', '饮食': '🍎', '其他': '✨' }
        const goals = res.data.map(g => ({ ...g, typeIcon: icons[g.type] || '✨' }))
        this.setData({ goals })
      }
    } catch (err) {}
  },

  showCreate() {
    this.setData({ showModal: true, editMode: false, goalForm: { id: '', name: '', type: '学习', description: '' } })
  },

  editGoal(e) {
    const goal = this.data.goals.find(g => g.id === e.currentTarget.dataset.id)
    if (goal) {
      this.setData({ showModal: true, editMode: true, goalForm: { ...goal } })
    }
  },

  hideModal() { this.setData({ showModal: false }) },

  onNameInput(e) { this.setData({ 'goalForm.name': e.detail.value }) },
  onDescInput(e) { this.setData({ 'goalForm.description': e.detail.value }) },
  selectType(e) { this.setData({ 'goalForm.type': e.currentTarget.dataset.type }) },

  async saveGoal() {
    const { id, name, type, description } = this.data.goalForm
    if (!name.trim()) { wx.showToast({ title: '请输入名称', icon: 'none' }); return }
    try {
      if (this.data.editMode) {
        await request.put('/api/goal/update', { id, name, type, description })
        wx.showToast({ title: '已更新', icon: 'success' })
      } else {
        await request.post('/api/goal/create', { name, type, description })
        wx.showToast({ title: '已创建', icon: 'success' })
      }
      this.hideModal()
      this.loadGoals()
    } catch (err) { wx.showToast({ title: '操作失败', icon: 'none' }) }
  },

  async completeGoal(e) {
    const id = e.currentTarget.dataset.id
    try {
      await request.put('/api/goal/complete/' + id)
      wx.showToast({ title: '已完成', icon: 'success' })
      this.loadGoals()
    } catch (err) { wx.showToast({ title: '操作失败', icon: 'none' }) }
  },

  async abandonGoal(e) {
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '放弃目标', content: '确定放弃？',
      success: async (res) => {
        if (res.confirm) {
          try {
            await request.put('/api/goal/abandon/' + id)
            wx.showToast({ title: '已放弃', icon: 'success' })
            this.loadGoals()
          } catch (err) { wx.showToast({ title: '操作失败', icon: 'none' }) }
        }
      }
    })
  },

  async deleteGoal(e) {
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '删除目标', content: '确定删除？',
      success: async (res) => {
        if (res.confirm) {
          try {
            await request.del('/api/goal/delete/' + id)
            wx.showToast({ title: '已删除', icon: 'success' })
            this.loadGoals()
          } catch (err) { wx.showToast({ title: '操作失败', icon: 'none' }) }
        }
      }
    })
  }
})
