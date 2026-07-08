import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    // Cobertura solo del código fuente. Threshold solo de LÍNEAS al 80%
    // (hoy 80,5%); statements (79,6%), branches (73,4%) y functions (65,6%)
    // quedan sin gate hasta subirlas, para no romper la CI
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,vue}'],
      reporter: ['text', 'html', 'lcov'],
      thresholds: {
        lines: 80,
      },
    },
  },
})
