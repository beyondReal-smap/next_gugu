"use client";
import React from 'react';
import { motion } from 'framer-motion';
import { Circle, X } from 'lucide-react';
import { playTap } from '@/lib/sound';

interface OxPadProps {
  onAnswer: (choice: boolean) => void; // true=O(맞는 식), false=X(틀린 식)
}

// OX 퀴즈 전용 입력 패드 — Keypad와 동일한 위치/역할
export function OxPad({ onAnswer }: OxPadProps) {
  const press = (v: boolean) => () => { playTap(); onAnswer(v); };
  return (
    <div className="grid grid-cols-2 gap-3">
      <motion.button
        whileTap={{ scale: 0.94 }}
        onClick={press(true)}
        aria-label="맞는 식"
        className="flex h-32 flex-col items-center justify-center gap-1.5 rounded-2xl bg-success/12 text-success
          transition-colors hover:bg-success/20 active:bg-success/25"
      >
        <Circle className="h-12 w-12" strokeWidth={3.2} />
        <span className="text-sm font-extrabold">맞아요</span>
      </motion.button>
      <motion.button
        whileTap={{ scale: 0.94 }}
        onClick={press(false)}
        aria-label="틀린 식"
        className="flex h-32 flex-col items-center justify-center gap-1.5 rounded-2xl bg-danger/12 text-danger
          transition-colors hover:bg-danger/20 active:bg-danger/25"
      >
        <X className="h-12 w-12" strokeWidth={3.2} />
        <span className="text-sm font-extrabold">아니에요</span>
      </motion.button>
    </div>
  );
}
