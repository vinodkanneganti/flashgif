/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  images: {
    // Rendition URLs are seeded as example.invalid in dev; real env will pin a CDN host here.
    remotePatterns: [
      { protocol: 'https', hostname: 'example.invalid' },
      { protocol: 'https', hostname: '**.flashgif.example' },
      { protocol: 'http',  hostname: 'localhost' },
    ],
  },
};

export default nextConfig;
