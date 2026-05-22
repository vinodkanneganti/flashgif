import { test, expect } from "@playwright/test";

test("home page renders Trending heading + type chips + at least one tile", async ({ page }) => {
  await page.goto("/");

  await expect(page.getByRole("heading", { name: "Trending" })).toBeVisible();

  // Type chips
  await expect(page.getByRole("button", { name: "All" })).toBeVisible();
  await expect(page.getByRole("button", { name: "GIFs" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Stickers" })).toBeVisible();

  // At least one media tile rendered (skeleton placeholders have no <img>)
  await expect(page.locator(".masonry-grid img").first()).toBeVisible({ timeout: 10_000 });
});

test("switching to GIFs chip filters the grid", async ({ page }) => {
  await page.goto("/");

  await page.getByRole("button", { name: "GIFs" }).click();
  await expect(page.getByRole("button", { name: "GIFs" })).toHaveAttribute("aria-pressed", "true");

  // Grid should still have items (dev seed has both gifs + stickers)
  await expect(page.locator(".masonry-grid img").first()).toBeVisible({ timeout: 10_000 });
});
