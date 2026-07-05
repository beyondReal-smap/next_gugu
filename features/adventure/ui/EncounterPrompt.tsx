"use client";
// NPC 근접 시 대결 프롬프트 — 월드 하단 카드
import React from 'react';
import { motion } from 'framer-motion';
import { Swords, Lock, Crown, Gauge, Timer, Heart } from 'lucide-react';
import { BattleStyle, NpcDef } from '@/lib/adventure/types';
import { BATTLE_STYLE_NAME } from '@/lib/adventure/battle';
import { Button } from '@/components/ui/Button';

const STYLE_ICONS: Record<BattleStyle, typeof Swords> = { hp: Heart, speed: Gauge, counter: Timer };

interface EncounterPromptProps {
  npc: NpcDef;
  defeated: boolean;
  bossLocked: boolean; // 보스인데 부하를 아직 다 못 이김
  onBattle: () => void;
}

export function EncounterPrompt({ npc, defeated, bossLocked, onBattle }: EncounterPromptProps) {
  const isBoss = npc.kind === 'boss';
  return (
    <motion.div
      initial={{ opacity: 0, y: 24 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: 24 }}
      transition={{ type: 'spring', stiffness: 320, damping: 28 }}
      className="absolute inset-x-4 bottom-[calc(env(safe-area-inset-bottom)+10.5rem)] z-10 rounded-3xl border border-border bg-surface/95 p-4 shadow-xl backdrop-blur"
    >
      <div className="mb-3 flex items-center gap-3">
        {/* 아바타 — 월드 캐릭터와 같은 색 */}
        <span
          className="relative flex h-12 w-12 shrink-0 items-center justify-center rounded-full"
          style={{ backgroundColor: npc.color }}
        >
          <span className="flex gap-1.5">
            <span className="h-2 w-2 rounded-full bg-white shadow-[inset_0_0_0_2.5px_rgba(32,36,44,0.9)]" />
            <span className="h-2 w-2 rounded-full bg-white shadow-[inset_0_0_0_2.5px_rgba(32,36,44,0.9)]" />
          </span>
          {isBoss && <Crown className="absolute -top-2.5 h-5 w-5 text-warning" fill="currentColor" />}
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-1.5">
            <span className="truncate font-extrabold text-text">{npc.name}</span>
            {isBoss && (
              <span className="shrink-0 rounded-full bg-danger/15 px-2 py-0.5 text-[10px] font-extrabold text-danger">보스</span>
            )}
            <span className="num shrink-0 rounded-full bg-accent/12 px-2 py-0.5 text-[10px] font-extrabold text-accent">
              {npc.table}단
            </span>
            {!isBoss && (
              <span className="flex shrink-0 items-center gap-0.5 rounded-full bg-surface-2 px-2 py-0.5 text-[10px] font-extrabold text-text-muted">
                {React.createElement(STYLE_ICONS[npc.battle], { className: 'h-3 w-3' })}
                {BATTLE_STYLE_NAME[npc.battle]}
              </span>
            )}
          </div>
          <p className="truncate text-sm text-text-muted">
            {bossLocked
              ? '부하들을 모두 이기면 도전할 수 있어요'
              : defeated
                ? '이미 격파한 상대 — 연습 대결로 XP를 벌 수 있어요'
                : `“${npc.greeting}”`}
          </p>
        </div>
      </div>
      <Button
        variant={isBoss ? 'danger' : 'primary'}
        size="md"
        className="w-full"
        disabled={bossLocked}
        onClick={onBattle}
      >
        {bossLocked ? <Lock className="h-4 w-4" /> : <Swords className="h-5 w-5" />}
        {bossLocked ? '잠겨 있어요' : defeated ? '다시 대결하기' : '대결하기'}
      </Button>
    </motion.div>
  );
}
