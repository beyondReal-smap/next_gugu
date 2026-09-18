"use client";
import React, { createContext, useContext, useState, useCallback } from 'react';
import { GameMode } from '../types';
import { useWebTrial } from './WebTrialProvider';

export interface ActiveSession {
  mode: GameMode;
  table: number | null; // null=혼합
}

interface SessionContextValue {
  active: ActiveSession | null;
  start: (mode: GameMode, table: number | null) => void;
  end: () => void;
}

const SessionContext = createContext<SessionContextValue | null>(null);

export function SessionProvider({ children }: { children: React.ReactNode }) {
  const [active, setActive] = useState<ActiveSession | null>(null);
  const { tryPlay } = useWebTrial();
  // 웹 체험판은 하루 판 수 한도를 넘으면 시작하지 않는다(설치 안내는 WebTrialProvider가 띄움)
  const start = useCallback((mode: GameMode, table: number | null) => {
    if (!tryPlay()) return;
    setActive({ mode, table });
  }, [tryPlay]);
  const end = useCallback(() => setActive(null), []);
  return <SessionContext.Provider value={{ active, start, end }}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionContextValue {
  const ctx = useContext(SessionContext);
  if (!ctx) throw new Error('useSession must be used within SessionProvider');
  return ctx;
}
