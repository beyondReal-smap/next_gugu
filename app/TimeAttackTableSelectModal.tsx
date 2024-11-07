import React from 'react';
import { Card } from './components/ui/card';
import { Button } from './components/ui/button';
import { Lock, Medal, Trophy, Check, Target, Star, Info } from 'lucide-react';

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

const TimeAttackTableSelectModal = ({
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
    timerActive
}: TimeAttackTableSelectModalProps) => {

    const handleTableSelect = (table: number) => {
        if (table > masteredLevel + 1) {
            showAlert(`${masteredLevel}단을 먼저 마스터해야 도전할 수 있어요! 💪`, 'warning');
            return;
        }

        setTimeAttackLevel(table);
        setUsedProblems(new Set());
        resetTimeAttack();
        generateNewProblem();
        setShowTableSelectModal(false);
        showAlert(`${table}단에 도전합니다! 🔥`, 'info');
    };

    const getTableStatus = (table: number) => {
        if (table === timeAttackLevel) return 'current';
        if (table <= masteredLevel) return 'mastered';
        if (table === masteredLevel + 1) return 'available';
        return 'locked';
    };

    return (
        <div className="fixed inset-0 flex items-center justify-center z-[100]">
            <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={() => setShowTableSelectModal(false)} />
            <div className="relative w-full max-w-xl mx-4 bg-white rounded-2xl shadow-xl overflow-hidden">
                {/* 그라데이션 헤더 */}
                <div className="bg-gradient-to-r from-indigo-500 to-blue-500  to-blue-400 px-6 py-5">
                    <div className="flex justify-between items-center">
                        <div>
                            <h2 className="text-xl font-suite font-bold text-white">단 선택</h2>
                            <p className="text-sm text-blue-100 mt-0.5">
                                도전할 구구단을 선택하세요
                            </p>
                        </div>
                        <div className="flex items-center gap-2 bg-white/20 backdrop-blur-sm px-3 py-1.5 rounded-full">
                            <Medal className="w-4 h-4 text-white" />
                            <span className="text-sm font-suite font-medium text-white">
                                최고기록: {masteredLevel}단
                            </span>
                        </div>
                    </div>
                </div>

                <div className="p-4">
                    <div className="grid grid-cols-3 sm:grid-cols-4 gap-2.5 max-h-[45vh] overflow-y-auto p-1">
                        {Array.from({ length: 18 }, (_, i) => i + 2).map((table) => {
                            const status = getTableStatus(table);
                            return (
                                <Card
                                    key={table}
                                    className={`
                                        relative overflow-hidden cursor-pointer
                                        transition-all duration-300
                                        ${status === 'current' ? 'border-2 border-indigo-500 bg-indigo-50' :
                                            status === 'mastered' ? 'border-emerald-100 bg-emerald-50/50' :
                                                status === 'available' ? 'border-amber-100 hover:border-amber-200' :
                                                    'border-gray-100 bg-gray-50/50'}
                                    `}
                                    onClick={() => handleTableSelect(table)}
                                >
                                    <div className="p-3">
                                        <div className="flex justify-between items-start mb-1.5">
                                            <span className="text-lg font-suite font-bold text-gray-900">{table}단</span>
                                            {status === 'current' && <Check className="w-4 h-4 text-indigo-500" />}
                                            {status === 'mastered' && <Trophy className="w-4 h-4 text-emerald-500" />}
                                            {status === 'available' && <Target className="w-4 h-4 text-amber-500" />}
                                            {status === 'locked' && <Lock className="w-4 h-4 text-gray-300" />}
                                        </div>

                                        <div className="text-xs">
                                            {status === 'current' && <span className="text-indigo-600">도전 중</span>}
                                            {status === 'mastered' && <span className="text-emerald-600">마스터</span>}
                                            {status === 'available' && <span className="text-amber-600">도전 가능</span>}
                                            {status === 'locked' && <span className="text-gray-400">잠김</span>}
                                        </div>
                                    </div>
                                </Card>
                            );
                        })}
                    </div>

                    <div className="mt-4 flex gap-2">
                        <Button
                            variant="default"
                            onClick={() => setShowTableSelectModal(false)}
                            className="w-full bg-gradient-to-r from-indigo-500 to-blue-500 hover:bg-blue-600 text-white font-suite font-semibold py-2.5 h-auto"
                        >
                            닫기
                        </Button>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default TimeAttackTableSelectModal;