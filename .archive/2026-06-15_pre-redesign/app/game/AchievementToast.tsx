"use client";
import React, { useEffect, useState, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Sparkles, BookOpen, Medal, Zap, Flame, Crown, Trophy,
  GraduationCap, Star, Award, TrendingUp, Rocket, CalendarCheck, Heart,
  type LucideIcon,
} from 'lucide-react';
import { AchievementDef } from './achievements';

const ICONS: Record<string, LucideIcon> = {
  Sparkles, BookOpen, Medal, Zap, Flame, Crown, Trophy,
  GraduationCap, Star, Award, TrendingUp, Rocket, CalendarCheck, Heart,
};

interface AchievementToastProps {
  incoming: AchievementDef[] | null; // 새로 해금된 업적 (변경 시 큐에 추가)
}

const AchievementToast: React.FC<AchievementToastProps> = ({ incoming }) => {
  const [queue, setQueue] = useState<AchievementDef[]>([]);
  const [current, setCurrent] = useState<AchievementDef | null>(null);
  const lastRef = useRef<AchievementDef[] | null>(null);

  // incoming 변경 시 큐에 추가
  useEffect(() => {
    if (!incoming || incoming === lastRef.current || incoming.length === 0) return;
    lastRef.current = incoming;
    setQueue((q) => [...q, ...incoming]);
  }, [incoming]);

  // 큐 소비
  useEffect(() => {
    if (current || queue.length === 0) return;
    const [next, ...rest] = queue;
    setCurrent(next);
    setQueue(rest);
    const t = setTimeout(() => setCurrent(null), 2800);
    return () => clearTimeout(t);
  }, [queue, current]);

  const Icon = current ? ICONS[current.icon] ?? Award : Award;

  return (
    <div className="pointer-events-none fixed inset-x-0 top-3 z-[95] flex justify-center px-4">
      <AnimatePresence>
        {current && (
          <motion.div
            key={current.id}
            initial={{ opacity: 0, y: -40, scale: 0.8 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -40, scale: 0.8 }}
            transition={{ type: 'spring', stiffness: 360, damping: 22 }}
            className="flex items-center gap-3 rounded-clay-sm border-[3px] border-white bg-white px-4 py-2.5 shadow-clay"
          >
            <motion.div
              animate={{ rotate: [0, -12, 12, -8, 0], scale: [1, 1.15, 1] }}
              transition={{ duration: 0.7 }}
              className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl border-2 border-white"
              style={{ backgroundColor: `${current.color}22` }}
            >
              <Icon className="h-6 w-6" style={{ color: current.color }} fill="currentColor" />
            </motion.div>
            <div className="min-w-0">
              <div className="flex items-center gap-1.5">
                <span className="font-suite text-[11px] font-extrabold text-clay-yellow-dark">업적 달성!</span>
                <Sparkles className="h-3 w-3 text-clay-yellow" fill="currentColor" />
              </div>
              <div className="font-suite text-sm font-extrabold text-clay-ink">{current.name}</div>
              <div className="font-suite text-[11px] font-medium text-clay-muted">{current.description}</div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};

export default AchievementToast;
