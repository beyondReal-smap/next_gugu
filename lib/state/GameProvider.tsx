"use client";
import React, { createContext, useContext, useEffect, useRef, useState, useCallback } from 'react';
import { GameState, SessionResult, CommitResult } from '../types';
import { getLevelInfo, LevelInfo } from '../level';
import { DEFAULT_STATE, applyVisit, applySession } from './commit';

const STORAGE_KEY = 'gugu.progress.v1';

interface GameContextValue {
  loaded: boolean;
  state: GameState;
  levelInfo: LevelInfo;
  commitSession: (result: SessionResult) => CommitResult;
  setDailyGoal: (goal: number) => void;
  setOnboarded: (v: boolean) => void;
  resetProgress: () => void;
}

const GameContext = createContext<GameContextValue | null>(null);

export function GameProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<GameState>(DEFAULT_STATE);
  const [loaded, setLoaded] = useState(false);
  const ref = useRef(state);
  ref.current = state;

  // 로드 + 방문(스트릭/데일리) 갱신
  useEffect(() => {
    let s = DEFAULT_STATE;
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) s = { ...DEFAULT_STATE, ...JSON.parse(raw) };
    } catch (e) {
      console.error('진행상태 로드 실패:', e);
    }
    s = applyVisit(s);
    setState(s);
    setLoaded(true);
  }, []);

  // 영속화
  useEffect(() => {
    if (!loaded) return;
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch (e) {
      console.error('진행상태 저장 실패:', e);
    }
  }, [state, loaded]);

  const commitSession = useCallback((result: SessionResult): CommitResult => {
    const { next, commit } = applySession(ref.current, result);
    setState(next);
    return commit;
  }, []);

  const setDailyGoal = useCallback((goal: number) => {
    setState((s) => ({ ...s, dailyGoal: Math.max(5, goal) }));
  }, []);
  const setOnboarded = useCallback((v: boolean) => {
    setState((s) => ({ ...s, onboarded: v }));
  }, []);
  const resetProgress = useCallback(() => {
    setState(applyVisit({ ...DEFAULT_STATE, onboarded: true }));
  }, []);

  const value: GameContextValue = {
    loaded,
    state,
    levelInfo: getLevelInfo(state.totalXp),
    commitSession,
    setDailyGoal,
    setOnboarded,
    resetProgress,
  };

  return <GameContext.Provider value={value}>{children}</GameContext.Provider>;
}

export function useGame(): GameContextValue {
  const ctx = useContext(GameContext);
  if (!ctx) throw new Error('useGame must be used within GameProvider');
  return ctx;
}
