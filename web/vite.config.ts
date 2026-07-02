import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import fs from 'node:fs'
import path from 'node:path'

const JS_BUILD_TARGET = 'es2020'
const LEGACY_BROWSER_TARGETS = ['chrome83', 'edge83', 'firefox78', 'safari14']

/**
 * Materialise {@code public/runtime-config.js} from the latest
 * {@code runtime-config.js.template} at vite-config load time.
 *
 * <p>The Docker image performs the same substitution via
 * {@code docker-entrypoint.d/30-runtime-config.sh}; here we do it inline so the
 * Vite dev server picks up fresh {@code SKILLHUB_WEB_AUTH_*} env vars whenever
 * it restarts — no manual file editing required.
 */
function materialiseRuntimeConfig() {
  const ROOT = process.cwd()
  const TEMPLATE = path.join(ROOT, 'runtime-config.js.template')
  const TARGET = path.join(ROOT, 'public', 'runtime-config.js')

  if (!fs.existsSync(TEMPLATE)) {
    // Template missing — leave whatever was already materialised in place.
    return
  }

  const source = fs.readFileSync(TEMPLATE, 'utf8')
  const rendered = source.replace(/\$\{([A-Z0-9_]+)\}/g, (match, name) => {
    if (Object.prototype.hasOwnProperty.call(process.env, name)) {
      return process.env[name] ?? ''
    }
    return match
  })
  fs.writeFileSync(TARGET, rendered, 'utf8')
}

// Run once at config load so the file is up-to-date before Vite starts
// serving it. Re-runs whenever the dev server is restarted (e.g. by a tooling
// change or after re-saving vite.config.ts).
materialiseRuntimeConfig()

/**
 * Vite plugin: re-render the file when the template changes so HMR-style
 * edits to the template surface without restarting Vite manually.
 */
function runtimeConfigWatcher(): Plugin {
  const ROOT = process.cwd()
  const TEMPLATE = path.join(ROOT, 'runtime-config.js.template')
  return {
    name: 'skillhub-runtime-config-watcher',
    configureServer(server) {
      if (fs.existsSync(TEMPLATE)) {
        server.watcher.add(TEMPLATE)
        server.watcher.on('change', (changed) => {
          if (changed === TEMPLATE) {
            materialiseRuntimeConfig()
            server.ws.send({ type: 'full-reload' })
          }
        })
      }
    },
  }
}

export default defineConfig({
  plugins: [runtimeConfigWatcher(), react()],
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
})
