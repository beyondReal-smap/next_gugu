import type { Metadata } from 'next';
import { LaneRunnerScreen } from '@/features/runner/lane/LaneRunnerScreen';

export const metadata: Metadata = {
  title: '구구 레인',
  description: '길을 바꿔 장애물을 피하고 구구단 정답이 적힌 길로 달리는 캐주얼 게임입니다.',
  alternates: { canonical: '/lane-runner' },
  robots: { index: false, follow: true },
};

export default function Page() {
  return <LaneRunnerScreen />;
}
