"use client";
import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Flame } from 'lucide-react';

interface ComboGaugeProps {
  combo: number;
}

function comboColor(combo: number): { from: string; to: string; text: string } {
  if (combo >= 20) return { from: 'from-clay-pink', to: 'to-clay-yellow', text: 'text-clay-pink-dark' };
  if (combo >= 10) return { from: 'from-orange-400', to: 'to-clay-pink', text: 'text-orange-500' };
  if (combo >= 5) return { from: 'from-clay-yellow', to: 'to-orange-400', text: 'text-clay-yellow-dark' };
  return { from: 'from-clay-blue', to: 'to-clay-purple', text: 'text-clay-blue' };
}

const ComboGauge: React.FC<ComboGaugeProps> = ({ combo }) => {
  const show = combo >= 2;
  const c = comboColor(combo);
  // 5콤보 주기로 게이지가 차오름
  const cycle = ((combo - 1) % 5) + 1;
  const progress = cycle / 5;
  const bonus = Math.min(15, Math.max(0, combo - 2) * 2);

  return (
    <AnimatePresence>
      {show && (
        <motion.div
          initial={{ opacity: 0, y: -8, scale: 0.9 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          exit={{ opacity: 0, y: -8, scale: 0.9 }}
          transition={{ type: 'spring', stiffness: 400, damping: 22 }}
          className="mb-2 flex items-center gap-2"
        >
          <motion.div
            key={combo}
            initial={{ scale: 1 }}
            animate={{ scale: [1, 1.25, 1] }}
            transition={{ duration: 0.3 }}
            className={`flex items-center gap-1 ${c.text}`}
          >
            <Flame className="h-5 w-5" fill="currentColor" />
            <span className="num-display text-lg font-extrabold">{combo}</span>
            <span className="font-suite text-xs font-extrabold">COMBO</span>
          </motion.div>

          <div className="relative h-3 flex-1 overflow-hidden rounded-full border-2 border-white bg-clay-bg shadow-clay-pressed">
            <motion.div
              className={`h-full rounded-full gauge-stripes bg-gradient-to-r ${c.from} ${c.to}`}
              animate={{ width: `${progress * 100}%` }}
              transition={{ type: 'spring', stiffness: 200, damping: 20 }}
            />
          </div>

          {bonus > 0 && (
            <motion.span
              key={`b-${bonus}`}
              initial={{ scale: 0.6, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              className="num-display shrink-0 text-xs font-extrabold text-clay-mint-dark"
            >
              +{bonus}XP
            </motion.span>
          )}
        </motion.div>
      )}
    </AnimatePresence>
  );
};

export default ComboGauge;
