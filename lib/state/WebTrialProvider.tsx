"use client";
import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { isNativeShell } from '@/lib/native/platform';
import { consumePlay, remainingPlays } from '@/lib/webTrial';
import { InstallPrompt, InstallReason } from '@/features/webTrial/InstallPrompt';

interface WebTrialContextValue {
  /** 웹 체험판으로 동작 중인지 (네이티브 앱이면 false — 제한 없음) */
  limited: boolean;
  /** 오늘 남은 판 수. 제한 없음 또는 로드 전이면 null */
  remaining: number | null;
  /** 한 판 시작 요청. 허용되면 차감 후 true, 한도 초과면 설치 안내를 띄우고 false */
  tryPlay: () => boolean;
  /** 앱 전용 기능 진입 시 설치 안내 */
  requireApp: (reason: InstallReason) => void;
}

const WebTrialContext = createContext<WebTrialContextValue | null>(null);

// 게임 시작 지점(SessionProvider·AdventureProvider·Runner)이 이 Provider 하나만 거치게 해
// 제한 로직이 호출부(홈·학습·결과 화면 등)로 흩어지지 않게 한다.
export function WebTrialProvider({ children }: { children: React.ReactNode }) {
  const [limited, setLimited] = useState(false);
  const [remaining, setRemaining] = useState<number | null>(null);
  const [prompt, setPrompt] = useState<InstallReason | null>(null);

  useEffect(() => {
    if (isNativeShell()) return;
    setLimited(true);
    setRemaining(remainingPlays());
  }, []);

  const tryPlay = useCallback(() => {
    if (isNativeShell()) return true;
    const ok = consumePlay();
    setRemaining(remainingPlays());
    if (!ok) setPrompt('limit');
    return ok;
  }, []);

  const requireApp = useCallback((reason: InstallReason) => setPrompt(reason), []);

  return (
    <WebTrialContext.Provider value={{ limited, remaining, tryPlay, requireApp }}>
      {children}
      {prompt && <InstallPrompt reason={prompt} onClose={() => setPrompt(null)} />}
    </WebTrialContext.Provider>
  );
}

export function useWebTrial(): WebTrialContextValue {
  const ctx = useContext(WebTrialContext);
  if (!ctx) throw new Error('useWebTrial must be used within WebTrialProvider');
  return ctx;
}
