"use client";
import React, { useEffect, useState } from 'react';
import { motion, AnimatePresence, useReducedMotion } from 'framer-motion';

export type MascotMood = 'idle' | 'happy' | 'sad' | 'celebrate';

interface MascotProps {
  mood: MascotMood;       // 외부 트리거 (정답/오답/축하)
  message?: string;       // 말풍선 메시지
  size?: number;
}

// 구구단 친구 마스코트 — 클레이 느낌의 둥근 캐릭터
const Mascot: React.FC<MascotProps> = ({ mood, message, size = 96 }) => {
  const reduce = useReducedMotion();
  const [current, setCurrent] = useState<MascotMood>('idle');
  const [bubble, setBubble] = useState<string | undefined>(undefined);

  // 외부 mood 변경 시 반영하고, 표정은 잠시 후 idle로 복귀
  useEffect(() => {
    if (mood === 'idle') {
      setCurrent('idle');
      return;
    }
    setCurrent(mood);
    const t = setTimeout(() => setCurrent('idle'), mood === 'celebrate' ? 2200 : 1300);
    return () => clearTimeout(t);
  }, [mood]);

  // 말풍선 메시지는 잠깐 표시
  useEffect(() => {
    if (!message) return;
    setBubble(message);
    const t = setTimeout(() => setBubble(undefined), 1600);
    return () => clearTimeout(t);
  }, [message]);

  const bodyColor =
    current === 'sad' ? '#93C5FD' : current === 'celebrate' ? '#F59E0B' : '#2563EB';

  // 표정에 따른 본체 애니메이션
  const bodyAnim =
    reduce
      ? {}
      : current === 'happy'
      ? { y: [0, -16, 0, -6, 0], scale: [1, 1.06, 1] }
      : current === 'celebrate'
      ? { rotate: [0, -8, 8, -5, 5, 0], scale: [1, 1.12, 1] }
      : current === 'sad'
      ? { rotate: [0, -3, 3, -2, 0], y: [0, 2, 0] }
      : { y: [0, -7, 0] }; // idle 둥실

  const bodyTransition =
    current === 'idle'
      ? { duration: 3, repeat: Infinity, ease: 'easeInOut' as const }
      : { duration: current === 'celebrate' ? 0.8 : 0.6, ease: 'easeInOut' as const };

  return (
    <div className="relative flex flex-col items-center justify-end" style={{ width: size }}>
      {/* 말풍선 */}
      <AnimatePresence>
        {bubble && (
          <motion.div
            initial={{ opacity: 0, y: 8, scale: 0.8 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 8, scale: 0.8 }}
            transition={{ type: 'spring', stiffness: 400, damping: 20 }}
            className="absolute -top-12 whitespace-nowrap rounded-clay-sm border-2 border-white bg-white px-3 py-1.5
                       text-sm font-suite font-extrabold text-clay-blue shadow-clay-sm"
          >
            {bubble}
            <span className="absolute -bottom-1.5 left-1/2 h-3 w-3 -translate-x-1/2 rotate-45 border-b-2 border-r-2 border-white bg-white" />
          </motion.div>
        )}
      </AnimatePresence>

      <motion.div animate={bodyAnim} transition={bodyTransition} style={{ width: size, height: size }}>
        <svg viewBox="0 0 120 120" width={size} height={size}>
          {/* 그림자 */}
          <ellipse cx="60" cy="112" rx="30" ry="6" fill="#1E3A8A" opacity="0.12" />
          {/* 몸체 */}
          <circle cx="60" cy="58" r="42" fill={bodyColor} />
          {/* 하이라이트 (클레이 광택) */}
          <ellipse cx="46" cy="40" rx="16" ry="11" fill="#FFFFFF" opacity="0.35" />
          {/* 볼터치 */}
          <circle cx="36" cy="66" r="7" fill="#F9A8D4" opacity="0.8" />
          <circle cx="84" cy="66" r="7" fill="#F9A8D4" opacity="0.8" />

          {/* 눈 */}
          {current === 'happy' || current === 'celebrate' ? (
            <>
              <path d="M40 52 q6 -8 12 0" stroke="#0F172A" strokeWidth="4" fill="none" strokeLinecap="round" />
              <path d="M68 52 q6 -8 12 0" stroke="#0F172A" strokeWidth="4" fill="none" strokeLinecap="round" />
            </>
          ) : current === 'sad' ? (
            <>
              <path d="M40 50 q6 6 12 0" stroke="#0F172A" strokeWidth="4" fill="none" strokeLinecap="round" />
              <path d="M68 50 q6 6 12 0" stroke="#0F172A" strokeWidth="4" fill="none" strokeLinecap="round" />
            </>
          ) : (
            <>
              <circle cx="46" cy="52" r="5" fill="#0F172A" />
              <circle cx="74" cy="52" r="5" fill="#0F172A" />
              <circle cx="47.5" cy="50.5" r="1.6" fill="#FFFFFF" />
              <circle cx="75.5" cy="50.5" r="1.6" fill="#FFFFFF" />
            </>
          )}

          {/* 입 */}
          {current === 'happy' || current === 'celebrate' ? (
            <path d="M48 70 q12 16 24 0" stroke="#0F172A" strokeWidth="4" fill="#F472B6" strokeLinecap="round" />
          ) : current === 'sad' ? (
            <path d="M50 78 q10 -10 20 0" stroke="#0F172A" strokeWidth="4" fill="none" strokeLinecap="round" />
          ) : (
            <path d="M50 72 q10 8 20 0" stroke="#0F172A" strokeWidth="4" fill="none" strokeLinecap="round" />
          )}

          {/* 축하 별 */}
          {current === 'celebrate' && (
            <>
              <text x="14" y="30" fontSize="16">✨</text>
              <text x="92" y="34" fontSize="16">⭐</text>
            </>
          )}
        </svg>
      </motion.div>
    </div>
  );
};

export default Mascot;
