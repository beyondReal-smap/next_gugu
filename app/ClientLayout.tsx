"use client";

import React from 'react';
import { ThemeProvider } from '@/lib/state/ThemeProvider';
import { PrefsProvider } from '@/lib/state/PrefsProvider';
import { GameProvider } from '@/lib/state/GameProvider';
import { SessionProvider } from '@/lib/state/SessionProvider';
import { AdventureProvider } from '@/lib/state/AdventureProvider';
import { AuthProvider } from '@/lib/state/AuthProvider';
import { PremiumProvider } from '@/lib/state/PremiumProvider';
import { WebTrialProvider } from '@/lib/state/WebTrialProvider';
import { AppShell } from '@/components/AppShell';
import { AnalyticsLoader } from '@/components/AnalyticsLoader';
import { MetaPixelLoader } from '@/components/MetaPixelLoader';
import { ReminderSync } from '@/components/ReminderSync';
import { LearningSync } from '@/components/LearningSync';

interface ClientLayoutProps {
  children: React.ReactNode;
}

// 전역 Provider 래핑 + 앱 셸(온보딩/탭/세션 오버레이)
// PremiumProvider는 useAuth를 사용하므로 AuthProvider 안쪽에 배치
export default function ClientLayout({ children }: ClientLayoutProps) {
  return (
    <ThemeProvider>
      <PrefsProvider>
        <AnalyticsLoader />
        <MetaPixelLoader />
        <AuthProvider>
          <PremiumProvider>
            <GameProvider>
              <WebTrialProvider>
                <SessionProvider>
                  <AdventureProvider>
                    <ReminderSync />
                    <LearningSync />
                    <AppShell>{children}</AppShell>
                  </AdventureProvider>
                </SessionProvider>
              </WebTrialProvider>
            </GameProvider>
          </PremiumProvider>
        </AuthProvider>
      </PrefsProvider>
    </ThemeProvider>
  );
}
