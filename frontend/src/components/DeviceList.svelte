<script>
  import { selectedId, userLocation } from '../stores.js'
  import { statusLabel, haversineMeters, fmtDistance } from '../status.js'

  /** 与地图同源的过滤结果 */
  export let items = []

  function select(id) {
    selectedId.set(id)
  }

  function distanceOf(d) {
    if (!$userLocation) return null
    return haversineMeters($userLocation, { lat: d.latitude, lng: d.longitude })
  }

  // 模拟定位后按距离排序，未定位按编号排序（排序仅影响展示顺序，不影响状态）
  $: sorted = [...items].sort((a, b) => {
    if ($userLocation) return distanceOf(a) - distanceOf(b)
    return a.code.localeCompare(b.code)
  })
</script>

<div class="list">
  {#if sorted.length === 0}
    <div class="card empty muted">没有符合条件的设备</div>
  {/if}
  {#each sorted as d (d.id)}
    <button
      type="button"
      class="item card"
      class:selected={d.id === $selectedId}
      on:click={() => select(d.id)}
    >
      <div class="row">
        <span class="name">{d.name}</span>
        <span class="badge {d.status}">{statusLabel(d.status)}</span>
      </div>
      <div class="row sub muted">
        <span>{d.code}</span>
        {#if distanceOf(d) != null}
          <span>距我 {fmtDistance(distanceOf(d))}</span>
        {/if}
      </div>
      <div class="row sub muted">{d.address}</div>
      {#if d.status !== 'AVAILABLE'}
        <div class="row reason" class:warn={d.status === 'PENDING_REPLENISHMENT'}>
          {d.statusReason}
        </div>
      {/if}
    </button>
  {/each}
</div>

<style>
  .list { display: flex; flex-direction: column; gap: 8px; max-height: 78vh; overflow-y: auto; padding-right: 2px; }
  .item {
    display: block;
    text-align: left;
    background: var(--card);
    color: var(--ink);
    border: 1px solid var(--line);
    padding: 12px 14px;
    width: 100%;
  }
  .item:hover { border-color: #fca5a5; background: #fff; }
  .item.selected { border-color: var(--brand); box-shadow: 0 0 0 1px var(--brand); }
  .row { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
  .row + .row { margin-top: 4px; }
  .name { font-weight: 600; font-size: 14px; }
  .sub { font-size: 12.5px; }
  .empty { text-align: center; padding: 24px; }
</style>
