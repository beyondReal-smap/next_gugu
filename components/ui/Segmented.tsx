"use client";
import React from 'react';
import { motion } from 'framer-motion';

interface Option<T extends string> {
  value: T;
  label: string;
}
interface SegmentedProps<T extends string> {
  options: Option<T>[];
  value: T;
  onChange: (v: T) => void;
  className?: string;
}

export function Segmented<T extends string>({ options, value, onChange, className = '' }: SegmentedProps<T>) {
  return (
    <div className={`relative flex rounded-2xl bg-surface-2 p-1 ${className}`}>
      {options.map((opt) => {
        const active = opt.value === value;
        return (
          <button
            key={opt.value}
            onClick={() => onChange(opt.value)}
            className={`relative z-10 flex-1 rounded-xl py-2 text-sm font-bold transition-colors
              ${active ? 'text-accent-fg' : 'text-text-muted'}`}
          >
            {active && (
              <motion.span
                layoutId="segmented-active"
                className="absolute inset-0 -z-10 rounded-xl bg-accent"
                transition={{ type: 'spring', stiffness: 400, damping: 32 }}
              />
            )}
            {opt.label}
          </button>
        );
      })}
    </div>
  );
}
