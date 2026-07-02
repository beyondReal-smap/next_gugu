"use client";

import React from 'react';
import { ThemeProvider } from '@/lib/state/ThemeProvider';
import { GameProvider } from '@/lib/state/GameProvider';
import { SessionProvider } from '@/lib/state/SessionProvider';
import { AppShell } from '@/components/AppShell';

interface ClientLayoutProps {
  children: React.ReactNode;
}

// 전역 Provider 래핑 + 앱 셸(온보딩/탭/세션 오버레이)
export default function ClientLayout({ children }: ClientLayoutProps) {
  return (
    <ThemeProvider>
      <GameProvider>
        <SessionProvider>
          <AppShell>{children}</AppShell>
        </SessionProvider>
      </GameProvider>
    </ThemeProvider>
  );
}
