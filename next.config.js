const path = require('path');
const { PHASE_DEVELOPMENT_SERVER } = require('next/constants');

// 운영(pm2 serve)이 dist를 그대로 서빙하므로, 개발 서버는 기본적으로 별도 폴더를 씁니다.
// NEXT_DIST_DIR로 바꿀 수 있지만 개발 서버가 dist를 쓰는 설정은 거부합니다(운영 페이지가 사라짐).
const PROD_DIST_DIR = 'dist';
const DEV_DIST_DIR = '.next-dev';

function resolveDistDir(phase) {
  const isDev = phase === PHASE_DEVELOPMENT_SERVER;
  const raw = process.env.NEXT_DIST_DIR;
  if (raw === undefined) return isDev ? DEV_DIST_DIR : PROD_DIST_DIR;
  const dir = raw.trim();
  if (!dir || path.isAbsolute(dir) || path.normalize(dir).split(path.sep).includes('..')) {
    throw new Error(`NEXT_DIST_DIR는 web/ 안의 상대 경로여야 합니다: "${raw}"`);
  }
  if (isDev && path.resolve(__dirname, dir) === path.resolve(__dirname, PROD_DIST_DIR)) {
    throw new Error('개발 서버는 NEXT_DIST_DIR=dist를 쓸 수 없습니다. 운영이 서빙하는 폴더를 덮어씁니다.');
  }
  return dir;
}

/** @type {(phase: string) => import('next').NextConfig} */
module.exports = (phase) => ({
  output: 'export',
  images: {
    unoptimized: true,
  },
  // Vercel 배포를 위한 추가 설정
  distDir: resolveDistDir(phase),  // 빌드는 dist, 개발 서버는 .next-dev (NEXT_DIST_DIR로 변경 가능)
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
});
