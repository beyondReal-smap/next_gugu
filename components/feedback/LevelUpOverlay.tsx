"use client";
import React, { useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { ChevronsUp } from 'lucide-react';
import { levelTitle } from '@/lib/level';

interface LevelUpOverlayProps {
  show: boolean;
  level: number;
  onClose: () => void;
}

// 미니멀 모던 레벨업 연출
export function LevelUpOverlay({ show, level, onClose }: LevelUpOverlayProps) {
  useEffect(() => {
    if (!show) return;
    const t = setTimeout(onClose, 2200);
    return () => clearTimeout(t);
  }, [show, onClose]);

  return (
    <AnimatePresence>
      {show && (
        <motion.div
          initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
          onClick={onClose}
          className="fixed inset-0 z-[90] flex items-center justify-center bg-bg/70 backdrop-blur-md"
        >
          <motion.div
            initial={{ scale: 0.8, y: 20, opacity: 0 }}
            animate={{ scale: 1, y: 0, opacity: 1 }}
            exit={{ scale: 0.9, opacity: 0 }}
            transition={{ type: 'spring', stiffness: 320, damping: 22 }}
            className="flex flex-col items-center gap-3 rounded-3xl border border-border bg-surface px-10 py-8 text-center shadow-2xl"
          >
            <motion.div
              initial={{ y: 6 }} animate={{ y: [-2, -8, -2] }}
              transition={{ duration: 1.2, repeat: Infinity, ease: 'easeInOut' }}
              className="flex h-16 w-16 items-center justify-center rounded-2xl bg-accent/15 text-accent"
            >
              <ChevronsUp className="h-9 w-9" strokeWidth={2.5} />
            </motion.div>
            <div className="text-sm font-bold uppercase tracking-widest text-accent">Level Up</div>
            <div className="num text-5xl font-extrabold text-text">Lv.{level}</div>
            <div className="text-sm font-bold text-text-muted">{levelTitle(level)}</div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
