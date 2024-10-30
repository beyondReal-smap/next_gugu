/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'export',
  images: {
    unoptimized: true,
  },
  // Vercel 배포를 위한 추가 설정
  distDir: 'dist',  // 빌드 출력 디렉토리 지정
  trailingSlash: true,  // URL 끝에 슬래시 추가
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