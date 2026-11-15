const request = require('./request')

function normalizeType(type) {
  const key = String(type || '').trim().toLowerCase()
  const groups = {
    考试: ['exam', '考试', '考试备考'], 学习: ['study', '学习', '学习提升'],
    运动: ['fitness', '运动', '健身', '健身运动', '锻炼', '体育'],
    阅读: ['reading', '阅读', '阅读计划'], 技能: ['skill', '技能', '技能学习'],
    习惯: ['habit', '习惯', '习惯养成']
  }
  return Object.keys(groups).find(name => groups[name].includes(key)) || key
}

async function loadPartners() {
  const res = await request.get('/api/partner/list')
  if (res.code !== 200 || !Array.isArray(res.data)) throw new Error(res.message || '搭子信息加载失败，请重试')
  return res.data
}

function needsReview(partners, type) {
  const category = normalizeType(type)
  return !!category && partners.some(p => p.status === 'accepted' && Array.isArray(p.goalTypes) &&
    p.goalTypes.some(t => normalizeType(t) === category))
}

async function forTask(taskId) {
  const res = await request.get('/api/checkin/review-required', { taskId })
  if (res.code !== 200 || !res.data || typeof res.data.required !== 'boolean') {
    throw new Error(res.message || '审核条件查询失败，请重试')
  }
  return res.data.required
}

module.exports = { loadPartners, needsReview, forTask }
