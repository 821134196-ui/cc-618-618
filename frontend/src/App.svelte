<script>
  import { onMount } from 'svelte'
  import PublicView from './components/PublicView.svelte'
  import AdminView from './components/AdminView.svelte'

  let route = location.hash || '#/'
  onMount(() => {
    const onHash = () => { route = location.hash || '#/' }
    window.addEventListener('hashchange', onHash)
    return () => window.removeEventListener('hashchange', onHash)
  })
</script>

<header>
  <div class="brand">
    <span class="logo">✚</span>
    <div>
      <h1>城市 AED 急救设备平台</h1>
      <p>设备巡检 · 急救取用 · 耗材回补</p>
    </div>
  </div>
  <nav>
    <a href="#/" class:active={route === '#/'}>公众查询</a>
    <a href="#/admin" class:active={route === '#/admin'}>维护工作台</a>
  </nav>
</header>

<main>
  {#if route === '#/admin'}
    <AdminView />
  {:else}
    <PublicView />
  {/if}
</main>

<style>
  header {
    background: linear-gradient(135deg, #991b1b, #b91c1c);
    color: #fff;
    padding: 14px 28px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 12px;
  }
  .brand { display: flex; align-items: center; gap: 14px; }
  .logo {
    width: 44px; height: 44px; border-radius: 10px;
    background: #fff; color: #b91c1c;
    display: flex; align-items: center; justify-content: center;
    font-size: 26px; font-weight: 800;
  }
  h1 { font-size: 19px; margin: 0; }
  .brand p { margin: 2px 0 0; font-size: 12px; opacity: 0.85; }
  nav { display: flex; gap: 8px; }
  nav a {
    color: #fecaca; text-decoration: none; padding: 8px 18px;
    border-radius: 8px; font-size: 15px;
  }
  nav a.active, nav a:hover { background: rgba(255,255,255,0.16); color: #fff; }
  main { padding: 20px 28px; max-width: 1500px; margin: 0 auto; }
</style>
