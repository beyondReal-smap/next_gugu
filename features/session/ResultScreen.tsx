"use client";
import React, { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { Check, Gauge, Sparkles, RotateCcw, X, Flame, Trophy } from 'lucide-react';
import { SessionResult, CommitResult } from '@/lib/types';
import { MODES } from '@/lib/modes';
import { useGame } from '@/lib/state/GameProvider';
import { Button } from '@/components/ui/Button';
import { Stars } from '@/components/ui/Stars';
import { Confetti } from '@/components/feedback/Confetti';
import { LevelUpOverlay } from '@/components/feedback/LevelUpOverlay';
import { AchievementToast } from '@/components/feedback/AchievementToast';
import { playComplete } from '@/lib/sound';

interface ResultScreenProps {
  result: SessionResult;
  commit: CommitResult;
  wrongCount: number;
  onAgain: () => void;
  onRetryWrong: () => void;
  onClose: () => void;
}

export function ResultScreen({ result, commit, wrongCount, onAgain, onRetryWrong, onClose }: ResultScreenProps) {
  const { state } = useGame();
  const def = MODES[result.mode];
  const total = result.answers.length;
  const correct = total - wrongCount;
  const accuracy = total ? Math.round((correct / total) * 100) : 0;
  const avgMs = total ? Math.round(result.answers.reduce((s, a) => s + a.ms, 0) / total) : 0;
  // commit 반영 후 상태이므로 bestScores는 항상 최신 기록
  const best = commit.score != null ? Math.max(state.bestScores[result.mode] ?? 0, commit.score) : null;

  const [confetti, setConfetti] = useState(0);
  const [levelUp, setLevelUp] = useState(false);

  useEffect(() => {
    playComplete();
    if (accuracy >= 80 || commit.isNewBest) setConfetti((n) => n + 1);
    if (commit.leveledUp) {
      const t = setTimeout(() => setLevelUp(true), 600);
      return () => clearTimeout(t);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const headline = def.scored
    ? commit.isNewBest ? '신기록 달성! 🏆' : '수고했어요!'
    : accuracy >= 95 ? '완벽해요! 🎯' : accuracy >= 70 ? '잘했어요!' : '좋은 시도예요!';

  return (
    <div className="flex min-h-dvh flex-col px-6 py-10">
      <Confetti trigger={confetti} />
      <AchievementToast ids={commit.unlocked} token={confetti + 1} />
      <LevelUpOverlay show={levelUp} level={commit.newLevel} onClose={() => setLevelUp(false)} />

      <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} className="flex-1">
        <div className="mb-1 text-sm font-bold text-text-muted">{def.name} 완료</div>
        <h1 className="mb-6 text-3xl font-extrabold text-text">{headline}</h1>

        {/* 점수형 모드(챌린지/서바이벌) — 점수 히어로 */}
        {def.scored && commit.score != null && (
          <motion.div
            initial={{ scale: 0.94, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            transition={{ type: 'spring', stiffness: 260, damping: 20, delay: 0.1 }}
            className="mb-4 rounded-3xl border border-border bg-surface p-6 text-center"
          >
            {commit.isNewBest && (
              <div className="mb-2 inline-flex items-center gap-1.5 rounded-full bg-warning/15 px-3 py-1 text-xs font-extrabold text-warning">
                <Trophy className="h-3.5 w-3.5" /> 신기록!
              </div>
            )}
            <div className="num text-6xl font-extrabold text-text">
              {commit.score}<span className="ml-1 text-xl text-text-muted">점</span>
            </div>
            <div className="num mt-2 text-sm font-bold text-text-muted">최고 기록 {best}점</div>
          </motion.div>
        )}

        {/* 핵심 수치 */}
        <div className="mb-4 grid grid-cols-2 gap-3">
          <Stat icon={<Check className="h-5 w-5 text-success" />} label="정확도" value={`${accuracy}%`} />
          <Stat icon={<Gauge className="h-5 w-5 text-accent" />} label="평균 속도" value={`${(avgMs / 1000).toFixed(1)}초`} />
          <Stat icon={<Sparkles className="h-5 w-5 text-warning" />} label="획득 XP" value={`+${commit.xpEarned}`} />
          <Stat icon={<Flame className="h-5 w-5 text-danger" />} label="최고 콤보" value={`${result.maxCombo}`} />
        </div>

        {/* 별점 (단 집중 세션) */}
        {commit.table != null && (
          <div className="mb-4 flex items-center justify-between rounded-2xl border border-border bg-surface px-5 py-4">
            <div>
              <div className="text-sm font-bold text-text">{commit.table}단 마스터리</div>
              {commit.improvedStars && <div className="text-xs font-bold text-accent">새 기록 달성!</div>}
            </div>
            <Stars value={commit.newStars} size={26} />
          </div>
        )}

        {commit.goalReached && (
          <div className="mb-4 rounded-2xl bg-success/15 px-5 py-3 text-center text-sm font-bold text-success">
            🎉 오늘의 목표를 달성했어요!
          </div>
        )}
      </motion.div>

      <div className="flex flex-col gap-2.5">
        {def.kind === 'fixed' && wrongCount > 0 && (
          <Button variant="surface" size="lg" onClick={onRetryWrong}>
            <RotateCcw className="h-5 w-5" /> 틀린 문제만 다시 ({wrongCount})
          </Button>
        )}
        <Button variant="primary" size="lg" onClick={onAgain}>한 판 더</Button>
        <Button variant="ghost" size="md" onClick={onClose}><X className="h-4 w-4" /> 닫기</Button>
      </div>
    </div>
  );
}

function Stat({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="rounded-2xl border border-border bg-surface px-4 py-3">
      <div className="mb-1 flex items-center gap-1.5 text-xs font-bold text-text-muted">{icon}{label}</div>
      <div className="num text-2xl font-extrabold text-text">{value}</div>
    </div>
  );
}
