<script>
  import { onMount } from 'svelte'
  import { api } from '../api.js'
  import { loadDevices } from '../stores.js'

  let devices = []
  let deviceId = ''
  let error = ''
  let message = ''
  let busy = false

  let form = emptyForm()

  function emptyForm() {
    return {
      inspectorName: '',
      batteryOk: true,
      padsOk: true,
      cabinetOk: true,
      batteryBatch: '',
      batteryExpiry: '',
      padsBatch: '',
      padsExpiry: '',
      notes: ''
    }
  }

  onMount(load)

  async function load() {
    try {
      devices = await api.get('/admin/aeds')
      if (!deviceId && devices.length > 0) deviceId = String(devices[0].id)
    } catch (e) {
      error = e.message
    }
  }

  $: selected = devices.find((d) => String(d.id) === String(deviceId)) || null

  async function submit() {
    if (!selected) return
    busy = true
    error = ''
    message = ''
    try {
      const saved = await api.post(`/admin/aeds/${selected.id}/inspections`, form)
      message = saved.passed
        ? `巡检已登记（${selected.code}），结论：通过`
        : `巡检已登记（${selected.code}），结论：未通过——设备将保持停用`
      form = emptyForm()
      await Promise.all([load(), loadDevices()])
    } catch (e) {
      error = e.message
    } finally {
      busy = false
    }
  }
</script>

<div class="card">
  <h2>巡检登记</h2>
  <p class="muted intro">逐项检查电池、电极片、箱体，并登记设备内实物耗材的批次与有效期。</p>

  {#if error}<div class="error-box">{error}</div>{/if}
  {#if message}<div class="success-box">{message}</div>{/if}

  <div class="form-grid">
    <div>
      <label>巡检设备 *</label>
      <select bind:value={deviceId}>
        {#each devices as d (d.id)}
          <option value={String(d.id)}>{d.code} · {d.name}</option>
        {/each}
      </select>
    </div>
    <div><label>巡检人 *</label><input bind:value={form.inspectorName} placeholder="姓名" /></div>
  </div>

  {#if selected && selected.openUsageEventId != null}
    <div class="warn-box">
      该设备存在未闭环的取用事件（#{selected.openUsageEventId}）。
      例行巡检不会使其恢复可用——请前往「取用事件处理」完成回补与复检。
    </div>
  {/if}

  <h3>检查项</h3>
  <div class="checks">
    <label><input type="checkbox" bind:checked={form.batteryOk} /> 电池正常</label>
    <label><input type="checkbox" bind:checked={form.padsOk} /> 电极片正常</label>
    <label><input type="checkbox" bind:checked={form.cabinetOk} /> 箱体正常</label>
  </div>

  <h3>实物耗材批次</h3>
  <div class="form-grid">
    <div><label>电池批次 *</label><input bind:value={form.batteryBatch} placeholder="如 BAT-2026-10A" /></div>
    <div><label>电池有效期 *</label><input type="date" bind:value={form.batteryExpiry} /></div>
    <div><label>电极片批次 *</label><input bind:value={form.padsBatch} placeholder="如 PAD-2026-10A" /></div>
    <div><label>电极片有效期 *</label><input type="date" bind:value={form.padsExpiry} /></div>
  </div>

  <div style="margin-top:12px">
    <label>备注</label>
    <textarea rows="2" bind:value={form.notes} placeholder="选填"></textarea>
  </div>

  <div class="form-actions">
    <button on:click={submit} disabled={busy || !selected}>{busy ? '提交中…' : '提交巡检'}</button>
  </div>
</div>

<style>
  .intro { font-size: 13px; margin-top: -6px; }
  h3 { margin-top: 16px; }
  .warn-box {
    background: var(--warn-bg);
    color: var(--warn);
    border: 1px solid #fde68a;
    border-radius: 8px;
    padding: 10px 14px;
    margin: 12px 0;
    font-size: 13.5px;
  }
</style>
