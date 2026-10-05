<script>
  import { createEventDispatcher } from 'svelte'
  import { post } from '../stores.js'

  export let device

  const dispatch = createEventDispatcher()

  let inspectorName = ''
  let batteryOk = true
  let padsOk = true
  let cabinetOk = true
  let batteryBatch = device.batteryBatch ?? ''
  let batteryExpiry = device.batteryExpiry ?? ''
  let padsBatch = device.padsBatch ?? ''
  let padsExpiry = device.padsExpiry ?? ''
  let notes = ''
  let error = ''
  let busy = false

  async function submit() {
    busy = true
    error = ''
    try {
      await post(`/api/admin/devices/${device.id}/inspections`, {
        inspectorName, batteryOk, padsOk, cabinetOk,
        batteryBatch, batteryExpiry: batteryExpiry || null,
        padsBatch, padsExpiry: padsExpiry || null, notes
      })
      dispatch('close')
    } catch (e) {
      error = e.message
    } finally {
      busy = false
    }
  }
</script>

<!-- svelte-ignore a11y-click-events-have-key-events a11y-no-static-element-interactions -->
<div class="modal-mask" on:click|self={() => dispatch('close')}>
  <div class="modal">
    <h3>巡检录入 — {device.code} · {device.name}</h3>
    <p class="muted" style="font-size:13px">
      逐项检查并记录现场实物耗材批次；巡检合格后将以下次应检日期（90 天后）更新台账。
    </p>
    <div class="form-grid">
      <div><label>巡检员 *</label><input bind:value={inspectorName} placeholder="姓名" /></div>
      <div><label>备注</label><input bind:value={notes} placeholder="巡检情况说明" /></div>
    </div>
    <div class="checks">
      <label><input type="checkbox" bind:checked={batteryOk} /> 电池正常</label>
      <label><input type="checkbox" bind:checked={padsOk} /> 电极片正常</label>
      <label><input type="checkbox" bind:checked={cabinetOk} /> 箱体正常</label>
    </div>
    <div class="form-grid">
      <div><label>电池实物批次</label><input bind:value={batteryBatch} /></div>
      <div><label>电池有效期至</label><input type="date" bind:value={batteryExpiry} /></div>
      <div><label>电极片实物批次</label><input bind:value={padsBatch} /></div>
      <div><label>电极片有效期至</label><input type="date" bind:value={padsExpiry} /></div>
    </div>
    {#if error}<div class="notice err">{error}</div>{/if}
    <div class="modal-actions">
      <button class="ghost" on:click={() => dispatch('close')}>取消</button>
      <button disabled={busy || !inspectorName.trim()} on:click={submit}>提交巡检</button>
    </div>
  </div>
</div>
