import { defineConfig } from 'vite'
import { svelte } from '@sveltejs/vite-plugin-svelte'

export default defineConfig({
  plugins: [svelte()],
  server: {
    // 开发时把 API 代理到 Quarkus
    proxy: { '/api': 'http://localhost:8080' }
  },
  build: {
    outDir: 'dist'
  }
})
