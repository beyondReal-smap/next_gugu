// 모드별 아이콘/틴트 — UI 레이어 전용 (lib/modes.ts는 React 무의존 유지)
import { BookOpen, Timer, Zap, Heart, Puzzle, Scale, LucideIcon } from 'lucide-react';
import { GameMode } from '@/lib/types';

export const MODE_ICONS: Record<GameMode, LucideIcon> = {
  practice: BookOpen,
  timeAttack: Timer,
  challenge: Zap,
  survival: Heart,
  missing: Puzzle,
  truefalse: Scale,
};

// Tailwind JIT 감지를 위해 완성된 클래스 문자열만 사용
export const MODE_TINT: Record<GameMode, { text: string; bg: string }> = {
  practice: { text: 'text-accent', bg: 'bg-accent/15' },
  timeAttack: { text: 'text-sky-500', bg: 'bg-sky-500/15' },
  challenge: { text: 'text-amber-500', bg: 'bg-amber-500/15' },
  survival: { text: 'text-rose-500', bg: 'bg-rose-500/15' },
  missing: { text: 'text-violet-500', bg: 'bg-violet-500/15' },
  truefalse: { text: 'text-emerald-500', bg: 'bg-emerald-500/15' },
};
