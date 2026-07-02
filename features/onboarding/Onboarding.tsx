"use client";
import React from 'react';
import { motion } from 'framer-motion';
import { Flame, TrendingUp, Star, Sparkles } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { useGame } from '@/lib/state/GameProvider';
import { useSession } from '@/lib/state/SessionProvider';
import { initSound } from '@/lib/sound';

const POINTS = [
  { icon: TrendingUp, title: '레벨업', desc: '정답마다 XP를 모아 레벨을 올려요' },
  { icon: Flame, title: '스트릭', desc: '매일 학습하면 연속 기록이 쌓여요' },
  { icon: Star, title: '마스터리', desc: '단별로 별을 모아 완전 정복해요' },
];

export function Onboarding() {
  const { setOnboarded } = useGame();
  const { start } = useSession();

  const begin = () => {
    initSound(); // 사용자 제스처에서 오디오 활성화
    setOnboarded(true);
    start('practice', 2); // 즉시 첫 세션 (쉬운 2단)
  };

  return (
    <div className="flex min-h-dvh flex-col justify-between px-6 py-12">
      <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }}>
        <div className="mb-2 inline-flex items-center gap-1.5 rounded-full bg-accent/15 px-3 py-1 text-xs font-bold text-accent">
          <Sparkles className="h-3.5 w-3.5" /> 매일 1분 구구단
        </div>
        <h1 className="text-4xl font-extrabold leading-tight text-text">
          구구단,<br />게임처럼<br /><span className="text-accent">매일 성장</span>해요
        </h1>
        <p className="mt-3 text-text-muted">레벨 · 스트릭 · 마스터리로 자연스럽게 익히는 모던한 구구단 학습.</p>
      </motion.div>

      <div className="my-8 space-y-3">
        {POINTS.map((p, i) => (
          <motion.div
            key={p.title}
            initial={{ opacity: 0, x: -12 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ delay: 0.1 + i * 0.08 }}
            className="flex items-center gap-4 rounded-2xl border border-border bg-surface px-4 py-3.5"
          >
            <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-accent/15 text-accent">
              <p.icon className="h-5 w-5" strokeWidth={2.4} />
            </div>
            <div>
              <div className="font-bold text-text">{p.title}</div>
              <div className="text-sm text-text-muted">{p.desc}</div>
            </div>
          </motion.div>
        ))}
      </div>

      <Button variant="primary" size="lg" onClick={begin} className="w-full">바로 시작하기</Button>
    </div>
  );
}
