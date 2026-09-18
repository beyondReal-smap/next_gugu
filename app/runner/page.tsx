import type { Metadata } from 'next';
import { RunnerScreen } from '@/features/runner/RunnerScreen';

export const metadata: Metadata = {
  title: '구구 점프',
  description: '구구단 정답을 골라 장애물을 넘는 캐주얼 달리기 게임입니다.',
  alternates: { canonical: '/runner' },
  robots: { index: false, follow: true },
};

export default function Page() {
  return <RunnerScreen />;
}
