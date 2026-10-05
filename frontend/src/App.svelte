<script>
  import { onMount } from 'svelte'
  import PublicPage from './components/PublicPage.svelte'
  import AdminPage from './components/AdminPage.svelte'

  let route = '#/'

  function syncRoute() {
    route = window.location.hash || '#/'
  }

  onMount(() => {
    syncRoute()
    window.addEventListener('hashchange', syncRoute)
    return () => window.removeEventListener('hashchange', syncRoute)
  })
</script>

<header class="topbar">
  <div class="brand">
    <span class="logo">✚</span>
    <div>
      <div class="title">城市 AED 急救设备平台</div>
      <div class="subtitle">设备巡检 · 急救取用 · 耗材回补</div>
    </div>
  </div>
  <nav>
    <a href="#/" class:active={route === '#/' || route === ''}>公众查询</a>
    <a href="#/admin" class:active={route === '#/admin'}>维护后台</a>
  </nav>
</header>

<main>
  {#if route === '#/admin'}
    <AdminPage />
  {:else}
    <PublicPage />
  {/if}
</main>

<style>
  .topbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 24px;
    background: var(--brand);
    color: #fff;
  }
  .brand { display: flex; align-items: center; gap: 12px; }
  .logo {
    width: 40px; height: 40px;
    display: flex; align-items: center; justify-content: center;
    background: #fff; color: var(--brand);
    border-radius: 10px; font-size: 22px; font-weight: 700;
  }
  .title { font-size: 17px; font-weight: 700; }
  .subtitle { font-size: 12px; opacity: 0.85; }
  nav { display: flex; gap: 8px; }
  nav a {
    padding: 8px 16px;
    border-radius: 8px;
    font-size: 14px;
    color: #fecaca;
  }
  nav a:hover { background: rgba(255, 255, 255, 0.12); color: #fff; }
  nav a.active { background: #fff; color: var(--brand); font-weight: 600; }
  main { flex: 1; padding: 20px 24px; max-width: 1400px; width: 100%; margin: 0 auto; }
</style>
