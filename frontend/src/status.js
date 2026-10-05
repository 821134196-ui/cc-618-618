export const STATUS_META = {
  AVAILABLE: { label: '可用', color: '#16a34a' },
  PENDING_REPLENISHMENT: { label: '待回补', color: '#d97706' },
  OUT_OF_SERVICE: { label: '停用', color: '#dc2626' }
}

export function statusLabel(status) {
  return (STATUS_META[status] || {}).label || status
}

export function statusColor(status) {
  return (STATUS_META[status] || {}).color || '#6b7280'
}

export function fmtDate(iso) {
  return iso ? String(iso).slice(0, 10) : '—'
}

export function fmtDateTime(iso) {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return String(iso)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 哈弗辛距离（米），用于本地模拟定位后的距离展示 */
export function haversineMeters(a, b) {
  const R = 6371000
  const toRad = (d) => (d * Math.PI) / 180
  const dLat = toRad(b.lat - a.lat)
  const dLng = toRad(b.lng - a.lng)
  const h = Math.sin(dLat / 2) ** 2
    + Math.cos(toRad(a.lat)) * Math.cos(toRad(b.lat)) * Math.sin(dLng / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(h))
}

export function fmtDistance(meters) {
  if (meters == null) return ''
  return meters < 1000 ? `${Math.round(meters)} 米` : `${(meters / 1000).toFixed(1)} 公里`
}
