import { SearchClient } from "./SearchClient";

export const dynamic = "force-dynamic";   // search depends on query params

export default function SearchPage() {
  return <SearchClient />;
}
