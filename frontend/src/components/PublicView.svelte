<script>
  import { onMount } from 'svelte'
  import {
    filteredDevices, publicDevices, searchText, statusFilter, refreshPublic
  } from '../stores.js'
  import DeviceMap from './DeviceMap.svelte'
  import DeviceList from './DeviceList.svelte'
  import DeviceDetailModal from './DeviceDetailModal.svelte'

  let selected = null

  onMount(refreshPublic)

  function openDetail(e) {
    selected = e.detail
  }
  async function closeDetail(changed) {
    selected = null
    if (changed) await refreshPublic()
  }
</script>

<div class="toolbar card">
  <div class="search">
    <input
      placeholder="搜索设备编号、名称或地址，如：AED-001 / 图书馆 / 人民大道"
      bind:value={$searchText} />
  </div>
  <select bind:value={$statusFilter}>
    <option value="ALL">全部状态</option>
    <option value="AVAILABLE">可用</option>
    <option value="PENDING_RESTOCK">待回补</option>
    <option value="OUT_OF_SERVICE">停用</option>
  </select>
  <span class="muted">共 {$filteredDevices.length} 台设备（地图与列表同源，状态一致）</span>
</div>

<div class="panels">
  <div class="card map-panel">
    <h3>设备分布地图 <span class="muted">（本地模拟定位）</span></h3>
    <DeviceMap devices={$filteredDevices} on:select={openDetail} />
    <div class="legend">
      <span><i style="background:#16a34a"></i>可用</span>
      <span><i style="background:#d97706"></i>待回补</span>
      <span><i style="background:#dc2626"></i>停用</span>
    </div>
  </div>
  <div class="card list-panel">
    <h3>设备列表</h3>
    <DeviceList devices={$filteredDevices} on:select={openDetail} />
  </div>
</div>

{#if selected}
  <DeviceDetailModal deviceId={selected.id} on:close={e => closeDetail(e.detail)} />
{/if}

<style>
  .toolbar {
    display: flex; gap: 14px; align-items: center; margin-bottom: 16px; flex-wrap: wrap;
  }
  .toolbar .search { flex: 1; min-width: 260px; }
  .toolbar select { width: 140px; }
  .panels {
    display: grid; grid-template-columns: minmax(420px, 7fr) minmax(380px, 5fr);
    gap: 16px; align-items: start;
  }
  @media (max-width: 1000px) {
    .panels { grid-template-columns: 1fr; }
  }
  h3 { margin: 0 0 12px; font-size: 16px; }
  .legend { display: flex; gap: 18px; margin-top: 10px; font-size: 13px; color: var(--muted); }
  .legend i {
    display: inline-block; width: 12px; height: 12px; border-radius: 50%;
    margin-right: 5px; vertical-align: -1px;
  }
</style>
