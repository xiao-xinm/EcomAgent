import { defineConfig } from '@playwright/test'

const reuseExistingServer = !process.env.CI

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  timeout: 30_000,
  expect: {
    timeout: 5_000,
  },
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  webServer: [
    {
      command: 'npm --workspace @smartcs/client-h5 run dev -- --host 127.0.0.1 --port 3000',
      url: 'http://127.0.0.1:3000',
      reuseExistingServer,
      timeout: 120_000,
    },
    {
      command: 'npm --workspace @smartcs/workstation run dev -- --host 127.0.0.1 --port 3001',
      url: 'http://127.0.0.1:3001',
      reuseExistingServer,
      timeout: 120_000,
    },
  ],
  projects: [
    {
      name: 'client-h5-mobile',
      testMatch: /client-h5\.spec\.ts/,
      use: {
        baseURL: 'http://127.0.0.1:3000',
        viewport: { width: 390, height: 844 },
        isMobile: true,
        hasTouch: true,
      },
    },
    {
      name: 'workstation-desktop',
      testMatch: /workstation\.spec\.ts/,
      use: {
        baseURL: 'http://127.0.0.1:3001',
        viewport: { width: 1440, height: 900 },
      },
    },
  ],
})
