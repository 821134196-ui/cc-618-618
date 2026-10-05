import { writable } from 'svelte/store'
import { api } from './api.js'

/**
 * 公众页唯一数据源：地图与文字列表都从 devices 渲染，
 * 保证两处状态始终一致（不会出现地图可用、列表停用之类的分歧）。
 */
export const devices = writable([])
export const devicesLoading = writable(false)
export const devicesError = writable('')
export const selectedId = writable(null)
/** 本地模拟定位结果 { lat, lng } */
export const userLocation = writable(null)

export async function loadDevices() {
  devicesLoading.set(true)
  devicesError.set('')
  try {
    const list = await api.get('/public/aeds')
    devices.set(list)
  } catch (e) {
    devicesError.set(e.message || '加载失败')
  } finally {
    devicesLoading.set(false)
  }
}
