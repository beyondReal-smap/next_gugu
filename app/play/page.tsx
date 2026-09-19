import type { Metadata } from 'next';
import { Home } from '@/features/home/Home';
import { PlayViewContent } from '@/features/home/PlayViewContent';

// 웹 게임 홈. 루트(/)는 앱 설치 랜딩이 되었고 게임은 여기로 옮겼다.
// AppShell이 프리렌더 시점에 빈 div만 반환해 정적 HTML에 본문이 없으므로 /learn·/profile과 같이 noindex.
export const metadata: Metadata = {
  title: '웹에서 플레이',
  alternates: { canonical: '/play' },
  robots: { index: false, follow: true },
};

export default function Page() {
  return (
    <>
      <PlayViewContent />
      <Home />
    </>
  );
}
