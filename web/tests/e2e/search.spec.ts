import { test, expect } from "@playwright/test";

test("searching from the header navigates to /search and shows results", async ({ page }) => {
  await page.goto("/");

  const search = page.getByPlaceholder("Search GIFs and stickers…");
  await search.fill("happy");
  await search.press("Enter");

  await expect(page).toHaveURL(/\/search\?q=happy/);
  await expect(page.getByRole("heading", { name: /Results for/ })).toBeVisible();
  await expect(page.locator(".masonry-grid img").first()).toBeVisible({ timeout: 10_000 });
});

test("autocomplete dropdown appears after typing", async ({ page }) => {
  await page.goto("/");

  const search = page.getByPlaceholder("Search GIFs and stickers…");
  await search.fill("ha");

  // Dropdown is a stack of buttons inside the wrapper; wait for at least one.
  await expect(page.locator("button").filter({ hasText: /title/i }).first())
    .toBeVisible({ timeout: 5_000 });
});

test("direct nav to /search?q=cat shows results", async ({ page }) => {
  await page.goto("/search?q=cat");
  // Heading renders with typographic curly quotes (&ldquo;/&rdquo;) — match
  // on the structural "Results for ... cat" without binding to quote chars.
  await expect(page.getByRole("heading", { name: /Results for .*cat/ })).toBeVisible();
  await expect(page.locator(".masonry-grid img").first()).toBeVisible({ timeout: 10_000 });
});
