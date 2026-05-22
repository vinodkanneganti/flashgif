import { CollectionDetailClient } from "./CollectionDetailClient";

export const dynamic = "force-dynamic";
export const metadata = { title: "Collection" };

export default function CollectionDetailPage({ params }: { params: { id: string } }) {
  return <CollectionDetailClient id={params.id} />;
}
