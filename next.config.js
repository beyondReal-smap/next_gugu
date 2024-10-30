/** @type {import('next').NextConfig} */
const nextConfig = {
    output: 'export',
    // distDir: 'out', // 선택적으로 추가
    images: {
      unoptimized: true, // Next.js Image 컴포넌트를 사용하는 경우 필요
    },
    webpack: (config, { buildId, dev, isServer, defaultLoaders, webpack }) => {
      if (config.resolve) {
        config.resolve.fallback = {
          ...config.resolve.fallback,
          fs: false,
        };
      }
      return config;
    },
  };
  
  module.exports = nextConfig;