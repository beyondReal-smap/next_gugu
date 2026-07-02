"use client";
import React from 'react';
import { motion } from 'framer-motion';

interface ProgressRingProps {
  value: number;        // 0~1
  size?: number;
  stroke?: number;
  className?: string;
  children?: React.ReactNode;
  trackClass?: string;  // 트랙 색 (text-* 사용)
  barClass?: string;    // 진행 색
}

// SVG 원형 진행 링 (currentColor 기반으로 테마 대응)
export function ProgressRing({
  value,
  size = 120,
  stroke = 10,
  className = '',
  children,
  trackClass = 'text-surface-2',
  barClass = 'text-accent',
}: ProgressRingProps) {
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const clamped = Math.max(0, Math.min(1, value));
  return (
    <div className={`relative inline-flex items-center justify-center ${className}`} style={{ width: size, height: size }}>
      <svg width={size} height={size} className="-rotate-90">
        <circle
          cx={size / 2} cy={size / 2} r={r} fill="none" strokeWidth={stroke}
          className={trackClass} stroke="currentColor"
        />
        <motion.circle
          cx={size / 2} cy={size / 2} r={r} fill="none" strokeWidth={stroke} strokeLinecap="round"
          className={barClass} stroke="currentColor"
          strokeDasharray={c}
          initial={{ strokeDashoffset: c }}
          animate={{ strokeDashoffset: c * (1 - clamped) }}
          transition={{ type: 'spring', stiffness: 90, damping: 18 }}
        />
      </svg>
      {children && <div className="absolute inset-0 flex flex-col items-center justify-center">{children}</div>}
    </div>
  );
}
