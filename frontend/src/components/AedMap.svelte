<script>
  import { devices, selectedId, userLocation } from '../stores.js'
  import { statusColor, STATUS_META } from '../status.js'

  /** 要展示的设备（与文字列表同源的过滤结果） */
  export let items = []

  // 视口尺寸与边距
  const W = 800
  const H = 540
  const PAD = 48

  // 视野范围基于全部设备（而非过滤结果），保证搜索时地图视野稳定
  $: all = $devices.length > 0 ? $devices : items
  $: lats = all.map((d) => d.latitude)
  $: lngs = all.map((d) => d.longitude)
  $: minLat = Math.min(...lats)
  $: maxLat = Math.max(...lats)
  $: minLng = Math.min(...lngs)
  $: maxLng = Math.max(...lngs)
  $: spanLat = maxLat - minLat || 0.001
  $: spanLng = maxLng - minLng || 0.001

  $: projectX = (lng) => PAD + ((lng - minLng) / spanLng) * (W - 2 * PAD)
  $: projectY = (lat) => H - PAD - ((lat - minLat) / spanLat) * (H - 2 * PAD)

  function select(id) {
    selectedId.set(id)
  }
</script>

<div class="card map-card">
  <div class="map-head">
    <h2>设备分布地图（本地模拟）</h2>
    <div class="legend">
      {#each Object.entries(STATUS_META) as [, meta]}
        <span class="legend-item">
          <span class="dot" style="background:{meta.color}"></span>{meta.label}
        </span>
      {/each}
      {#if $userLocation}
        <span class="legend-item"><span class="dot me"></span>我的位置（模拟）</span>
      {/if}
    </div>
  </div>

  <svg viewBox="0 0 {W} {H}" role="img" aria-label="AED 设备分布地图">
    <!-- 底图：本地模拟的街区网格，不依赖外部地图服务 -->
    <rect x="0" y="0" width={W} height={H} fill="#e8eef3" rx="10" />
    {#each Array(9) as _, i}
      <line x1={PAD + i * 88} y1="0" x2={PAD + i * 88} y2={H} stroke="#f8fafc" stroke-width="10" />
    {/each}
    {#each Array(6) as _, i}
      <line x1="0" y1={PAD + i * 88} x2={W} y2={PAD + i * 88} stroke="#f8fafc" stroke-width="10" />
    {/each}
    <!-- 模拟河流与主干道 -->
    <path d="M -20 {H * 0.78} C {W * 0.3} {H * 0.62}, {W * 0.55} {H * 0.95}, {W + 20} {H * 0.7}"
          fill="none" stroke="#bfdbfe" stroke-width="26" stroke-linecap="round" />
    <line x1="0" y1={H * 0.35} x2={W} y2={H * 0.42} stroke="#ffffff" stroke-width="16" />
    <line x1={W * 0.3} y1="0" x2={W * 0.36} y2={H} stroke="#ffffff" stroke-width="16" />

    <!-- 模拟定位点 -->
    {#if $userLocation}
      <g>
        <circle cx={projectX($userLocation.lng)} cy={projectY($userLocation.lat)} r="14"
                fill="#3b82f6" opacity="0.25" />
        <circle cx={projectX($userLocation.lng)} cy={projectY($userLocation.lat)} r="6"
                fill="#2563eb" stroke="#fff" stroke-width="2" />
      </g>
    {/if}

    <!-- 设备标记：与文字列表渲染同一份数据 -->
    {#each items as d (d.id)}
      <g class="marker" class:selected={d.id === $selectedId}
         on:click={() => select(d.id)}
         on:keydown={(e) => e.key === 'Enter' && select(d.id)}
         tabindex="0" role="button" aria-label={`${d.name} ${d.code}`}>
        {#if d.id === $selectedId}
          <circle cx={projectX(d.longitude)} cy={projectY(d.latitude)} r="16"
                  fill="none" stroke={statusColor(d.status)} stroke-width="2.5" />
        {/if}
        <circle cx={projectX(d.longitude)} cy={projectY(d.latitude)} r="10"
                fill={statusColor(d.status)} stroke="#fff" stroke-width="2" />
        <text x={projectX(d.longitude)} y={projectY(d.latitude) + 3.5} class="plus">✚</text>
        <text x={projectX(d.longitude)} y={projectY(d.latitude) + 26} class="code">{d.code}</text>
      </g>
    {/each}
  </svg>
</div>

<style>
  .map-card { padding: 12px 16px 8px; }
  .map-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
  .legend { display: flex; gap: 14px; font-size: 13px; color: var(--muted); flex-wrap: wrap; }
  .legend-item { display: inline-flex; align-items: center; gap: 5px; }
  .dot { width: 10px; height: 10px; border-radius: 50%; display: inline-block; }
  .dot.me { background: #2563eb; }
  svg { width: 100%; height: auto; display: block; }
  .marker { cursor: pointer; outline: none; }
  .marker .plus {
    fill: #fff; font-size: 11px; font-weight: 700;
    text-anchor: middle; pointer-events: none;
  }
  .marker .code {
    fill: var(--ink); font-size: 11px;
    text-anchor: middle; pointer-events: none;
    paint-order: stroke; stroke: #fff; stroke-width: 3px;
  }
</style>
