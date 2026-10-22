import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    // 本地开发代理到 nginx（8088），同源访问后端
    proxy: {
      '/admin': 'http://localhost:8088',
      '/conductor': 'http://localhost:8088'
    }
  }
})
