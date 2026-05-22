import { redirect } from "next/navigation";
import { getCurrentUser } from "@/lib/auth/server";
import { getChannel } from "@/lib/api/channels";
import { ProfileForm } from "./ProfileForm";

export const dynamic = "force-dynamic";
export const metadata = { title: "Profile settings" };

export default async function SettingsProfilePage() {
  const user = await getCurrentUser();
  if (!user) redirect("/login?next=/settings/profile");

  // Seed the form with the current public-profile values.
  const channel = await getChannel(user.username);

  return (
    <div className="container mx-auto max-w-xl px-4 py-8 space-y-6">
      <h1 className="text-2xl font-semibold">Profile settings</h1>
      <ProfileForm
        initial={{
          display_name: channel.display_name,
          bio: channel.bio ?? "",
          website_url: channel.website_url ?? "",
          avatar_url: channel.avatar_url ?? "",
          banner_url: channel.banner_url ?? "",
          social_links: channel.social_links ?? {},
        }}
      />
    </div>
  );
}
