import React, { useCallback, useRef } from 'react';
import { motion } from 'framer-motion';
import { X, Check, Lock } from "lucide-react";

interface TimeAttackTableSelectModalProps {
    masteredLevel: number;
    timeAttackLevel: number;
    setTimeAttackLevel: (level: number) => void;
    setShowTableSelectModal: (show: boolean) => void;
    setUsedProblems: (problems: Set<string>) => void;
    showAlert: (message: string, type: 'success' | 'error' | 'info' | 'warning') => void;
    resetTimeAttack: () => void;
    generateNewProblem: () => void;
    gameMode: 'practice' | 'timeAttack';
    setIsPaused: (paused: boolean) => void;
    isTimeAttackComplete: boolean;
    timerActive: boolean;
}

const TimeAttackTableSelectModal: React.FC<TimeAttackTableSelectModalProps> = ({
    masteredLevel,
    timeAttackLevel,
    setTimeAttackLevel,
    setShowTableSelectModal,
    setUsedProblems,
    showAlert,
    resetTimeAttack,
    generateNewProblem,
    gameMode,
    setIsPaused,
    isTimeAttackComplete,
    timerActive,
}) => {
    const handleCloseTableSelectModal = useCallback(() => {
        setShowTableSelectModal(false);
        if (gameMode === 'timeAttack' && !isTimeAttackComplete && timerActive) {
            setIsPaused(false);
        }
    }, [setShowTableSelectModal, gameMode, isTimeAttackComplete, setIsPaused, timerActive]);

    return (
        <div className="fixed inset-0 flex items-start justify-center z-50 pt-20">
            <div 
                className="absolute inset-0 bg-black/20 backdrop-blur-sm" 
                onClick={handleCloseTableSelectModal} 
            />
            <motion.div
                initial={{ opacity: 0, y: -20 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -20 }}
                className="relative bg-gray-50 rounded-2xl shadow-xl w-full max-w-md mx-4 overflow-hidden"
            >
                {/* 헤더 */}
                <div className="bg-white px-6 py-4 border-b border-gray-100 flex justify-between items-center">
                    <h3 className="text-lg font-bold text-gray-900">단수 선택</h3>
                    <button
                        onClick={handleCloseTableSelectModal}
                        className="text-gray-400 hover:text-gray-600 transition-colors"
                    >
                        <X className="h-5 w-5" />
                    </button>
                </div>

                {/* 콘텐츠 */}
                <div className="p-6 space-y-6 max-h-[calc(100vh-200px)] overflow-y-auto">
                    <div className="grid grid-cols-4 gap-3">
                        {Array.from({ length: 18 }, (_, i) => i + 2).map((table) => {
                            const isMastered = table <= masteredLevel;
                            const isCurrent = table === timeAttackLevel;
                            const isLocked = !isMastered && table > masteredLevel + 1;

                            return (
                                <motion.div
                                    key={table}
                                    whileHover={isLocked ? {} : { scale: 1.05 }}
                                    whileTap={isLocked ? {} : { scale: 0.95 }}
                                    className="relative"
                                >
                                    <button
                                        onClick={() => {
                                            if (isLocked) return;
                                            setTimeAttackLevel(table);
                                            setShowTableSelectModal(false);
                                            setUsedProblems(new Set());
                                            showAlert(`${table}단에 도전합니다!\n준비되셨나요? 💪`, 'success');
                                            resetTimeAttack();
                                            generateNewProblem();
                                            if (gameMode === 'timeAttack') setIsPaused(false);
                                            
                                            // 상태를 로컬 스토리지에 저장
                                            const savedState = JSON.parse(localStorage.getItem('multiplicationGame') || '{}');
                                            localStorage.setItem('multiplicationGame', JSON.stringify({
                                                ...savedState,
                                                timeAttackLevel: table,
                                                lastTimeAttackDate: new Date().toISOString()
                                            }));
                                        }}
                                        disabled={isLocked}
                                        className={`
                                            w-full aspect-square rounded-xl text-base font-medium
                                            flex items-center justify-center relative
                                            transition-all duration-300 shadow-sm
                                            ${isLocked
                                                ? 'bg-gray-100 text-gray-400 cursor-not-allowed'
                                                : isCurrent
                                                    ? 'bg-indigo-500 text-white shadow-indigo-100'
                                                    : isMastered
                                                        ? 'bg-emerald-50 text-emerald-600 border-2 border-emerald-200'
                                                        : 'bg-white text-gray-600 border-2 border-gray-200 hover:border-indigo-200 hover:bg-indigo-50'
                                            }
                                        `}
                                    >
                                        <div className="flex flex-col items-center gap-1">
                                            <span>{table}단</span>
                                            {isMastered && (
                                                <Check className="w-4 h-4 text-emerald-500" />
                                            )}
                                            {isLocked && (
                                                <Lock className="w-4 h-4 text-gray-400" />
                                            )}
                                        </div>
                                    </button>
                                    {isMastered && (
                                        <motion.div
                                            initial={{ scale: 0 }}
                                            animate={{ scale: 1 }}
                                            className="absolute -top-1 -right-1 w-3 h-3 bg-emerald-500 rounded-full 
                                            border-2 border-white shadow-sm z-10"
                                            title="마스터 완료!"
                                        />
                                    )}
                                </motion.div>
                            );
                        })}
                    </div>

                    <div className="mt-6 flex items-center justify-center gap-6 text-sm">
                        <div className="flex items-center gap-2">
                            <div className="w-3 h-3 bg-emerald-500 rounded-full" />
                            <span className="text-gray-600">마스터 완료</span>
                        </div>
                        <div className="flex items-center gap-2">
                            <div className="w-3 h-3 bg-indigo-500 rounded-full" />
                            <span className="text-gray-600">현재 도전 중</span>
                        </div>
                        <div className="flex items-center gap-2">
                            <div className="w-3 h-3 bg-gray-300 rounded-full" />
                            <span className="text-gray-600">잠김</span>
                        </div>
                    </div>
                </div>
            </motion.div>
        </div>
    );
};

export default TimeAttackTableSelectModal;