<script>
  import { onMount } from 'svelte'
  import { devices, devicesLoading, devicesError, loadDevices, selectedId, userLocation } from '../stores.js'
  import { STATUS_META } from '../status.js'
  import AedMap from './AedMap.svelte'
  import DeviceList from './DeviceList.svelte'
  import DeviceDetail from './DeviceDetail.svelte'

  let query = ''
  let statusFilter = ''
  let located = false

  onMount(() => {
    loadDevices()
  })

  // 地图与文字列表使用同一份过滤结果，保证两处看到的设备与状态完全一致
  $: filtered = $devices.filter((d) => {
    const q = query.trim().toLowerCase()
    const matchQuery = !q
      || d.code.toLowerCase().includes(q)
      || d.name.toLowerCase().includes(q)
      || (d.address || '').toLowerCase().includes(q)
    const matchStatus = !statusFilter || d.status === statusFilter
    return matchQuery && matchStatus
  })

  $: selected = $devices.find((d) => d.id === $selectedId) || null

  /** 本地模拟定位：在设备分布范围内随机生成一个位置 */
  function simulateLocate() {
    if ($devices.length === 0) return
    const lats = $devices.map((d) => d.latitude)
    const lngs = $devices.map((d) => d.longitude)
    const lat = Math.min(...lats) + Math.random() * (Math.max(...lats) - Math.min(...lats))
    const lng = Math.min(...lngs) + Math.random() * (Math.max(...lngs) - Math.min(...lngs))
    userLocation.set({ lat, lng })
    located = true
  }

  function clearLocate() {
    userLocation.set(null)
    located = false
  }
</script>

<div class="page">
  <div class="toolbar card">
    <div class="search">
      <input
        type="search"
        placeholder="搜索设备编号、名称或地址…"
        bind:value={query}
        aria-label="搜索设备"
      />
    </div>
    <select bind:value={statusFilter} aria-label="按状态筛选">
      <option value="">全部状态</option>
      {#each Object.entries(STATUS_META) as [key, meta]}
        <option value={key}>{meta.label}</option>
      {/each}
    </select>
    {#if located}
      <button class="ghost" on:click={clearLocate}>清除定位</button>
    {:else}
      <button class="ghost" on:click={simulateLocate}>📍 模拟定位</button>
    {/if}
    <button class="ghost" on:click={() => loadDevices()}>刷新</button>
  </div>

  {#if $devicesError}
    <div class="error-box">{$devicesError}</div>
  {/if}

  <div class="layout">
    <section class="map-col">
      <AedMap items={filtered} />
      {#if selected}
        <DeviceDetail device={selected} on:changed={() => loadDevices()} />
      {:else}
        <div class="card hint">点击地图标记或列表项查看设备详情；紧急情况下可在详情中发起“紧急取用”模拟报警。</div>
      {/if}
    </section>

    <section class="list-col">
      <div class="list-head muted">
        {#if $devicesLoading}
          正在加载…
        {:else}
          共 {filtered.length} 台设备（与地图同源，状态实时一致）
        {/if}
      </div>
      <DeviceList items={filtered} />
    </section>
  </div>
</div>

<style>
  .page { display: flex; flex-direction: column; gap: 14px; }
  .toolbar {
    display: flex;
    gap: 10px;
    align-items: center;
    padding: 12px 16px;
  }
  .toolbar .search { flex: 1; }
  .toolbar select { width: 140px; }
  .toolbar button { white-space: nowrap; }
  .layout {
    display: grid;
    grid-template-columns: minmax(0, 3fr) minmax(300px, 2fr);
    gap: 14px;
    align-items: start;
  }
  .map-col { display: flex; flex-direction: column; gap: 14px; min-width: 0; }
  .list-col { display: flex; flex-direction: column; gap: 8px; min-width: 0; }
  .list-head { font-size: 13px; padding: 0 4px; }
  .hint { color: var(--muted); font-size: 14px; }
  @media (max-width: 960px) {
    .layout { grid-template-columns: 1fr; }
  }
</style>
