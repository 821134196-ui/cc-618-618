<script>
  import { createEventDispatcher } from 'svelte'
  import { api } from '../api.js'
  import { statusLabel, fmtDate, fmtDateTime } from '../status.js'

  /** 当前选中设备（来自与地图、列表相同的数据源） */
  export let device

  const dispatch = createEventDispatcher()

  let reporterName = ''
  let busy = false
  let message = ''
  let error = ''

  // 切换设备时重置表单状态
  $: if (device) {
    message = ''
    error = ''
  }

  async function reportUse() {
    if (busy) return
    const ok = window.confirm(
      `确认对 ${device.name}（${device.code}）发起紧急取用？\n` +
      '取用后设备将立即转为“待回补”，须完成耗材回补并复检通过后才恢复可用。'
    )
    if (!ok) return
    busy = true
    message = ''
    error = ''
    try {
      await api.post(`/public/aeds/${device.id}/use`, {
        reporterName: reporterName.trim() || '热心市民',
        note: '公众端模拟报警取用'
      })
      message = '已登记紧急取用，设备转为“待回补”。维护人员将尽快回补耗材并复检。'
      reporterName = ''
      dispatch('changed')
    } catch (e) {
      error = e.message
      dispatch('changed')
    } finally {
      busy = false
    }
  }
</script>

<div class="card detail">
  <div class="head">
    <div>
      <h2>{device.name}</h2>
      <div class="muted">{device.code} · {device.address}</div>
    </div>
    <span class="badge {device.status}">{statusLabel(device.status)}</span>
  </div>

  {#if device.status !== 'AVAILABLE'}
    <div class="reason" class:warn={device.status === 'PENDING_REPLENISHMENT'}>
      {device.status === 'PENDING_REPLENISHMENT' ? '⏳' : '⚠️'} {device.statusReason}
    </div>
  {/if}

  <dl class="facts">
    <div><dt>电池有效期</dt><dd>{fmtDate(device.batteryExpiry)}</dd></div>
    <div><dt>电极片有效期</dt><dd>{fmtDate(device.padsExpiry)}</dd></div>
    <div><dt>最近巡检</dt><dd>{fmtDateTime(device.lastInspectedAt)}</dd></div>
    <div><dt>坐标</dt><dd>{device.latitude.toFixed(4)}, {device.longitude.toFixed(4)}</dd></div>
  </dl>

  {#if message}<div class="success-box">{message}</div>{/if}
  {#if error}<div class="error-box">{error}</div>{/if}

  <div class="use-box">
    <h3>紧急取用（本地模拟报警）</h3>
    {#if device.status === 'AVAILABLE'}
      <div class="use-row">
        <input
          type="text"
          placeholder="您的称呼（选填）"
          bind:value={reporterName}
          aria-label="报案人称呼"
        />
        <button on:click={reportUse} disabled={busy}>
          {busy ? '正在上报…' : '🚨 紧急取用'}
        </button>
      </div>
      <p class="muted tip">仅在真实紧急情况下使用。取用后设备立即转为“待回补”，须完成耗材回补并复检通过后才恢复可用。</p>
    {:else}
      <p class="muted tip">当前设备不可取用：{device.statusReason}。请选择地图上其他绿色“可用”设备。</p>
    {/if}
  </div>
</div>

<style>
  .detail { display: flex; flex-direction: column; gap: 12px; }
  .head { display: flex; justify-content: space-between; align-items: flex-start; gap: 10px; }
  .head h2 { margin: 0 0 4px; }
  .facts {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
    gap: 10px;
    margin: 0;
  }
  .facts div { background: #f9fafb; border-radius: 8px; padding: 8px 12px; }
  .facts dt { font-size: 12px; color: var(--muted); }
  .facts dd { margin: 2px 0 0; font-size: 14px; font-weight: 600; }
  .use-box { border-top: 1px dashed var(--line); padding-top: 12px; }
  .use-row { display: flex; gap: 10px; }
  .use-row input { flex: 1; }
  .use-row button { white-space: nowrap; }
  .tip { font-size: 12.5px; margin: 8px 0 0; }
</style>
