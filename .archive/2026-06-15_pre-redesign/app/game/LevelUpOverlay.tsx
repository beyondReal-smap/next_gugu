"use client";
import React, { useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Star, Sparkles } from 'lucide-react';
import { levelTitle } from './levelSystem';

interface LevelUpOverlayProps {
  show: boolean;
  level: number;
  onClose: () => void;
}

const LevelUpOverlay: React.FC<LevelUpOverlayProps> = ({ show, level, onClose }) => {
  useEffect(() => {
    if (!show) return;
    const t = setTimeout(onClose, 2400);
    return () => clearTimeout(t);
  }, [show, onClose]);

  return (
    <AnimatePresence>
      {show && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={onClose}
          className="fixed inset-0 z-[90] flex items-center justify-center bg-clay-blue-dark/40 backdrop-blur-sm"
        >
          {/* 방사형 빛 */}
          <motion.div
            initial={{ scale: 0, opacity: 0 }}
            animate={{ scale: 1.4, opacity: [0, 0.5, 0.3], rotate: 360 }}
            transition={{ duration: 2.4, ease: 'easeOut' }}
            className="absolute h-80 w-80 rounded-full bg-gradient-to-r from-clay-yellow via-clay-pink to-clay-purple blur-3xl"
          />

          <motion.div
            initial={{ scale: 0.4, y: 30, opacity: 0 }}
            animate={{ scale: 1, y: 0, opacity: 1 }}
            exit={{ scale: 0.6, opacity: 0 }}
            transition={{ type: 'spring', stiffness: 300, damping: 18 }}
            className="relative flex flex-col items-center gap-2 rounded-clay-lg border-[4px] border-white
                       bg-white px-10 py-8 shadow-clay"
          >
            <motion.div
              animate={{ rotate: [-8, 8, -8] }}
              transition={{ duration: 0.8, repeat: Infinity, ease: 'easeInOut' }}
              className="flex items-center gap-1"
            >
              <Sparkles className="h-6 w-6 text-clay-yellow" fill="currentColor" />
              <span className="font-suite text-lg font-extrabold text-clay-pink">LEVEL UP!</span>
              <Sparkles className="h-6 w-6 text-clay-yellow" fill="currentColor" />
            </motion.div>

            <motion.div
              initial={{ scale: 0 }}
              animate={{ scale: [0, 1.3, 1] }}
              transition={{ delay: 0.2, duration: 0.6, ease: 'easeOut' }}
              className="relative flex h-28 w-28 items-center justify-center"
            >
              <Star className="absolute h-28 w-28 text-clay-yellow" fill="currentColor" />
              <span className="num-display relative text-5xl font-extrabold text-white drop-shadow-md">
                {level}
              </span>
            </motion.div>

            <span className="font-suite text-xl font-extrabold text-clay-blue-dark">
              {levelTitle(level)}
            </span>
            <span className="font-suite text-sm font-bold text-clay-muted">레벨 {level} 달성!</span>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
};

export default LevelUpOverlay;
