/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const JS_BUILD_TARGET = 'es2020'
const LEGACY_BROWSER_TARGETS = ['chrome83', 'edge83', 'firefox78', 'safari14']

// 项目是 ESM (`"type": "module"`),__dirname / path 不存在;
// 用 URL + import.meta.url 拿到当前配置文件所在目录,纯 DOM 类型,不需要 @types/node。
const configDir = new URL('.', import.meta.url).pathname

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': new URL('./src', import.meta.url).pathname,
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
    exclude: ['**/node_modules/**', 'e2e/**'],
    testTimeout: 30000,
    hookTimeout: 30000,
  },
  server: {
    port: 3000,
    // vite dev 默认只接受 localhost/127.0.0.1,加上公司域名/IP 才能从外部访问。
    // 调试阶段若临时想关掉安全检查,改成 allowedHosts: true 即可。
    allowedHosts: [
      'www.skillhub.zhengderl.cn',
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
      // OAuth2 dance lands on /login/oauth2/code/<registrationId>; without
      // this entry the dev / preview servers return the SPA index.html
      // (because /login/* is otherwise served by Vite as a frontend route)
      // and the Spring Security callback handler never gets the code to
      // exchange. Forwarding to the backend lets Spring set SESSION and
      // redirect back to the SPA via the publicBaseUrl-relative Location.
      '/login/oauth2/code': {
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
      '/login/oauth2/code': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})

// 仅用于 IDE / 静态检查; 运行时由 vite.config.ts 主导。
export const __viteConfigDir = configDir