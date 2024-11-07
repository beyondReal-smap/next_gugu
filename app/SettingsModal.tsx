// SettingsModal.tsx
import React from 'react';
import { motion } from 'framer-motion';
import { Button } from "./components/ui/button";
import {
    X, Check, Delete, Trophy, Award, Clock, Medal,
    BarChart2, Star, AlertCircle
} from "lucide-react";

interface SettingsModalProps {
    show: boolean;
    onClose: () => void;
    gameMode: 'practice' | 'timeAttack';
    selectedTable: number;
    setSelectedTable: (table: number) => void;
    timeAttackLevel: number;
    masteredLevel: number;
    totalAttempts: number;
    successfulAttempts: number;
    practiceStats: {
      [key: number]: {
        attempts: number;
        correct: number;
      }
    };
    onResetRecords: () => void;
    generateNewProblem: () => void;  // 추가
  }


const SettingsModal: React.FC<SettingsModalProps> = ({
    show,
    onClose,
    gameMode,
    selectedTable,
    setSelectedTable, // prop 추가
    timeAttackLevel,
    masteredLevel,
    totalAttempts,
    successfulAttempts,
    practiceStats,
    onResetRecords,
}) => {
    if (!show) return null;

    const baseCardStyle = `
    relative overflow-hidden
    bg-white rounded-xl
    shadow-md
    border-2 border-indigo-100
    transition-all duration-300
    group
  `;

    const labelStyle = "text-xs font-suite font-medium text-gray-500";
    const valueStyle = "text-lg font-suite font-bold text-indigo-700";
    const iconBaseStyle = "w-5 h-5 transition-transform duration-300 group-hover:scale-110";

    return (
        <div className="fixed inset-0 flex items-start justify-center z-[50] pt-20">
            <div className="absolute inset-0 bg-black/20 backdrop-blur-sm" onClick={onClose} />
            <motion.div
                initial={{ opacity: 0, y: -20 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -20 }}
                className="relative bg-gray-50 rounded-2xl shadow-xl w-full max-w-md mx-4 overflow-hidden"
            >
                {/* 헤더 */}
                <div className="bg-white px-6 py-4 border-b border-gray-100 flex justify-between items-center">
                    <h3 className="text-lg font-suite font-bold text-gray-900">설정</h3>
                    <button
                        onClick={onClose}
                        className="text-gray-400 hover:text-gray-600 transition-colors"
                    >
                        <X className="h-5 w-5" />
                    </button>
                </div>

                {/* 콘텐츠 */}
                <div className="p-6 space-y-6 max-h-[calc(100vh-200px)] overflow-y-auto">
                    {/* 모드별 통계 */}
                    <div className="space-y-4">
                        <h4 className="text-sm font-suite font-semibold text-gray-900 mb-3">
                            {gameMode === 'practice' ? '연습 모드 통계' : '타임어택 통계'}
                        </h4>

                        {gameMode === 'practice' ? (
                            <>
                                {/* 연습 모드 현재 단 통계 카드 */}
                                <div className={baseCardStyle}>
                                    <div className="p-4">
                                        <div className="flex items-center justify-between mb-4">
                                            <div>
                                                <p className={labelStyle}>현재 학습 중</p>
                                                <p className={valueStyle}>{selectedTable}단</p>
                                            </div>
                                            <Trophy className={`${iconBaseStyle} text-indigo-500`} />
                                        </div>

                                        {practiceStats[selectedTable] && (
                                            <div className="grid grid-cols-3 gap-4 mt-2">
                                                <div>
                                                    <p className={labelStyle}>시도</p>
                                                    <div className="flex items-center gap-1 mt-1">
                                                        <BarChart2 className="w-4 h-4 text-violet-500" />
                                                        <p className="text-sm font-suite font-semibold text-gray-700">
                                                            {practiceStats[selectedTable].attempts}회
                                                        </p>
                                                    </div>
                                                </div>
                                                <div>
                                                    <p className={labelStyle}>정답</p>
                                                    <div className="flex items-center gap-1 mt-1">
                                                        <Check className="w-4 h-4 text-green-500" />
                                                        <p className="text-sm font-suite font-semibold text-gray-700">
                                                            {practiceStats[selectedTable].correct}회
                                                        </p>
                                                    </div>
                                                </div>
                                                <div>
                                                    <p className={labelStyle}>정확도</p>
                                                    <div className="flex items-center gap-1 mt-1">
                                                        <Star className="w-4 h-4 text-amber-500" />
                                                        <p className="text-sm font-suite font-semibold text-gray-700">
                                                            {practiceStats[selectedTable].attempts > 0
                                                                ? Math.round((practiceStats[selectedTable].correct / practiceStats[selectedTable].attempts) * 100)
                                                                : 0}%
                                                        </p>
                                                    </div>
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                </div>

                                {/* 전체 학습 현황 */}
                                <div className={baseCardStyle}>
                                    <div className="p-4">
                                        <p className={labelStyle}>전체 학습 현황</p>
                                        <div className="mt-2 grid grid-cols-9 gap-1">
                                            {Array.from({ length: 18 }, (_, i) => i + 2).map((table) => (
                                                <motion.button
                                                    key={table}
                                                    whileHover={{ scale: 1.05 }}
                                                    whileTap={{ scale: 0.95 }}
                                                    onClick={() => setSelectedTable(table)}
                                                    className={`
                                            aspect-square rounded-md flex items-center justify-center text-xs font-suite font-medium
                                            transition-all duration-200
                                            ${table === selectedTable ? 'ring-2 ring-indigo-500 ring-offset-2' : ''}
                                            ${practiceStats[table]?.attempts > 0
                                                            ? practiceStats[table].correct / practiceStats[table].attempts >= 0.8
                                                                ? 'bg-green-100 text-green-700 hover:bg-green-200'
                                                                : 'bg-amber-100 text-amber-700 hover:bg-amber-200'
                                                            : 'bg-gray-100 text-gray-400 hover:bg-gray-200'
                                                        }
                                        `}
                                                    title={`${table}단 통계 보기`}
                                                >
                                                    {table}
                                                </motion.button>
                                            ))}
                                        </div>
                                    </div>
                                </div>
                            </>
                        ) : (
                            <>
                                {/* 초통계 카드들 */}
                                <div className="grid grid-cols-2 gap-4">
                                    <div className={baseCardStyle}>
                                        <div className="p-4">
                                            <p className={labelStyle}>현재 레벨</p>
                                            <div className="flex items-center justify-between mt-1">
                                                <p className={valueStyle}>{timeAttackLevel}단</p>
                                                <Trophy className={`${iconBaseStyle} text-indigo-500`} />
                                            </div>
                                        </div>
                                    </div>

                                    <div className={baseCardStyle}>
                                        <div className="p-4">
                                            <p className={labelStyle}>최고 레벨</p>
                                            <div className="flex items-center justify-between mt-1">
                                                <p className={valueStyle}>{masteredLevel}단</p>
                                                <Award className={`${iconBaseStyle} text-amber-500`} />
                                            </div>
                                        </div>
                                    </div>
                                </div>

                                <div className={baseCardStyle}>
                                    <div className="p-4">
                                        <p className={`${labelStyle} text-center mb-3`}>도전 기록</p>
                                        <div className="grid grid-cols-3 gap-4">
                                            <div className="text-center">
                                                <div className="flex flex-col items-center gap-1 mb-1">
                                                    <Medal className="w-5 h-5 text-violet-500" />
                                                    <p className="text-xs font-suite font-medium text-gray-600">
                                                        총 도전
                                                    </p>
                                                </div>
                                                <p className={`${valueStyle} text-violet-600`}>{totalAttempts}회</p>
                                            </div>
                                            <div className="text-center">
                                                <div className="flex flex-col items-center gap-1 mb-1">
                                                    <Check className="w-5 h-5 text-emerald-500" />
                                                    <p className="text-xs font-suite font-medium text-gray-600">
                                                        성공
                                                    </p>
                                                </div>
                                                <p className={`${valueStyle} text-emerald-600`}>{successfulAttempts}회</p>
                                            </div>
                                            <div className="text-center">
                                                <div className="flex flex-col items-center gap-1 mb-1">
                                                    <Star className="w-5 h-5 text-yellow-500" />
                                                    <p className="text-xs font-suite font-medium text-gray-600">
                                                        성공률
                                                    </p>
                                                </div>
                                                <p className={`${valueStyle} text-yellow-600`}>
                                                    {totalAttempts > 0
                                                        ? Math.round((successfulAttempts / totalAttempts) * 100)
                                                        : 0}%
                                                </p>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </>
                        )}
                    </div>

                    {/* 구분선 */}
                    <div className="border-t border-gray-100 -mx-6" />

                    {/* 초기화 버튼 */}
                    <div className="space-y-3">
                        <button
                            onClick={onResetRecords}
                            className="w-full flex items-center justify-center gap-2 px-4 py-3 rounded-xl
                text-red-600 hover:bg-red-50 transition-colors
                border-2 border-red-100 hover:border-red-200"
                        >
                            <Delete className="w-5 h-5" />
                            <span className="font-medium">기록 초기화</span>
                        </button>

                        <p className="text-xs text-gray-500 text-center">
                            * 초기화 시 현재 모드의 모든 기록이 삭제됩니다
                        </p>
                    </div>
                </div>
            </motion.div>
        </div>
    );
};

export default SettingsModal;