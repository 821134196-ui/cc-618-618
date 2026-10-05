<script>
  import { onMount } from 'svelte'
  import { api } from '../api.js'
  import { loadDevices } from '../stores.js'
  import { fmtDate, fmtDateTime } from '../status.js'

  let events = []
  let error = ''
  let message = ''
  let busyId = null

  // 每个事件一张回补表单 / 复检表单
  let replenishForms = {}
  let recheckForms = {}

  onMount(load)

  async function load() {
    error = ''
    try {
      events = await api.get('/admin/usage-events')
      initForms()
    } catch (e) {
      error = e.message
    }
  }

  function initForms() {
    const rp = {}
    const rc = {}
    for (const e of events) {
      rp[e.id] = { staffName: '', batteryBatch: '', batteryExpiry: '', padsBatch: '', padsExpiry: '' }
      rc[e.id] = {
        inspectorName: '',
        batteryOk: true,
        padsOk: true,
        cabinetOk: true,
        // 复检批次默认带出回补登记批次，供维护人员现场核对
        batteryBatch: e.replenishment ? e.replenishment.batteryBatch : '',
        batteryExpiry: e.replenishment ? e.replenishment.batteryExpiry : '',
        padsBatch: e.replenishment ? e.replenishment.padsBatch : '',
        padsExpiry: e.replenishment ? e.replenishment.padsExpiry : '',
        notes: ''
      }
    }
    replenishForms = rp
    recheckForms = rc
  }

  $: open = events.filter((e) => e.status === 'OPEN')
  $: replenished = events.filter((e) => e.status === 'REPLENISHED')
  $: closed = events.filter((e) => e.status === 'CLOSED')

  async function afterChange(text) {
    message = text
    await Promise.all([load(), loadDevices()])
  }

  async function submitReplenish(e) {
    busyId = e.id
    message = ''
    error = ''
    try {
      await api.post(`/admin/usage-events/${e.id}/replenish`, replenishForms[e.id])
      await afterChange(`已登记回补（${e.deviceCode}），等待复检`)
    } catch (err) {
      error = err.message
    } finally {
      busyId = null
    }
  }

  async function submitRecheck(e) {
    busyId = e.id
    message = ''
    error = ''
    try {
      const result = await api.post(`/admin/usage-events/${e.id}/recheck`, recheckForms[e.id])
      if (result.status === 'CLOSED') {
        await afterChange(`复检通过，${e.deviceCode} 已恢复可用`)
      } else {
        await afterChange(`复检未通过，${e.deviceCode} 仍保持“待回补”，请整改后重新复检`)
      }
    } catch (err) {
      error = err.message
    } finally {
      busyId = null
    }
  }
</script>

<div class="panel">
  {#if error}<div class="error-box">{error}</div>{/if}
  {#if message}<div class="success-box">{message}</div>{/if}

  <section class="card">
    <h2>待回补（{open.length}）</h2>
    {#if open.length === 0}
      <p class="muted">暂无待回补的取用事件。</p>
    {/if}
    {#each open as e (e.id)}
      <div class="event">
        <div class="event-head">
          <strong>{e.deviceName}</strong>
          <span class="muted">{e.deviceCode} · {fmtDateTime(e.reportedAt)} · 报案人：{e.reporterName}</span>
        </div>
        {#if e.note}<div class="muted note">备注:{e.note}</div>{/if}
        <div class="form-grid">
          <div><label>回补经办人 *</label><input bind:value={replenishForms[e.id].staffName} placeholder="姓名" /></div>
          <div><label>实际电池批次 *</label><input bind:value={replenishForms[e.id].batteryBatch} placeholder="如 BAT-2026-10A" /></div>
          <div><label>电池有效期 *</label><input type="date" bind:value={replenishForms[e.id].batteryExpiry} /></div>
          <div><label>实际电极片批次 *</label><input bind:value={replenishForms[e.id].padsBatch} placeholder="如 PAD-2026-10A" /></div>
          <div><label>电极片有效期 *</label><input type="date" bind:value={replenishForms[e.id].padsExpiry} /></div>
        </div>
        <div class="form-actions">
          <button class="small" disabled={busyId === e.id} on:click={() => submitReplenish(e)}>
            登记回补（录入实际批次）
          </button>
        </div>
      </div>
    {/each}
  </section>

  <section class="card">
    <h2>待复检（{replenished.length}）</h2>
    {#if replenished.length === 0}
      <p class="muted">暂无待复检的取用事件。</p>
    {/if}
    {#each replenished as e (e.id)}
      <div class="event">
        <div class="event-head">
          <strong>{e.deviceName}</strong>
          <span class="muted">{e.deviceCode} · 取用 {fmtDateTime(e.reportedAt)}</span>
        </div>
        {#if e.replenishment}
          <div class="ref">
            回补登记（{fmtDateTime(e.replenishment.replenishedAt)} · {e.replenishment.staffName}）：
            电池批次 <code>{e.replenishment.batteryBatch}</code>（有效期至 {fmtDate(e.replenishment.batteryExpiry)}），
            电极片批次 <code>{e.replenishment.padsBatch}</code>（有效期至 {fmtDate(e.replenishment.padsExpiry)}）
            —— 请核对设备内实际耗材批次后复检
          </div>
        {/if}
        <div class="checks">
          <label><input type="checkbox" bind:checked={recheckForms[e.id].batteryOk} /> 电池正常</label>
          <label><input type="checkbox" bind:checked={recheckForms[e.id].padsOk} /> 电极片正常</label>
          <label><input type="checkbox" bind:checked={recheckForms[e.id].cabinetOk} /> 箱体正常</label>
        </div>
        <div class="form-grid">
          <div><label>复检人 *</label><input bind:value={recheckForms[e.id].inspectorName} placeholder="姓名" /></div>
          <div><label>设备内电池实际批次 *</label><input bind:value={recheckForms[e.id].batteryBatch} /></div>
          <div><label>电池有效期 *</label><input type="date" bind:value={recheckForms[e.id].batteryExpiry} /></div>
          <div><label>设备内电极片实际批次 *</label><input bind:value={recheckForms[e.id].padsBatch} /></div>
          <div><label>电极片有效期 *</label><input type="date" bind:value={recheckForms[e.id].padsExpiry} /></div>
          <div><label>备注</label><input bind:value={recheckForms[e.id].notes} placeholder="选填" /></div>
        </div>
        <div class="form-actions">
          <button class="small" disabled={busyId === e.id} on:click={() => submitRecheck(e)}>
            提交复检（批次须与回补登记一致）
          </button>
        </div>
      </div>
    {/each}
  </section>

  <section class="card">
    <h2>已闭环（{closed.length}）</h2>
    {#if closed.length === 0}
      <p class="muted">暂无历史记录。</p>
    {:else}
      <table>
        <thead>
          <tr><th>设备</th><th>取用时间</th><th>闭环时间</th><th>回补批次</th><th>经办</th></tr>
        </thead>
        <tbody>
          {#each closed as e (e.id)}
            <tr>
              <td>{e.deviceName}<br /><span class="muted">{e.deviceCode}</span></td>
              <td>{fmtDateTime(e.reportedAt)}</td>
              <td>{fmtDateTime(e.closedAt)}</td>
              <td>
                {#if e.replenishment}
                  电池 {e.replenishment.batteryBatch} / 电极片 {e.replenishment.padsBatch}
                {:else}—{/if}
              </td>
              <td>{e.replenishment ? e.replenishment.staffName : '—'}</td>
            </tr>
          {/each}
        </tbody>
      </table>
    {/if}
  </section>
</div>

<style>
  .panel { display: flex; flex-direction: column; gap: 14px; }
  .event { border-top: 1px solid var(--line); padding: 14px 0 4px; }
  .event:first-of-type { border-top: none; }
  .event-head { display: flex; gap: 10px; align-items: baseline; flex-wrap: wrap; margin-bottom: 8px; }
  .note { font-size: 13px; margin-bottom: 8px; }
  .ref {
    background: #eff6ff;
    border: 1px solid #bfdbfe;
    color: #1e40af;
    border-radius: 8px;
    padding: 8px 12px;
    font-size: 13px;
    margin-bottom: 10px;
  }
  .ref code { background: #dbeafe; padding: 1px 6px; border-radius: 4px; }
  .checks { margin-bottom: 10px; }
</style>
