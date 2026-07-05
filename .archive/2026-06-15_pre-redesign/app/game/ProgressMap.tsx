"use client";
import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Star, X, Trophy, Lock } from 'lucide-react';

interface ProgressMapProps {
  show: boolean;
  onClose: () => void;
  tableStars: Record<number, number>;
  onSelectTable: (table: number) => void;
}

const TABLES = [2, 3, 4, 5, 6, 7, 8, 9];

const ProgressMap: React.FC<ProgressMapProps> = ({ show, onClose, tableStars, onSelectTable }) => {
  const totalStars = Object.values(tableStars).reduce((a, b) => a + b, 0);
  const mastered = TABLES.filter((t) => (tableStars[t] || 0) >= 1).length;

  return (
    <AnimatePresence>
      {show && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={onClose}
          className="fixed inset-0 z-[85] flex items-end justify-center bg-clay-blue-dark/40 backdrop-blur-sm sm:items-center"
        >
          <motion.div
            initial={{ y: '100%', opacity: 0.5 }}
            animate={{ y: 0, opacity: 1 }}
            exit={{ y: '100%', opacity: 0 }}
            transition={{ type: 'spring', stiffness: 280, damping: 28 }}
            onClick={(e) => e.stopPropagation()}
            className="max-h-[88vh] w-full max-w-md overflow-y-auto rounded-t-clay-lg border-[4px] border-white
                       bg-clay-bg p-5 shadow-clay sm:rounded-clay-lg"
          >
            {/* 드래그 핸들 */}
            <div className="mx-auto mb-4 h-1.5 w-12 rounded-full bg-clay-border" />

            {/* 헤더 */}
            <div className="mb-4 flex items-center justify-between">
              <div>
                <h3 className="font-suite text-xl font-extrabold text-clay-blue-dark">구구단 모험 지도</h3>
                <div className="mt-1 flex items-center gap-3 text-sm font-bold text-clay-muted">
                  <span className="flex items-center gap-1">
                    <Star className="h-4 w-4 text-clay-yellow" fill="currentColor" /> {totalStars}/24
                  </span>
                  <span className="flex items-center gap-1">
                    <Trophy className="h-4 w-4 text-clay-mint" /> {mastered}/8단
                  </span>
                </div>
              </div>
              <button
                onClick={onClose}
                aria-label="닫기"
                className="flex h-9 w-9 items-center justify-center rounded-2xl border-2 border-white bg-white text-clay-muted shadow-clay-sm active:translate-y-0.5"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            {/* 단 그리드 */}
            <div className="grid grid-cols-2 gap-3">
              {TABLES.map((table, i) => {
                const stars = tableStars[table] || 0;
                const done = stars >= 1;
                return (
                  <motion.button
                    key={table}
                    initial={{ opacity: 0, scale: 0.8, y: 12 }}
                    animate={{ opacity: 1, scale: 1, y: 0 }}
                    transition={{ delay: i * 0.04, type: 'spring', stiffness: 360, damping: 22 }}
                    whileTap={{ scale: 0.95 }}
                    onClick={() => onSelectTable(table)}
                    className={`relative flex flex-col items-center gap-2 rounded-clay-sm border-[3px] border-white p-4 shadow-clay-sm transition-colors
                      ${done ? 'bg-gradient-to-br from-white to-clay-yellow-light/40' : 'bg-white'}`}
                  >
                    {done && (
                      <span className="absolute -right-1.5 -top-1.5 flex h-6 w-6 items-center justify-center rounded-full border-2 border-white bg-clay-mint text-white shadow">
                        <Trophy className="h-3 w-3" />
                      </span>
                    )}
                    <span className="num-display text-3xl font-extrabold text-clay-blue">{table}<span className="text-lg text-clay-muted">단</span></span>
                    <div className="flex gap-0.5">
                      {[1, 2, 3].map((s) => (
                        <Star
                          key={s}
                          className={`h-5 w-5 ${s <= stars ? 'text-clay-yellow' : 'text-clay-border'}`}
                          fill="currentColor"
                        />
                      ))}
                    </div>
                  </motion.button>
                );
              })}
            </div>

            <p className="mt-4 text-center font-suite text-xs font-medium text-clay-muted">
              단을 누르면 연습을 시작해요 · 타임어택을 클리어하면 별을 얻어요 ⭐
            </p>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
};

export default ProgressMap;
