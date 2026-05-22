import { test, expect, type Page } from "@playwright/test";

/** Header-scoped locators — disambiguates header nav buttons from form submits. */
function headerLoginBtn(page: Page)  { return page.locator("header").getByRole("button", { name: "Log in" }); }
function headerSignUpBtn(page: Page) { return page.locator("header").getByRole("button", { name: "Sign up" }); }

test.describe("auth", () => {
  test("login as dev user → header shows UserMenu → logout reverts", async ({ page }) => {
    await page.goto("/login");

    const loginForm = page.getByRole("form", { name: "Log in" });
    await loginForm.getByLabel("Email").fill("dev@flashgif.example");
    await loginForm.getByLabel("Password").fill("dev-password");
    await loginForm.getByRole("button", { name: "Log in" }).click();

    await expect(page).toHaveURL("/", { timeout: 10_000 });

    const accountBtn = page.getByRole("button", { name: /Account menu for/ });
    await expect(accountBtn).toBeVisible();
    await expect(headerLoginBtn(page)).toHaveCount(0);

    // Open menu, log out.
    await accountBtn.click();
    await page.getByRole("menuitem", { name: /Log out/ }).click();

    // Back to logged-out state.
    await expect(headerLoginBtn(page)).toBeVisible({ timeout: 10_000 });
    await expect(page.getByRole("button", { name: /Account menu for/ })).toHaveCount(0);
  });

  test("register a fresh user → header shows their display name", async ({ page }) => {
    const stamp    = Date.now().toString(36);
    const email    = `e2e-${stamp}@flashgif.example`;
    const username = `e2e_${stamp}`;

    await page.goto("/register");

    const form = page.getByRole("form", { name: "Sign up" });
    await form.getByLabel("Display name").fill(`E2E ${stamp}`);
    await form.getByLabel("Username").fill(username);
    await form.getByLabel("Email").fill(email);
    await form.getByLabel("Password").fill("playwright-test-password");
    await form.getByRole("button", { name: "Sign up" }).click();

    await expect(page).toHaveURL("/", { timeout: 10_000 });
    await expect(page.getByRole("button", { name: new RegExp(`Account menu for E2E ${stamp}`) }))
      .toBeVisible();
    await expect(headerSignUpBtn(page)).toHaveCount(0);
  });

  test("authenticated user visiting /login is redirected home", async ({ page }) => {
    await page.goto("/login");
    const loginForm = page.getByRole("form", { name: "Log in" });
    await loginForm.getByLabel("Email").fill("dev@flashgif.example");
    await loginForm.getByLabel("Password").fill("dev-password");
    await loginForm.getByRole("button", { name: "Log in" }).click();
    await expect(page).toHaveURL("/", { timeout: 10_000 });

    await page.goto("/login");
    await expect(page).toHaveURL("/");
  });
});
