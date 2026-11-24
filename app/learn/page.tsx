import type { Metadata } from 'next';
import { Learn } from '@/features/learn/Learn';

// AppShell이 프리렌더 시점(loaded=false)에 빈 div만 반환하므로 이 라우트의 정적 HTML에는
// 고유 본문이 없다. 색인시키면 홈과 중복 콘텐츠가 되고 검색 결과에서도 쓸모가 없다.
// 검색 유입은 정적 문서인 /guide/* 가 받는다. follow는 유지해 링크는 계속 따라가게 둔다.
export const metadata: Metadata = {
  title: '학습',
  description: '2단부터 9단까지 단별 학습 진도와 마스터리 별을 확인하고, 약한 구구단을 골라 연습합니다.',
  alternates: { canonical: '/learn' },
  robots: { index: false, follow: true },
};

export default function Page() {
  return <Learn />;
}
