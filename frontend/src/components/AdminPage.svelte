<script>
  import UsageEventsPanel from './UsageEventsPanel.svelte'
  import InspectionForm from './InspectionForm.svelte'
  import DeviceTable from './DeviceTable.svelte'

  const tabs = [
    { key: 'events', label: '取用事件处理' },
    { key: 'inspect', label: '巡检登记' },
    { key: 'devices', label: '设备管理' }
  ]
  let tab = 'events'
</script>

<div class="admin">
  <div class="tabs">
    {#each tabs as t}
      <button class="ghost" class:active={tab === t.key} on:click={() => (tab = t.key)}>
        {t.label}
      </button>
    {/each}
  </div>

  {#if tab === 'events'}
    <UsageEventsPanel />
  {:else if tab === 'inspect'}
    <InspectionForm />
  {:else}
    <DeviceTable />
  {/if}
</div>

<style>
  .admin { display: flex; flex-direction: column; gap: 14px; }
  .tabs { display: flex; gap: 8px; flex-wrap: wrap; }
  .tabs button.active {
    background: var(--brand);
    color: #fff;
    border-color: var(--brand);
  }
</style>
