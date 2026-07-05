"use client";
import React, { useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import * as Icons from 'lucide-react';
import { getAchievement } from '@/lib/achievements';

interface AchievementToastProps {
  ids: string[];        // 표시할 업적 id 목록 (변경 시 큐잉)
  token: number;        // 트리거 토큰 (같은 ids라도 재표시)
  // id → 업적 정의 커스텀 조회 (기본: 코어 업적 레지스트리). 어드벤처 등 별도 업적 시스템 겸용
  resolve?: (id: string) => { name: string; icon: string } | undefined;
}

export function AchievementToast({ ids, token, resolve }: AchievementToastProps) {
  const [queue, setQueue] = useState<string[]>([]);
  const [current, setCurrent] = useState<string | null>(null);

  useEffect(() => {
    if (!ids || ids.length === 0) return;
    setQueue((q) => [...q, ...ids]);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  useEffect(() => {
    if (current || queue.length === 0) return;
    const [next, ...rest] = queue;
    setCurrent(next);
    setQueue(rest);
    const t = setTimeout(() => setCurrent(null), 2600);
    return () => clearTimeout(t);
  }, [queue, current]);

  const def = current ? (resolve ?? getAchievement)(current) : undefined;
  const Icon = def ? ((Icons as unknown as Record<string, Icons.LucideIcon>)[def.icon] ?? Icons.Award) : Icons.Award;

  return (
    <div className="pointer-events-none fixed inset-x-0 top-4 z-[95] flex justify-center px-4">
      <AnimatePresence>
        {def && (
          <motion.div
            key={current}
            initial={{ opacity: 0, y: -30, scale: 0.9 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -30, scale: 0.9 }}
            transition={{ type: 'spring', stiffness: 360, damping: 24 }}
            className="flex items-center gap-3 rounded-2xl border border-border bg-surface px-4 py-3 shadow-xl"
          >
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-accent/15 text-accent">
              <Icon className="h-5 w-5" strokeWidth={2.4} />
            </div>
            <div className="min-w-0">
              <div className="text-[11px] font-bold uppercase tracking-wide text-accent">업적 달성</div>
              <div className="text-sm font-bold text-text">{def.name}</div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
