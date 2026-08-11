/** @type {import('next').NextConfig} */
const isDev = process.env.NODE_ENV !== 'production';

const securityHeaders = [
  { key: 'X-Content-Type-Options', value: 'nosniff' },
  { key: 'X-Frame-Options', value: 'DENY' },
  { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
  { key: 'Permissions-Policy', value: 'camera=(), microphone=(), geolocation=()' },
  // HSTS apenas em produção (HTTPS). Em dev (HTTP) o browser ignoraria mesmo.
  ...(isDev
    ? []
    : [{ key: 'Strict-Transport-Security', value: 'max-age=63072000; includeSubDomains; preload' }]),
  {
    key: 'Content-Security-Policy',
    value: [
      "default-src 'self'",
      // Em dev o Next.js/webpack usa eval() para source-maps e Fast Refresh.
      // 'unsafe-eval' é necessário apenas em desenvolvimento.
      isDev
        ? "script-src 'self' 'unsafe-inline' 'unsafe-eval'"
        : "script-src 'self' 'unsafe-inline'",
      "style-src 'self' 'unsafe-inline'",
      "img-src 'self' data: blob:",
      "font-src 'self' data:",
      // Em dev o webpack usa websocket para HMR (ws://localhost:3000)
      isDev
        ? "connect-src 'self' ws://localhost:3000 " + (process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080')
        : "connect-src 'self' " + (process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080'),
      "object-src 'none'",
      "frame-ancestors 'none'",
      "base-uri 'self'",
      "form-action 'self'",
    ].join('; '),
  },
];

const nextConfig = {
  output: 'standalone',
  reactStrictMode: true,
  poweredByHeader: false,
  compiler: {
    removeConsole: isDev ? false : { exclude: ['error', 'warn'] },
  },
  async headers() {
    return [{ source: '/:path*', headers: securityHeaders }];
  },
};

module.exports = nextConfig;
