import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

const JS_BUILD_TARGET = 'es2020'
const LEGACY_BROWSER_TARGETS = ['chrome83', 'edge83', 'firefox78', 'safari14']

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  build: {
    target: JS_BUILD_TARGET,
    cssTarget: LEGACY_BROWSER_TARGETS,
  },
  optimizeDeps: {
    esbuildOptions: {
      target: JS_BUILD_TARGET,
    },
  },
  test: {
    exclude: ['**/node_modules/**', '**/e2e/**'],
    testTimeout: 30000,
    hookTimeout: 30000,
  },
  server: {
    port: 3000,
    // vite dev 默认只接受 localhost/127.0.0.1,加上公司域名/IP 才能从外部访问。
    // 调试阶段若临时想关掉安全检查,改成 allowedHosts: true 即可。
    allowedHosts: [
      'skillhub.zhengderl.cn',
      'localhost',
      '127.0.0.1',
      '192.168.1.197',
    ],
    watch: {
      usePolling: true,
      interval: 150,
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/oauth2': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  // vite preview (production-build preview server) needs its own proxy
  // block because the dev and preview servers are configured separately.
  // Without these entries an SPA loaded from the preview server would
  // never reach the backend for /api or /oauth2 requests.
  preview: {
    host: '0.0.0.0',
    port: 3000,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/oauth2': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})