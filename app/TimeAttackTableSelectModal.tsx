import React from 'react';
import { Button } from "./components/ui/button";
import { motion } from 'framer-motion';
import { X, Lock } from "lucide-react";

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
    setIsPaused,
    isTimeAttackComplete,
    timerActive
}) => {
    const handleTableSelect = (table: number) => {
        if (table > masteredLevel + 1) {
            showAlert('이전 단계를 먼저 완료해주세요!', 'warning');
            return;
        }

        setTimeAttackLevel(table);
        setUsedProblems(new Set());
        resetTimeAttack();
        generateNewProblem();
        setShowTableSelectModal(false);
        showAlert(`${table}단 도전을 시작합니다! 🎯`, 'success');
    };

    return (
        <div className="fixed inset-0 flex items-center justify-center z-[100]">
            <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={() => setShowTableSelectModal(false)} />
            <motion.div
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                exit={{ opacity: 0, scale: 0.95 }}
                className="relative bg-white rounded-xl shadow-xl w-full max-w-sm mx-4"
            >
                <div className="p-4">
                    <div className="flex justify-between items-center mb-4">
                        <h4 className="text-lg font-bold text-gray-900">단 선택</h4>
                        <button
                            onClick={() => setShowTableSelectModal(false)}
                            className="text-gray-400 hover:text-gray-600 transition-colors"
                        >
                            <X className="h-5 w-5" />
                        </button>
                    </div>
                    <div className="grid grid-cols-2 gap-2">
                        {Array.from({ length: 9 }, (_, i) => i + 2).map((table) => (
                            <Button
                                key={table}
                                variant={timeAttackLevel === table ? "default" : "outline"}
                                onClick={() => handleTableSelect(table)}
                                disabled={table > masteredLevel + 1 || (timerActive && !isTimeAttackComplete)}
                                className={`
                                    h-16 relative overflow-hidden
                                    ${timeAttackLevel === table ? 'bg-indigo-500 hover:bg-indigo-600' : ''}
                                `}
                            >
                                {table > masteredLevel + 1 && (
                                    <Lock className="absolute top-2 right-2 w-4 h-4 text-gray-400" />
                                )}
                                <span className="text-lg">{table}단</span>
                            </Button>
                        ))}
                    </div>
                </div>
            </motion.div>
        </div>
    );
};

export default TimeAttackTableSelectModal;