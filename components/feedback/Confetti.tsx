"use client";
import React, { useEffect, useState } from 'react';
import { motion, AnimatePresence, useReducedMotion } from 'framer-motion';

interface ConfettiProps {
  trigger: number;
  intensity?: number;
}

// 모던·절제된 컨페티 (액센트 톤 중심)
const COLORS = ['#6366F1', '#818CF8', '#34D399', '#FBBF24', '#F472B6', '#22D3EE'];

interface Piece { id: number; x: number; dx: number; dy: number; rot: number; color: string; size: number; delay: number; dur: number; }

function make(count: number): Piece[] {
  return Array.from({ length: count }, (_, i) => ({
    id: i,
    x: 50 + (Math.random() - 0.5) * 30,
    dx: (Math.random() - 0.5) * 60,
    dy: 60 + Math.random() * 50,
    rot: Math.random() * 540 - 270,
    color: COLORS[i % COLORS.length],
    size: 6 + Math.random() * 6,
    delay: Math.random() * 0.12,
    dur: 1.1 + Math.random() * 0.6,
  }));
}

export function Confetti({ trigger, intensity = 28 }: ConfettiProps) {
  const reduce = useReducedMotion();
  const [pieces, setPieces] = useState<Piece[]>([]);
  useEffect(() => {
    if (trigger <= 0 || reduce) return;
    setPieces(make(intensity));
    const t = setTimeout(() => setPieces([]), 2000);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [trigger]);

  return (
    <div className="pointer-events-none fixed inset-0 z-[80] overflow-hidden">
      <AnimatePresence>
        {pieces.map((p) => (
          <motion.div
            key={`${trigger}-${p.id}`}
            initial={{ top: '30vh', left: `${p.x}vw`, opacity: 1, rotate: 0 }}
            animate={{ top: `${p.dy + 30}vh`, left: `${p.x + p.dx}vw`, opacity: [1, 1, 0], rotate: p.rot }}
            transition={{ duration: p.dur, delay: p.delay, ease: 'easeOut' }}
            style={{ position: 'absolute', width: p.size, height: p.size * 0.5, backgroundColor: p.color, borderRadius: 2 }}
          />
        ))}
      </AnimatePresence>
    </div>
  );
}
