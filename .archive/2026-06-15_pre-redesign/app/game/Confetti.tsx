"use client";
import React, { useEffect, useState } from 'react';
import { motion, AnimatePresence, useReducedMotion } from 'framer-motion';

interface ConfettiProps {
  trigger: number;          // 값이 바뀔 때마다 1회 폭발
  intensity?: number;       // 파티클 수
  origin?: { x: number; y: number }; // 0~1 비율 (기본 중앙 상단)
}

const COLORS = ['#2563EB', '#F59E0B', '#EC4899', '#10B981', '#8B5CF6', '#60A5FA', '#FCD34D'];

interface Piece {
  id: number;
  x: number;       // 시작 x (vw)
  dx: number;      // 수평 이동량 (vw)
  dy: number;      // 낙하량 (vh)
  rotate: number;
  color: string;
  size: number;
  shape: 'rect' | 'circle';
  delay: number;
  duration: number;
}

function makePieces(count: number, origin: { x: number; y: number }): Piece[] {
  const ox = origin.x * 100;
  const oy = origin.y * 100;
  return Array.from({ length: count }, (_, i) => {
    const spread = (Math.random() - 0.5) * 70;
    return {
      id: i,
      x: ox,
      dx: spread,
      dy: 40 + Math.random() * 60 + oy,
      rotate: Math.random() * 720 - 360,
      color: COLORS[Math.floor(Math.random() * COLORS.length)],
      size: 7 + Math.random() * 8,
      shape: Math.random() > 0.5 ? 'rect' : 'circle',
      delay: Math.random() * 0.15,
      duration: 1.1 + Math.random() * 0.8,
    };
  });
}

const Confetti: React.FC<ConfettiProps> = ({ trigger, intensity = 36, origin = { x: 0.5, y: 0.28 } }) => {
  const reduce = useReducedMotion();
  const [pieces, setPieces] = useState<Piece[]>([]);

  useEffect(() => {
    if (trigger <= 0 || reduce) return;
    setPieces(makePieces(intensity, origin));
    const t = setTimeout(() => setPieces([]), 2200);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [trigger]);

  return (
    <div className="pointer-events-none fixed inset-0 z-[80] overflow-hidden">
      <AnimatePresence>
        {pieces.map((p) => (
          <motion.div
            key={`${trigger}-${p.id}`}
            initial={{ top: `${origin.y * 100}vh`, left: `${p.x}vw`, opacity: 1, rotate: 0, scale: 1 }}
            animate={{
              top: `${p.dy}vh`,
              left: `${p.x + p.dx}vw`,
              opacity: [1, 1, 0],
              rotate: p.rotate,
              scale: [1, 1.1, 0.6],
            }}
            exit={{ opacity: 0 }}
            transition={{ duration: p.duration, delay: p.delay, ease: 'easeOut' }}
            style={{
              position: 'absolute',
              width: p.size,
              height: p.shape === 'rect' ? p.size * 0.6 : p.size,
              backgroundColor: p.color,
              borderRadius: p.shape === 'circle' ? '50%' : '2px',
            }}
          />
        ))}
      </AnimatePresence>
    </div>
  );
};

export default Confetti;
