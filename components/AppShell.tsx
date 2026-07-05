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
    </>
  );
}
