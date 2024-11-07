import React from 'react';
import { motion } from 'framer-motion';
import { X, Star, Check } from "lucide-react";

interface PracticeTableSelectModalProps {
    show: boolean;
    onClose: () => void;
    currentTable: number;
    practiceStats: {
        [key: number]: {
            attempts: number;
            correct: number;
            lastPlayed: Date | null;
        }
    };
    onTableSelect: (table: number) => void;
    showAlert: (message: string, type: 'success' | 'error' | 'info' | 'warning') => void;
}

const PracticeTableSelectModal: React.FC<PracticeTableSelectModalProps> = ({
    show,
    onClose,
    currentTable,
    practiceStats = {}, // 기본값 추가
    onTableSelect,
    showAlert
}) => {
    // 정확도 계산 함수
    const getAccuracy = (stats?: { attempts: number; correct: number }) => {
        if (!stats || stats.attempts === 0) return 0;
        return Math.round((stats.correct / stats.attempts) * 100);
    };

    // 상태에 따른 색상 결정 함수
    const getStatusColor = (stats?: { attempts: number; correct: number }) => {
        const accuracy = getAccuracy(stats);
        if (!stats || stats.attempts === 0) {
            return 'bg-white text-gray-600 border-2 border-gray-200 hover:border-indigo-200 hover:bg-indigo-50';
        }
        if (accuracy >= 90) {
            return 'bg-emerald-50 text-emerald-600 border-2 border-emerald-200 hover:bg-emerald-100';
        }
        if (accuracy >= 70) {
            return 'bg-amber-50 text-amber-600 border-2 border-amber-200 hover:bg-amber-100';
        }
        return 'bg-white text-gray-600 border-2 border-gray-200 hover:border-indigo-200 hover:bg-indigo-50';
    };

    if (!show) return null;

    return (
        <div className="fixed inset-0 flex items-start justify-center z-50 pt-20">
            <div 
                className="absolute inset-0 bg-black/20 backdrop-blur-sm" 
                onClick={onClose} 
            />
            <motion.div
                initial={{ opacity: 0, y: -20 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -20 }}
                className="relative bg-gray-50 rounded-2xl shadow-xl w-full max-w-2xl mx-4 overflow-hidden"
            >
                <div className="bg-gradient-to-r from-indigo-500 to-blue-500  px-6 py-4 flex justify-between items-center">
                    <h3 className="text-lg font-suite font-bold text-white">구구단 선택</h3>
                    <button
                        onClick={onClose}
                        className="text-gray-400 hover:text-gray-600 transition-colors"
                    >
                        <X className="h-5 w-5" />
                    </button>
                </div>

                <div className="p-6">
                    <div className="grid grid-cols-4 gap-3">
                        {Array.from({ length: 18 }, (_, i) => i + 2).map((table) => {
                            const stats = practiceStats[table];
                            const accuracy = getAccuracy(stats);
                            const isCurrent = table === currentTable;

                            return (
                                <motion.div
                                    key={table}
                                    whileHover={{ scale: 1.05 }}
                                    whileTap={{ scale: 0.95 }}
                                    className="relative"
                                >
                                    <button
                                        onClick={() => {
                                            onTableSelect(table);
                                            showAlert(`${table}단 연습을 시작합니다! 💪`, 'success');
                                            onClose();
                                            // 상태를 로컬 스토리지에 저장
                                            const savedState = JSON.parse(localStorage.getItem('multiplicationGame') || '{}');
                                            localStorage.setItem('multiplicationGame', JSON.stringify({
                                                ...savedState,
                                                selectedTable: table,
                                                lastPracticeDate: new Date().toISOString()
                                            }));
                                        }}
                                        className={`
                                            w-full aspect-square rounded-xl text-base font-suite font-medium
                                            flex items-center justify-center relative
                                            transition-all duration-300 shadow-sm
                                            ${isCurrent 
                                                ? 'bg-indigo-500 text-white shadow-indigo-100'
                                                : getStatusColor(stats)}
                                        `}
                                    >
                                        <div className="flex flex-col items-center gap-1">
                                            <span>{table}단</span>
                                            {stats && stats.attempts > 0 && (
                                                <div className="flex items-center gap-1 text-xs">
                                                    <Star className="w-3 h-3" />
                                                    <span>{accuracy}%</span>
                                                </div>
                                            )}
                                        </div>
                                    </button>
                                    {accuracy >= 90 && (
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
                            <span className="text-gray-600">90% 이상</span>
                        </div>
                        <div className="flex items-center gap-2">
                            <div className="w-3 h-3 bg-amber-500 rounded-full" />
                            <span className="text-gray-600">70% 이상</span>
                        </div>
                        <div className="flex items-center gap-2">
                            <div className="w-3 h-3 bg-indigo-500 rounded-full" />
                            <span className="text-gray-600">현재 선택</span>
                        </div>
                    </div>
                </div>
            </motion.div>
        </div>
    );
};

export default PracticeTableSelectModal;