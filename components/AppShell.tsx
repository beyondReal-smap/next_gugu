"use client";
import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useGame } from '@/lib/state/GameProvider';
import { useSession } from '@/lib/state/SessionProvider';
import { TabBar } from '@/components/ui/TabBar';
import { Onboarding } from '@/features/onboarding/Onboarding';
import { SessionScreen } from '@/features/session/SessionScreen';

export function AppShell({ children }: { children: React.ReactNode }) {
  const { loaded, state } = useGame();
  const { active, end } = useSession();

  // 로드 전 깜빡임 방지
  if (!loaded) return <div className="min-h-dvh bg-bg" />;

  if (!state.onboarded) return <Onboarding />;

  return (
    <>
      <main className="min-h-dvh pb-24">{children}</main>
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
    </>
  );
}
