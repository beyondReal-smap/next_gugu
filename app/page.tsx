import type { Metadata } from 'next';
import { Landing } from '@/features/landing/Landing';

// 루트는 앱 설치 랜딩(App Store / Google Play). 웹 게임은 /play 로 이동.
export const metadata: Metadata = {
  title: { absolute: '구구 어드벤처 — 구구단이 모험이 된다' },
  description:
    '3D 월드를 탐험하며 구구단 대결! 2단부터 9단까지 8개 지역을 정복하는 구구단 학습 게임. App Store와 Google Play에서 무료로 설치하세요.',
  alternates: { canonical: '/' },
  openGraph: {
    type: 'website',
    siteName: '구구 어드벤처',
    locale: 'ko_KR',
    url: '/',
    title: '구구 어드벤처 — 구구단이 모험이 된다',
    description: '3D 월드 모험, 6가지 게임 모드, 틀린 문제 집중 반복. iOS·Android 무료.',
    images: [{ url: '/landing/02-world3d.webp', width: 600, height: 1304, alt: '구구 어드벤처 3D 월드' }],
  },
};

export default function Page() {
  return <Landing />;
}
