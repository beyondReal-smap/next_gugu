"use client";
import React from 'react';
import { motion } from 'framer-motion';
import { Star } from 'lucide-react';
import { LevelInfo, levelTitle } from './levelSystem';

interface XPBarProps {
  levelInfo: LevelInfo;
  compact?: boolean;
}

const XPBar: React.FC<XPBarProps> = ({ levelInfo, compact = false }) => {
  const { level, currentLevelXp, xpForNextLevel, progress } = levelInfo;

  return (
    <div className="flex items-center gap-3">
      {/* 레벨 배지 */}
      <motion.div
        initial={{ scale: 0.6, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ type: 'spring', stiffness: 400, damping: 16 }}
        className="relative flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl
                   bg-gradient-to-br from-clay-yellow-light to-clay-yellow
                   border-[3px] border-white shadow-clay-yellow"
      >
        <Star className="absolute h-12 w-12 text-white/30" fill="currentColor" />
        <span className="num-display relative text-xl font-extrabold text-white drop-shadow">
          {level}
        </span>
      </motion.div>

      {/* 진행 게이지 */}
      <div className="min-w-0 flex-1">
        {!compact && (
          <div className="mb-1 flex items-center justify-between">
            <span className="truncate font-suite text-sm font-extrabold text-clay-blue-dark">
              Lv.{level} · {levelTitle(level)}
            </span>
            <span className="num-display shrink-0 text-xs font-bold text-clay-muted">
              {currentLevelXp}/{xpForNextLevel} XP
            </span>
          </div>
        )}
        <div className="relative h-4 w-full overflow-hidden rounded-full border-2 border-white bg-clay-bg shadow-clay-pressed">
          <motion.div
            className="h-full rounded-full gauge-stripes bg-gradient-to-r from-clay-blue to-clay-purple"
            initial={false}
            animate={{ width: `${Math.max(4, progress * 100)}%` }}
            transition={{ type: 'spring', stiffness: 120, damping: 18 }}
          />
        </div>
      </div>
    </div>
  );
};

export default XPBar;
