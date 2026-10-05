<script>
  import { createEventDispatcher } from 'svelte'
  import { STATUS_META } from '../stores.js'

  export let devices = []
  const dispatch = createEventDispatcher()
</script>

{#if devices.length === 0}
  <p class="muted">没有匹配的设备，请调整搜索条件。</p>
{:else}
  <div class="scroll">
    <table>
      <thead>
        <tr><th>编号</th><th>名称 / 地址</th><th>状态</th></tr>
      </thead>
      <tbody>
        {#each devices as d (d.id)}
          <tr class="clickable" on:click={() => dispatch('select', d)}>
            <td class="mono">{d.code}</td>
            <td>
              <div>{d.name}</div>
              <div class="muted" style="font-size:12px">{d.address}</div>
            </td>
            <td>
              <span class="badge {d.status}">{STATUS_META[d.status]?.label ?? d.status}</span>
              {#if d.reason}
                <div class="muted" style="font-size:12px;margin-top:3px">{d.reason}</div>
              {/if}
            </td>
          </tr>
        {/each}
      </tbody>
    </table>
  </div>
{/if}

<style>
  .scroll { max-height: 560px; overflow-y: auto; }
</style>
