import type { Metadata } from 'next';
import { BasketScreen } from '@/features/basket/BasketScreen';

export const metadata: Metadata = {
  title: '구구 바구니',
  description: '바구니를 직접 움직여 구구단 정답이 적힌 열매를 받는 어린이 게임입니다.',
  alternates: { canonical: '/basket' },
  robots: { index: false, follow: true },
};

export default function Page() {
  return <BasketScreen />;
}
