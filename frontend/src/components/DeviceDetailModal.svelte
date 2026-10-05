<script>
  import { onMount, createEventDispatcher } from 'svelte'
  import { STATUS_META, post } from '../stores.js'

  export let deviceId

  const dispatch = createEventDispatcher()
  let device = null
  let error = ''
  let notice = ''
  let confirming = false
  let reporterName = ''
  let note = ''
  let busy = false

  onMount(load)

  async function load() {
    device = await fetch(`/api/public/devices/${deviceId}`).then(r => r.json())
  }

  async function doRetrieve() {
    busy = true
    error = ''
    try {
      await post(`/api/public/devices/${deviceId}/retrieve`, {
        reporterName: reporterName || '现场市民',
        note
      })
      notice = '已上报紧急取用并触发报警，设备已转为「待回补」。维护人员回补耗材并复检通过后才会恢复可用。'
      confirming = false
      await load()
    } catch (e) {
      error = e.message
    } finally {
      busy = false
    }
  }
</script>

<!-- svelte-ignore a11y-click-events-have-key-events a11y-no-static-element-interactions -->
<div class="modal-mask" on:click|self={() => dispatch('close', !!notice)}>
  <div class="modal">
    {#if !device}
      <p class="muted">加载中…</p>
    {:else}
      <h3>
        {device.code} · {device.name}
        <span class="badge {device.status}">{STATUS_META[device.status]?.label}</span>
      </h3>
      {#if device.reason}
        <div class="notice err">停用原因：{device.reason}</div>
      {/if}
      <table>
        <tbody>
          <tr><th>地址</th><td>{device.address}</td></tr>
          <tr><th>位置</th><td class="mono">{device.latitude.toFixed(4)}, {device.longitude.toFixed(4)}（模拟定位）</td></tr>
          <tr><th>最近巡检</th><td>{device.lastInspectionDate ?? '—'}</td></tr>
          <tr><th>下次应巡检</th><td>{device.nextInspectionDue ?? '—'}</td></tr>
        </tbody>
      </table>
      <p class="muted" style="font-size:13px">
        如遇设备故障或耗材问题，请前往附近其他可用设备，或联系现场工作人员。
      </p>

      {#if error}<div class="notice err">{error}</div>{/if}
      {#if notice}<div class="notice ok">{notice}</div>{/if}

      {#if confirming}
        <div class="form-grid">
          <div><label>上报人（选填）</label><input bind:value={reporterName} placeholder="您的称呼" /></div>
          <div><label>情况说明（选填）</label><input bind:value={note} placeholder="如：患者位置、现场情况" /></div>
        </div>
        <div class="notice err">确认后将立即触发取用报警，设备转为待回补，不可撤销。</div>
      {/if}

      <div class="modal-actions">
        <button class="ghost" on:click={() => dispatch('close', !!notice)}>关闭</button>
        {#if device.status === 'AVAILABLE' && !notice}
          {#if confirming}
            <button class="ghost" on:click={() => (confirming = false)}>取消</button>
            <button disabled={busy} on:click={doRetrieve}>确认紧急取用</button>
          {:else}
            <button on:click={() => (confirming = true)}>紧急取用上报</button>
          {/if}
        {:else if device.status !== 'AVAILABLE'}
          <button disabled title="设备当前不可用，不能取用">紧急取用上报</button>
        {/if}
      </div>
    {/if}
  </div>
</div>
