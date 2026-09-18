"use client";
import React from 'react';
import { motion } from 'framer-motion';
import { Delete } from 'lucide-react';
import { playTap } from '@/lib/sound';

interface KeypadProps {
  onInput: (n: number) => void;
  onDelete: () => void;
  onSubmit: () => void;
  canSubmit: boolean;
}

export function Keypad({ onInput, onDelete, onSubmit, canSubmit }: KeypadProps) {
  const press = (fn: () => void) => () => { playTap(); fn(); };
  const Key = ({ children, onClick, className = '', label }: { children: React.ReactNode; onClick: () => void; className?: string; label: string }) => (
    <motion.button
      whileTap={{ scale: 0.94 }}
      onClick={onClick}
      aria-label={label}
      className={`flex h-16 touch-manipulation items-center justify-center rounded-2xl bg-surface-2 text-2xl font-bold text-text
        transition-colors hover:bg-border active:bg-border ${className}`}
    >
      {children}
    </motion.button>
  );

  return (
    <div className="grid grid-cols-3 gap-2.5 touch-manipulation">
      {[1, 2, 3, 4, 5, 6, 7, 8, 9].map((n) => (
        <Key key={n} label={`숫자 ${n}`} onClick={press(() => onInput(n))}><span className="num">{n}</span></Key>
      ))}
      <Key label="지우기" onClick={press(onDelete)} className="!text-text-muted"><Delete className="h-6 w-6" /></Key>
      <Key label="숫자 0" onClick={press(() => onInput(0))}><span className="num">0</span></Key>
      <motion.button
        whileTap={{ scale: 0.94 }}
        onClick={press(onSubmit)}
        disabled={!canSubmit}
        aria-label="확인"
        className="flex h-16 touch-manipulation items-center justify-center rounded-2xl bg-accent text-lg font-bold text-accent-fg
          transition-[filter] hover:brightness-110 disabled:opacity-40 disabled:pointer-events-none"
      >
        확인
      </motion.button>
    </div>
  );
}
