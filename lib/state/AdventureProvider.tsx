"use client";
import React, { createContext, useContext, useCallback, useEffect, useRef, useState } from 'react';
import { AdventureProgress } from '../adventure/types';
import { DEFAULT_PROGRESS, applyBattle } from '../adventure/progress';
import { newlyUnlockedAdv } from '../adventure/achievements';

const STORAGE_KEY = 'gugu.adventure.v1';

interface AdventureContextValue {
  loaded: boolean;
  progress: AdventureProgress;
  open: boolean;
  openAdventure: () => void;
  closeAdventure: () => void;
  /** 배틀 결과 반영 후, 이번에 새로 해금된 어드벤처 업적 id 반환 */
  recordBattle: (npcId: string, won: boolean) => string[];
  resetAdventure: () => void;
}

const AdventureContext = createContext<AdventureContextValue | null>(null);

export function AdventureProvider({ children }: { children: React.ReactNode }) {
  const [progress, setProgress] = useState<AdventureProgress>(DEFAULT_PROGRESS);
  const [loaded, setLoaded] = useState(false);
  const [open, setOpen] = useState(false);
  const ref = useRef(progress);
  ref.current = progress;

  useEffect(() => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        // 외부 저장소 경계 검증 — 유효 JSON이어도 형태가 깨졌으면 안전값으로 정규화
        const parsed = JSON.parse(raw) as Partial<AdventureProgress>;
        const normalized: AdventureProgress = {
          ...DEFAULT_PROGRESS,
          ...parsed,
          defeatedNpcs: Array.isArray(parsed.defeatedNpcs)
            ? parsed.defeatedNpcs.filter((x): x is string => typeof x === 'string')
            : [],
          battlesWon: typeof parsed.battlesWon === 'number' ? parsed.battlesWon : 0,
          battlesLost: typeof parsed.battlesLost === 'number' ? parsed.battlesLost : 0,
          achievements: Array.isArray(parsed.achievements)
            ? parsed.achievements.filter((x): x is string => typeof x === 'string')
            : [],
        };
        // 업적 소급 반영 — 업적 도입 전 저장소도 이미 달성한 조건은 잠금 해제 (토스트 없이 조용히)
        const backfill = newlyUnlockedAdv(normalized);
        setProgress(
          backfill.length
            ? { ...normalized, achievements: [...normalized.achievements, ...backfill] }
            : normalized
        );
      }
    } catch (e) {
      console.error('어드벤처 진행 로드 실패:', e);
    }
    setLoaded(true);
  }, []);

  useEffect(() => {
    if (!loaded) return;
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(progress));
    } catch (e) {
      console.error('어드벤처 진행 저장 실패:', e);
    }
  }, [progress, loaded]);

  const openAdventure = useCallback(() => setOpen(true), []);
  const closeAdventure = useCallback(() => setOpen(false), []);
  const recordBattle = useCallback((npcId: string, won: boolean): string[] => {
    const { next, unlocked } = applyBattle(ref.current, npcId, won);
    setProgress(next);
    return unlocked;
  }, []);
  const resetAdventure = useCallback(() => setProgress(DEFAULT_PROGRESS), []);

  return (
    <AdventureContext.Provider
      value={{ loaded, progress, open, openAdventure, closeAdventure, recordBattle, resetAdventure }}
    >
      {children}
    </AdventureContext.Provider>
  );
}

export function useAdventure(): AdventureContextValue {
  const ctx = useContext(AdventureContext);
  if (!ctx) throw new Error('useAdventure must be used within AdventureProvider');
  return ctx;
}
