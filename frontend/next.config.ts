import type { NextConfig } from "next";

const backendOrigin = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8088";

const contentSecurityPolicy = [
  "default-src 'self'",
  "script-src 'self' 'unsafe-inline' https://js.stripe.com",
  "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
  "img-src 'self' data: https:",
  "font-src 'self' data: https://fonts.gstatic.com",
  `connect-src 'self' ${backendOrigin} https://api.stripe.com`,
  "frame-src https://js.stripe.com https://hooks.stripe.com",
  "frame-ancestors 'none'",
  // BUG-D21-4: connect-src is derived from NEXT_PUBLIC_API_URL at build time, so it already
  // resolves to the real Railway origin (https://backend-production-8daea.up.railway.app) —
  // no hardcoded URL to swap in here. Do not add a *.railway.app wildcard.
].join("; ");

const nextConfig: NextConfig = {
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "Content-Security-Policy", value: contentSecurityPolicy },
          { key: "X-Frame-Options", value: "DENY" },
          { key: "X-Content-Type-Options", value: "nosniff" },
        ],
      },
    ];
  },
};

export default nextConfig;
