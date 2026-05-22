import { notFound } from "next/navigation";
import { ApiError } from "@/lib/api/client";
import { getChannel, type ChannelResponse } from "@/lib/api/channels";
import { ChannelClient } from "./ChannelClient";

export const dynamic = "force-dynamic";

export async function generateMetadata({ params }: { params: { username: string } }) {
  return { title: `@${params.username}` };
}

export default async function ChannelPage({ params }: { params: { username: string } }) {
  let channel: ChannelResponse;
  try {
    channel = await getChannel(params.username);
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) notFound();
    throw e;
  }
  return <ChannelClient channel={channel} />;
}
