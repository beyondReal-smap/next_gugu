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
    
    // 폰트 파일 처리를 위한 규칙 추가
    config.module.rules.push({
      test: /\.(woff|woff2|eot|ttf|otf)$/i,
      type: 'asset/resource',
      generator: {
        filename: 'static/fonts/[hash][ext][query]'
      }
    });

    return config;
  },
  // 정적 자산에 대한 헤더 설정 추가
  async headers() {
    return [
      {
        source: '/fonts/:path*',
        headers: [
          {
            key: 'Cache-Control',
            value: 'public, max-age=31536000, immutable'
          }
        ]
      }
    ];
  }
};

module.exports = nextConfig;