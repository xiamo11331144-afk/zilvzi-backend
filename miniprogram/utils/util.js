/**
 * 日期工具类
 */
const formatDate = (date, format = 'YYYY-MM-DD') => {
  const d = new Date(date)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hour = String(d.getHours()).padStart(2, '0')
  const minute = String(d.getMinutes()).padStart(2, '0')
  const second = String(d.getSeconds()).padStart(2, '0')

  return format
    .replace('YYYY', year)
    .replace('MM', month)
    .replace('DD', day)
    .replace('HH', hour)
    .replace('mm', minute)
    .replace('ss', second)
}

const getWeekDay = (date) => {
  const days = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return days[new Date(date).getDay()]
}

const getDaysInMonth = (year, month) => {
  return new Date(year, month, 0).getDate()
}

const isToday = (date) => {
  const today = new Date()
  const d = new Date(date)
  return today.toDateString() === d.toDateString()
}

module.exports = {
  formatDate,
  getWeekDay,
  getDaysInMonth,
  isToday
}