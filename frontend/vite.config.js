import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src')
    }
  },
  server: {
    port: 3000,
    proxy: {
      '/product': {
        target: 'http://localhost:9000',
        changeOrigin: true
      },
      '/order': {
        target: 'http://localhost:9000',
        changeOrigin: true
      },
      '/user': {
        target: 'http://localhost:9000',
        changeOrigin: true
      }
    }
  }
})
