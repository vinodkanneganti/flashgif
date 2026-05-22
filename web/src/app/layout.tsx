import type { Metadata } from "next";
import "./globals.css";
import { QueryProvider } from "@/lib/query/QueryProvider";
import { Header } from "@/components/layout/Header";
import { Footer } from "@/components/layout/Footer";
import { getCurrentUser } from "@/lib/auth/server";

export const metadata: Metadata = {
  title: { default: "FlashGif", template: "%s · FlashGif" },
  description: "Discover, save, and share the perfect GIF.",
};

export default async function RootLayout({ children }: { children: React.ReactNode }) {
  // SSR-fetch current user via the httpOnly cookie. Seeds React Query so the
  // header renders the right state on first paint (no Login → UserMenu flash).
  const user = await getCurrentUser();

  return (
    <html lang="en" className="h-full">
      <body className="min-h-full bg-background text-foreground antialiased flex flex-col">
        <QueryProvider seed={{ me: user }}>
          <Header user={user} />
          <main className="flex-1">{children}</main>
          <Footer />
        </QueryProvider>
      </body>
    </html>
  );
}
