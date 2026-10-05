import { writable, derived } from 'svelte/store'

// ---- 状态元数据：前后端统一的展示映射 ----
export const STATUS_META = {
  AVAILABLE: { label: '可用', color: '#16a34a' },
  PENDING_RESTOCK: { label: '待回补', color: '#d97706' },
  OUT_OF_SERVICE: { label: '停用', color: '#dc2626' }
}

export const RETRIEVAL_STATE = {
  OPEN: '待回补',
  RESTOCKED: '待复检',
  CLOSED: '已闭环'
}

// ---- 公众端数据：地图与文字列表共用这一个 store，保证两处状态一致 ----
export const publicDevices = writable([])
export const searchText = writable('')
export const statusFilter = writable('ALL')

export async function refreshPublic() {
  const res = await fetch('/api/public/devices')
  publicDevices.set(await res.json())
}

// 搜索 + 状态过滤后的结果：地图和列表都渲染它
export const filteredDevices = derived(
  [publicDevices, searchText, statusFilter],
  ([$devices, $q, $status]) => {
    const q = $q.trim().toLowerCase()
    return $devices.filter(d =>
      ($status === 'ALL' || d.status === $status) &&
      (!q || d.code.toLowerCase().includes(q) ||
        d.name.toLowerCase().includes(q) ||
        d.address.toLowerCase().includes(q))
    )
  }
)

// ---- 维护端数据 ----
export const adminDevices = writable([])
export const retrievals = writable([])
export const alarms = writable([])

export async function refreshAdmin() {
  const [d, r, a] = await Promise.all([
    fetch('/api/admin/devices').then(x => x.json()),
    fetch('/api/admin/retrievals').then(x => x.json()),
    fetch('/api/admin/alarms').then(x => x.json())
  ])
  adminDevices.set(d)
  retrievals.set(r)
  alarms.set(a)
}

// ---- 通用 POST ----
export async function post(url, body) {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body ?? {})
  })
  const data = await res.json().catch(() => ({}))
  if (!res.ok) throw new Error(data.message || `请求失败（${res.status}）`)
  return data
}

export function fmtTime(iso) {
  return iso ? iso.slice(0, 16).replace('T', ' ') : '—'
}
