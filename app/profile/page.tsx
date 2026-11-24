import type { Metadata } from 'next';
import { Profile } from '@/features/profile/Profile';

// 개인 학습 기록 화면 — 검색 결과에 노출될 이유가 없고, 프리렌더 HTML에 고유 본문도 없다.
export const metadata: Metadata = {
  title: '프로필',
  description: '레벨과 경험치, 연속 학습 스트릭, 업적 17종의 달성 현황을 확인합니다.',
  alternates: { canonical: '/profile' },
  robots: { index: false, follow: true },
};

export default function Page() {
  return <Profile />;
}
