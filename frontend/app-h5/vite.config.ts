import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('../client-h5/src', import.meta.url)),
        '@app': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      port: Number(env.VITE_PORT) || 3002,
    },
  }
})
