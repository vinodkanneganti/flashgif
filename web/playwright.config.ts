import { defineConfig, devices } from "@playwright/test";

/**
 * Smoke tests assume the backend is already up on :8080 with seeded data.
 * Run: `pnpm dev` in one terminal, `pnpm test:e2e` in another.
 *
 * Playwright auto-starts the Next dev server on :3000 so the e2e command is one-step.
 */
export default defineConfig({
  testDir: "./tests/e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI ? "github" : "list",
  use: {
    baseURL: "http://localhost:3000",
    trace: "retain-on-failure",
  },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
  ],
  webServer: {
    command: "pnpm dev",
    url: "http://localhost:3000",
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
});
