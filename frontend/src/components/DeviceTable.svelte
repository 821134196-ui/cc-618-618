<script>
  import { onMount } from 'svelte'
  import { api } from '../api.js'
  import { loadDevices } from '../stores.js'
  import { statusLabel, fmtDate, fmtDateTime } from '../status.js'

  let rows = []
  let error = ''
  let message = ''
  let busy = false

  let form = { code: '', name: '', address: '', latitude: '', longitude: '', maintenancePhone: '' }

  onMount(load)

  async function load() {
    try {
      rows = await api.get('/admin/aeds')
    } catch (e) {
      error = e.message
    }
  }

  async function submit() {
    busy = true
    error = ''
    message = ''
    try {
      await api.post('/admin/aeds', {
        code: form.code.trim(),
        name: form.name.trim(),
        address: form.address.trim(),
        latitude: Number(form.latitude),
        longitude: Number(form.longitude),
        maintenancePhone: form.maintenancePhone.trim()
      })
      message = `设备 ${form.code.trim()} 已创建（新设备需完成首次巡检才会显示可用）`
      form = { code: '', name: '', address: '', latitude: '', longitude: '', maintenancePhone: '' }
      await Promise.all([load(), loadDevices()])
    } catch (e) {
      error = e.message
    } finally {
      busy = false
    }
  }
</script>

<div class="col">
  <div class="card">
    <h2>设备台账（维护端）</h2>
    {#if error}<div class="error-box">{error}</div>{/if}
    {#if message}<div class="success-box">{message}</div>{/if}
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>编号</th><th>名称</th><th>地址</th><th>内部维修电话</th>
            <th>状态</th><th>原因</th><th>电池有效期</th><th>电极片有效期</th><th>最近巡检</th>
          </tr>
        </thead>
        <tbody>
          {#each rows as d (d.id)}
            <tr>
              <td>{d.code}</td>
              <td>{d.name}</td>
              <td>{d.address}</td>
              <td class="phone">{d.maintenancePhone || '—'}</td>
              <td><span class="badge {d.status}">{statusLabel(d.status)}</span></td>
              <td class="muted">{d.statusReason}</td>
              <td>{fmtDate(d.batteryExpiry)}</td>
              <td>{fmtDate(d.padsExpiry)}</td>
              <td>{fmtDateTime(d.lastInspectedAt)}</td>
            </tr>
          {/each}
        </tbody>
      </table>
    </div>
  </div>

  <div class="card">
    <h2>新增设备</h2>
    <div class="form-grid">
      <div><label>设备编号 *</label><input bind:value={form.code} placeholder="如 AED-0009" /></div>
      <div><label>名称 *</label><input bind:value={form.name} placeholder="安装点位名称" /></div>
      <div><label>地址</label><input bind:value={form.address} placeholder="详细地址" /></div>
      <div><label>纬度 *</label><input bind:value={form.latitude} placeholder="如 31.2304" /></div>
      <div><label>经度 *</label><input bind:value={form.longitude} placeholder="如 121.4737" /></div>
      <div><label>内部维修电话</label><input bind:value={form.maintenancePhone} placeholder="仅维护端可见" /></div>
    </div>
    <div class="form-actions">
      <button on:click={submit} disabled={busy}>{busy ? '提交中…' : '创建设备'}</button>
    </div>
  </div>
</div>

<style>
  .col { display: flex; flex-direction: column; gap: 14px; }
  .table-wrap { overflow-x: auto; }
  .phone { white-space: nowrap; font-family: ui-monospace, monospace; font-size: 13px; }
</style>
