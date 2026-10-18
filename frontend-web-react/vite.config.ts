import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// 前台开发服务器：5173 端口；/api、/upload、/ws 代理到后端 8080（与 Vue 版 frontend-web 完全一致）
// 需要与旧版 Vue 前台并行运行时：npm run dev -- --port 5175
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/upload': { target: 'http://localhost:8080', changeOrigin: true },
      // 站内私信 WebSocket（PRD CHT-02）：开发期由 Vite 代理转发，生产同源部署无需此配置
      '/ws': { target: 'http://localhost:8080', ws: true, changeOrigin: true }
    }
  },
  build: {
    chunkSizeWarningLimit: 900,
    rollupOptions: {
      output: {
        // 拆分体积较大的第三方依赖，改善首屏与缓存命中
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          if (id.includes('@radix-ui')) return 'radix'
          if (id.includes('react-router') || id.includes('react-dom') || id.includes('/react/')) return 'react'
          if (id.includes('@reduxjs') || id.includes('react-redux') || id.includes('axios')) return 'data'
          return 'vendor'
        }
      }
    }
  }
})
