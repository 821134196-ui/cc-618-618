<script>
  import { createEventDispatcher } from 'svelte'
  import { STATUS_META } from '../stores.js'

  export let devices = []

  const dispatch = createEventDispatcher()

  // 本地模拟地图：把设备经纬度归一化映射到 SVG 视窗
  const W = 900, H = 600, PAD = 60
  $: lats = devices.map(d => d.latitude)
  $: lngs = devices.map(d => d.longitude)
  $: minLat = Math.min(...lats, 31.20), maxLat = Math.max(...lats, 31.26)
  $: minLng = Math.min(...lngs, 121.44), maxLng = Math.max(...lngs, 121.50)

  function x(d) {
    const span = maxLng - minLng || 0.01
    return PAD + ((d.longitude - minLng) / span) * (W - 2 * PAD)
  }
  function y(d) {
    const span = maxLat - minLat || 0.01
    return H - PAD - ((d.latitude - minLat) / span) * (H - 2 * PAD)
  }
</script>

<svg viewBox="0 0 {W} {H}" role="img" aria-label="AED 设备分布地图">
  <!-- 模拟城区底图：街区、道路、河流、公园 -->
  <rect width={W} height={H} fill="#e8eef4" rx="10" />
  <path d="M0,470 C220,430 380,520 600,480 S850,420 900,450 L900,600 L0,600 Z" fill="#bfdbfe" opacity="0.7" />
  <rect x="610" y="70" width="180" height="130" rx="14" fill="#bbf7d0" opacity="0.8" />
  <text x="700" y="140" text-anchor="middle" font-size="14" fill="#15803d">滨江公园</text>

  {#each [120, 260, 400, 540, 680, 820] as gx}
    <line x1={gx} y1="0" x2={gx} y2={H} stroke="#ffffff" stroke-width="12" />
    <line x1={gx} y1="0" x2={gx} y2={H} stroke="#cbd5e1" stroke-width="1.5" stroke-dasharray="10 8" />
  {/each}
  {#each [110, 240, 370, 500] as gy}
    <line x1="0" y1={gy} x2={W} y2={gy} stroke="#ffffff" stroke-width="12" />
    <line x1="0" y1={gy} x2={W} y2={gy} stroke="#cbd5e1" stroke-width="1.5" stroke-dasharray="10 8" />
  {/each}

  <!-- 设备标记：颜色即状态，与列表同一数据源 -->
  {#each devices as d (d.id)}
    <!-- svelte-ignore a11y-click-events-have-key-events a11y-no-static-element-interactions -->
    <g class="marker" transform="translate({x(d)},{y(d)})"
       on:click={() => dispatch('select', d)}>
      <circle r="15" fill={STATUS_META[d.status]?.color ?? '#64748b'}
              stroke="#fff" stroke-width="3" />
      <circle r="4.5" fill="#fff" />
      <text y="32" text-anchor="middle">{d.code}</text>
      <title>{d.code} {d.name} — {STATUS_META[d.status]?.label}{d.reason ? `（${d.reason}）` : ''}</title>
    </g>
  {/each}
</svg>

<style>
  svg { width: 100%; height: auto; display: block; }
  .marker { cursor: pointer; }
  .marker:hover circle:first-child { stroke: #0f172a; }
  .marker text { font-size: 12px; fill: #334155; font-weight: 600; }
</style>
