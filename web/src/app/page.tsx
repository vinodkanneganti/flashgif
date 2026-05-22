import { getTrending, type MediaSummary } from "@/lib/api/endpoints";
import { HomeClient } from "./HomeClient";

// Render on every request while we shake out the data path. Switch back to
// `revalidate = 60` once the backend cache + DTO contract are stable so the
// home page can serve from the ISR cache.
export const dynamic = "force-dynamic";

export default async function Home() {
  // Server-side initial fetch for SEO + fast first paint. Falls back to client
  // fetch if backend is unreachable during SSR.
  let initial: MediaSummary[] = [];
  try {
    initial = await getTrending();
  } catch {
    initial = [];
  }

  return <HomeClient initial={initial} />;
}
