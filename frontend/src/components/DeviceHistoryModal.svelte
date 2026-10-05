<script>
  import { onMount, createEventDispatcher } from 'svelte'
  import { fmtTime, RETRIEVAL_STATE } from '../stores.js'

  export let device

  const dispatch = createEventDispatcher()
  let inspections = []
  let retrievals = []

  onMount(async () => {
    ;[inspections, retrievals] = await Promise.all([
      fetch(`/api/admin/devices/${device.id}/inspections`).then(r => r.json()),
      fetch(`/api/admin/devices/${device.id}/retrievals`).then(r => r.json())
    ])
  })
</script>

<!-- svelte-ignore a11y-click-events-have-key-events a11y-no-static-element-interactions -->
<div class="modal-mask" on:click|self={() => dispatch('close')}>
  <div class="modal">
    <h3>{device.code} · {device.name} — 巡检与取用记录</h3>

    <h4>巡检记录</h4>
    {#if inspections.length === 0}
      <p class="muted">暂无巡检记录。</p>
    {:else}
      <table>
        <thead>
          <tr><th>时间</th><th>类型</th><th>巡检员</th><th>电池</th><th>电极片</th><th>箱体</th><th>实物批次</th><th>结论</th></tr>
        </thead>
        <tbody>
          {#each inspections as i (i.id)}
            <tr>
              <td>{fmtTime(i.inspectedAt)}</td>
              <td>{i.type === 'RECHECK' ? '复检' : '例行'}</td>
              <td>{i.inspectorName}</td>
              <td>{i.batteryOk ? '✓' : '✗'}</td>
              <td>{i.padsOk ? '✓' : '✗'}</td>
              <td>{i.cabinetOk ? '✓' : '✗'}</td>
              <td class="mono">{i.batteryBatch ?? '—'} / {i.padsBatch ?? '—'}</td>
              <td>{i.passed ? '通过' : '未通过'}</td>
            </tr>
          {/each}
        </tbody>
      </table>
    {/if}

    <h4 style="margin-top:16px">取用事件</h4>
    {#if retrievals.length === 0}
      <p class="muted">暂无取用事件。</p>
    {:else}
      <table>
        <thead>
          <tr><th>取用时间</th><th>状态</th><th>上报人</th><th>回补批次（电池 / 电极片）</th><th>回补人</th><th>闭环时间</th></tr>
        </thead>
        <tbody>
          {#each retrievals as r (r.id)}
            <tr>
              <td>{fmtTime(r.retrievedAt)}</td>
              <td><span class="badge {r.state}">{RETRIEVAL_STATE[r.state]}</span></td>
              <td>{r.reporterName ?? '—'}</td>
              <td class="mono">{r.restockBatteryBatch ?? '—'} / {r.restockPadsBatch ?? '—'}</td>
              <td>{r.restockOperator ?? '—'}</td>
              <td>{fmtTime(r.closedAt)}</td>
            </tr>
          {/each}
        </tbody>
      </table>
    {/if}

    <div class="modal-actions">
      <button class="ghost" on:click={() => dispatch('close')}>关闭</button>
    </div>
  </div>
</div>

<style>
  h4 { margin: 8px 0; font-size: 14px; }
</style>
