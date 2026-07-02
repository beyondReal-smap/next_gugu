"use client";
import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Shuffle, Trophy, Play } from 'lucide-react';
import { GameMode } from '@/lib/types';
import { MODE_LIST, MODES } from '@/lib/modes';
import { MODE_ICONS, MODE_TINT } from '@/components/modeIcons';
import { useGame } from '@/lib/state/GameProvider';
import { useSession } from '@/lib/state/SessionProvider';
import { Button } from '@/components/ui/Button';
import { Stars } from '@/components/ui/Stars';
import { MIN_TABLE, MAX_TABLE } from '@/lib/problems';

const TABLES = Array.from({ length: MAX_TABLE - MIN_TABLE + 1 }, (_, i) => MIN_TABLE + i);

export function Learn() {
  const { state } = useGame();
  const { start } = useSession();
  const [mode, setMode] = useState<GameMode>('practice');
  const def = MODES[mode];

  const totalStars = Object.values(state.tableMastery).reduce((a, m) => a + m.stars, 0);

  return (
    <div className="mx-auto max-w-md px-5 pt-6 animate-fade-in">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-2xl font-extrabold text-text">학습</h1>
        <div className="flex items-center gap-1.5 rounded-full bg-warning/12 px-3 py-1.5 text-warning">
          <Trophy className="h-4 w-4" />
          <span className="num text-sm font-extrabold">{totalStars}/24</span>
        </div>
      </div>

      {/* 모드 카드 */}
      <div className="mb-3 grid grid-cols-2 gap-2.5">
        {MODE_LIST.map((m) => {
          const active = m.id === mode;
          const Icon = MODE_ICONS[m.id];
          const tint = MODE_TINT[m.id];
          const best = m.scored ? state.bestScores[m.id] ?? 0 : 0;
          return (
            <motion.button
              key={m.id}
              whileTap={{ scale: 0.97 }}
              onClick={() => setMode(m.id)}
              aria-pressed={active}
              className={`relative rounded-2xl border p-3.5 text-left transition-colors
                ${active ? 'border-accent bg-accent/10' : 'border-border bg-surface hover:bg-surface-2'}`}
            >
              <span className={`mb-2 flex h-9 w-9 items-center justify-center rounded-xl ${tint.bg} ${tint.text}`}>
                <Icon className="h-5 w-5" strokeWidth={2.4} />
              </span>
              <span className="block text-sm font-extrabold text-text">{m.name}</span>
              <span className="block text-xs text-text-muted">{m.tagline}</span>
              {m.scored && best > 0 && (
                <span className="num absolute right-2.5 top-2.5 rounded-full bg-warning/15 px-2 py-0.5 text-[10px] font-extrabold text-warning">
                  최고 {best}
                </span>
              )}
            </motion.button>
          );
        })}
      </div>

      <p className="mb-4 text-sm text-text-muted">{def.detail}</p>

      <AnimatePresence mode="wait">
        {def.supportsTable ? (
          <motion.div key="tables" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}>
            {/* 혼합 랜덤 */}
            <button
              onClick={() => start(mode, null)}
              className="mb-3 flex w-full items-center gap-3 rounded-2xl border border-border bg-surface px-5 py-4 text-left transition-colors hover:bg-surface-2"
            >
              <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-accent/15 text-accent">
                <Shuffle className="h-5 w-5" />
              </span>
              <span>
                <span className="block font-bold text-text">전체 랜덤</span>
                <span className="block text-sm text-text-muted">2~9단을 골고루 섞어서</span>
              </span>
            </button>

            {/* 단 맵 */}
            <div className="mb-2 text-sm font-bold text-text-muted">단 선택</div>
            <div className="grid grid-cols-2 gap-3 pb-4">
              {TABLES.map((t, i) => {
                const m = state.tableMastery[t];
                const stars = m?.stars ?? 0;
                return (
                  <motion.button
                    key={t}
                    initial={{ opacity: 0, scale: 0.92 }}
                    animate={{ opacity: 1, scale: 1 }}
                    transition={{ delay: i * 0.03 }}
                    whileTap={{ scale: 0.96 }}
                    onClick={() => start(mode, t)}
                    className="flex flex-col items-start gap-2 rounded-2xl border border-border bg-surface p-4 text-left transition-colors hover:bg-surface-2"
                  >
                    <span className="num text-3xl font-extrabold text-text">
                      {t}<span className="text-lg text-text-muted">단</span>
                    </span>
                    <Stars value={stars} size={16} />
                  </motion.button>
                );
              })}
            </div>
          </motion.div>
        ) : (
          /* 전체 랜덤 전용 모드 (챌린지/서바이벌) — 기록 + 시작 CTA */
          <motion.div
            key="challenge"
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            className="rounded-3xl border border-border bg-surface p-5 pb-5 mb-4"
          >
            <div className="mb-4 flex items-center justify-between">
              <div>
                <div className="text-sm font-bold text-text-muted">내 최고 기록</div>
                <div className="num text-4xl font-extrabold text-text">
                  {(state.bestScores[mode] ?? 0) > 0 ? `${state.bestScores[mode]}점` : '—'}
                </div>
              </div>
              {(() => {
                const Icon = MODE_ICONS[mode];
                const tint = MODE_TINT[mode];
                return (
                  <span className={`flex h-14 w-14 items-center justify-center rounded-2xl ${tint.bg} ${tint.text}`}>
                    <Icon className="h-7 w-7" strokeWidth={2.4} />
                  </span>
                );
              })()}
            </div>
            <Button variant="primary" size="lg" className="w-full" onClick={() => start(mode, null)}>
              <Play className="h-5 w-5" fill="currentColor" /> 도전 시작
            </Button>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
