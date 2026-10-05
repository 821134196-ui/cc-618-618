<script>
  import { onMount } from 'svelte'
  import {
    adminDevices, retrievals, alarms, refreshAdmin,
    STATUS_META, RETRIEVAL_STATE, post, fmtTime
  } from '../stores.js'
  import InspectionModal from './InspectionModal.svelte'
  import DeviceHistoryModal from './DeviceHistoryModal.svelte'

  let inspectTarget = null
  let historyTarget = null
  let msg = {}
  let showHistory = false

  // 每个待处理事件的回补/复检表单状态
  let forms = {}

  onMount(refreshAdmin)

  $: openRetrievals = $retrievals.filter(r => r.state !== 'CLOSED')
  $: closedRetrievals = $retrievals.filter(r => r.state === 'CLOSED')
  $: counts = $adminDevices.reduce((acc, d) => {
    acc[d.status] = (acc[d.status] || 0) + 1
    return acc
  }, {})

  // 为每个待处理事件准备表单对象（响应式保证渲染前已存在）
  $: openRetrievals.forEach(r => {
    if (!forms[r.id]) {
      forms[r.id] = {
        operator: '', batteryBatch: '', batteryExpiry: '',
        padsBatch: '', padsExpiry: '',
        inspector: '', batteryOk: true, padsOk: true, cabinetOk: true, notes: ''
      }
    }
  })

  function formOf(id) {
    return forms[id] ?? {}
  }

  async function doRestock(r) {
    const f = formOf(r.id)
    msg = { ...msg, [r.id]: null }
    try {
      await post(`/api/admin/retrievals/${r.id}/restock`, {
        operator: f.operator, batteryBatch: f.batteryBatch, batteryExpiry: f.batteryExpiry,
        padsBatch: f.padsBatch, padsExpiry: f.padsExpiry
      })
      msg = { ...msg, [r.id]: { ok: true, text: '回补已登记，请进行复检' } }
      await refreshAdmin()
    } catch (e) {
      msg = { ...msg, [r.id]: { ok: false, text: e.message } }
    }
  }

  async function doRecheck(r) {
    const f = formOf(r.id)
    msg = { ...msg, [r.id]: null }
    try {
      await post(`/api/admin/retrievals/${r.id}/recheck`, {
        inspectorName: f.inspector, batteryOk: f.batteryOk, padsOk: f.padsOk,
        cabinetOk: f.cabinetOk, notes: f.notes
      })
      await refreshAdmin()
    } catch (e) {
      msg = { ...msg, [r.id]: { ok: false, text: e.message } }
    }
  }

  async function onModalDone() {
    inspectTarget = null
    historyTarget = null
    await refreshAdmin()
  }
</script>

<div class="stats">
  {#each ['AVAILABLE', 'PENDING_RESTOCK', 'OUT_OF_SERVICE'] as s}
    <div class="stat card">
      <div class="num" style="color:{STATUS_META[s].color}">{counts[s] || 0}</div>
      <div class="muted">{STATUS_META[s].label}</div>
    </div>
  {/each}
  <div class="stat card">
    <div class="num" style="color:#b91c1c">{openRetrievals.length}</div>
    <div class="muted">待处理取用事件</div>
  </div>
</div>

<!-- 待处理取用事件：回补 → 复检 -->
<div class="card">
  <h3>取用事件处理 <span class="muted">（耗材补齐且复检通过后，设备才恢复可用）</span></h3>
  {#if openRetrievals.length === 0}
    <p class="muted">当前没有待处理的取用事件。</p>
  {/if}
  {#each openRetrievals as r (r.id)}
    <div class="event">
      <div class="event-head">
        <strong>{r.deviceCode} · {r.deviceName}</strong>
        <span class="badge {r.state}">{RETRIEVAL_STATE[r.state]}</span>
        <span class="muted">取用于 {fmtTime(r.retrievedAt)} · 上报人：{r.reporterName || '—'}</span>
      </div>
      {#if r.note}<div class="muted" style="margin:4px 0">备注：{r.note}</div>{/if}

      {#if r.state === 'OPEN'}
        <div class="form-grid">
          <div><label>回补人 *</label><input bind:value={forms[r.id].operator} placeholder="姓名" /></div>
          <div><label>电池实际批次 *</label><input bind:value={forms[r.id].batteryBatch} placeholder="如 BAT-2026-C01" /></div>
          <div><label>电池有效期至 *</label><input type="date" bind:value={forms[r.id].batteryExpiry} /></div>
          <div><label>电极片实际批次 *</label><input bind:value={forms[r.id].padsBatch} placeholder="如 PAD-2026-C01" /></div>
          <div><label>电极片有效期至 *</label><input type="date" bind:value={forms[r.id].padsExpiry} /></div>
        </div>
        <button class="small" on:click={() => doRestock(r)}>登记回补（核对实物批次）</button>
      {:else}
        <div class="restock-info">
          已回补 — 电池批次 <span class="mono">{r.restockBatteryBatch}</span>（至 {r.restockBatteryExpiry}），
          电极片批次 <span class="mono">{r.restockPadsBatch}</span>（至 {r.restockPadsExpiry}），
          回补人 {r.restockOperator}，{fmtTime(r.restockedAt)}
        </div>
        <div class="form-grid">
          <div><label>复检人 *</label><input bind:value={forms[r.id].inspector} placeholder="姓名" /></div>
          <div><label>备注</label><input bind:value={forms[r.id].notes} placeholder="复检情况" /></div>
        </div>
        <div class="checks">
          <label><input type="checkbox" bind:checked={forms[r.id].batteryOk} /> 电池正常</label>
          <label><input type="checkbox" bind:checked={forms[r.id].padsOk} /> 电极片正常</label>
          <label><input type="checkbox" bind:checked={forms[r.id].cabinetOk} /> 箱体正常</label>
        </div>
        <button class="small" on:click={() => doRecheck(r)}>提交复检（全部通过则闭环）</button>
      {/if}
      {#if msg[r.id]}
        <div class="notice {msg[r.id].ok ? 'ok' : 'err'}">{msg[r.id].text}</div>
      {/if}
    </div>
  {/each}

  {#if closedRetrievals.length > 0}
    <button class="ghost small" style="margin-top:8px" on:click={() => (showHistory = !showHistory)}>
      {showHistory ? '收起' : '展开'}已闭环事件（{closedRetrievals.length}）
    </button>
    {#if showHistory}
      <table style="margin-top:8px">
        <thead><tr><th>设备</th><th>取用时间</th><th>回补批次</th><th>回补人</th><th>闭环时间</th></tr></thead>
        <tbody>
          {#each closedRetrievals as r (r.id)}
            <tr>
              <td>{r.deviceCode}</td>
              <td>{fmtTime(r.retrievedAt)}</td>
              <td class="mono">{r.restockBatteryBatch} / {r.restockPadsBatch}</td>
              <td>{r.restockOperator}</td>
              <td>{fmtTime(r.closedAt)}</td>
            </tr>
          {/each}
        </tbody>
      </table>
    {/if}
  {/if}
</div>

<!-- 设备台账：含内部维修电话与耗材批次（仅维护端可见） -->
<div class="card">
  <h3>设备台账 <span class="muted">（含内部信息，公众页不展示）</span></h3>
  <div class="scroll">
    <table>
      <thead>
        <tr>
          <th>编号</th><th>名称</th><th>状态</th><th>内部维修电话</th>
          <th>电池批次 / 有效期</th><th>电极片批次 / 有效期</th>
          <th>下次应巡检</th><th>操作</th>
        </tr>
      </thead>
      <tbody>
        {#each $adminDevices as d (d.id)}
          <tr>
            <td class="mono">{d.code}</td>
            <td>{d.name}</td>
            <td>
              <span class="badge {d.status}">{STATUS_META[d.status]?.label}</span>
              {#if d.reason}<div class="muted" style="font-size:12px">{d.reason}</div>{/if}
            </td>
            <td class="mono">{d.maintainerPhone}</td>
            <td class="mono">{d.batteryBatch ?? '—'}<br />{d.batteryExpiry ?? ''}</td>
            <td class="mono">{d.padsBatch ?? '—'}<br />{d.padsExpiry ?? ''}</td>
            <td>{d.nextInspectionDue ?? '—'}</td>
            <td style="white-space:nowrap">
              <button class="small" on:click={() => (inspectTarget = d)}>巡检</button>
              <button class="ghost small" on:click={() => (historyTarget = d)}>记录</button>
            </td>
          </tr>
        {/each}
      </tbody>
    </table>
  </div>
</div>

<!-- 报警记录（本地模拟） -->
<div class="card">
  <h3>报警记录 <span class="muted">（本地模拟）</span></h3>
  {#if $alarms.length === 0}
    <p class="muted">暂无报警。</p>
  {:else}
    <table>
      <thead><tr><th>时间</th><th>设备</th><th>内容</th></tr></thead>
      <tbody>
        {#each $alarms as a (a.id)}
          <tr>
            <td>{fmtTime(a.triggeredAt)}</td>
            <td class="mono">{a.deviceCode}</td>
            <td>{a.message}</td>
          </tr>
        {/each}
      </tbody>
    </table>
  {/if}
</div>

{#if inspectTarget}
  <InspectionModal device={inspectTarget} on:close={onModalDone} />
{/if}
{#if historyTarget}
  <DeviceHistoryModal device={historyTarget} on:close={onModalDone} />
{/if}

<style>
  .stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 16px; }
  @media (max-width: 800px) { .stats { grid-template-columns: repeat(2, 1fr); } }
  .stat { text-align: center; }
  .stat .num { font-size: 30px; font-weight: 800; }
  .card { margin-bottom: 16px; }
  h3 { margin: 0 0 12px; font-size: 16px; }
  .event { border: 1px solid var(--line); border-radius: 10px; padding: 12px 14px; margin-bottom: 12px; }
  .event-head { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
  .restock-info {
    background: #fffbeb; border: 1px solid #fde68a; border-radius: 8px;
    padding: 8px 12px; font-size: 13px; margin: 8px 0;
  }
  .scroll { overflow-x: auto; }
</style>
