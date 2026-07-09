"use client";
import React from 'react';
import { usePathname } from 'next/navigation';
import { motion, AnimatePresence } from 'framer-motion';
import { useGame } from '@/lib/state/GameProvider';
import { useSession } from '@/lib/state/SessionProvider';
import { useAdventure } from '@/lib/state/AdventureProvider';
import { TabBar } from '@/components/ui/TabBar';
import { Onboarding } from '@/features/onboarding/Onboarding';
import { SessionScreen } from '@/features/session/SessionScreen';
import { AdventureScreen } from '@/features/adventure';
import { Paywall } from '@/features/paywall/Paywall';
import { triggerHapticFeedback, HAPTIC_TYPES } from '@/src/utils/hapticFeedback';

export function AppShell({ children }: { children: React.ReactNode }) {
  const { loaded, state } = useGame();
  const { active, end } = useSession();
  const { open: adventureOpen, closeAdventure } = useAdventure();

  // 앱형 스크롤: body 대신 main이 스크롤을 소유(globals.css에서 body overflow hidden).
  // 스크롤 컨테이너가 탭 간 공유되므로 경로 변경 시 최상단으로 리셋
  const pathname = usePathname() ?? '/';
  const mainRef = React.useRef<HTMLElement>(null);
  React.useEffect(() => {
    mainRef.current?.scrollTo(0, 0);
  }, [pathname]);

  // 전역 탭 햅틱: 모든 인터랙티브 요소(버튼/링크/role=button 등) 탭 시 가벼운 impact 피드백.
  // 한 곳에서 위임 처리하여 온보딩·탭바·페이월·어드벤처 등 전체 화면을 누락 없이 커버한다.
  // hapticFeedback 유틸이 네이티브(iOS/Android)/웹을 내부에서 분기하며, data-no-haptic으로 개별 옵트아웃 가능.
  React.useEffect(() => {
    const SELECTOR =
      'button, a[href], [role="button"], [role="tab"], input[type="checkbox"], input[type="radio"]';
    const onClick = (e: MouseEvent) => {
      const el = (e.target as HTMLElement | null)?.closest<HTMLElement>(SELECTOR);
      if (!el || el.hasAttribute('data-no-haptic')) return;
      if ((el as HTMLButtonElement).disabled || el.getAttribute('aria-disabled') === 'true') return;
      triggerHapticFeedback(HAPTIC_TYPES.IMPACT_LIGHT);
    };
    document.addEventListener('click', onClick);
    return () => document.removeEventListener('click', onClick);
  }, []);

  // 로드 전 깜빡임 방지
  if (!loaded) return <div className="h-dvh bg-bg" />;

  if (!state.onboarded) {
    return (
      <div className="app-scroll bg-bg">
        <Onboarding />
      </div>
    );
  }

  return (
    <>
      <main ref={mainRef} className="app-scroll pb-24">{children}</main>
      <TabBar />

      <AnimatePresence>
        {active && (
          <motion.div
            initial={{ opacity: 0, y: 24 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 24 }}
            transition={{ type: 'spring', stiffness: 320, damping: 30 }}
            className="fixed inset-0 z-50 mx-auto max-w-md bg-bg"
          >
            {/* key: 모드/단이 바뀌면 세션을 새로 마운트해 내부 ref 초기화 보장 */}
            <SessionScreen key={`${active.mode}-${active.table ?? 'all'}`} mode={active.mode} table={active.table} onExit={end} />
          </motion.div>
        )}
      </AnimatePresence>

      {/* 어드벤처 오버레이 — 세션 오버레이와 동일 패턴 */}
      <AnimatePresence>
        {adventureOpen && (
          <motion.div
            initial={{ opacity: 0, y: 24 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 24 }}
            transition={{ type: 'spring', stiffness: 320, damping: 30 }}
            className="fixed inset-0 z-50 mx-auto max-w-md bg-bg"
          >
            <AdventureScreen onExit={closeAdventure} />
          </motion.div>
        )}
      </AnimatePresence>

      {/* 페이월 오버레이 — 세션/어드벤처보다 위 (z-60) */}
      <Paywall />
    </>
  );
}
