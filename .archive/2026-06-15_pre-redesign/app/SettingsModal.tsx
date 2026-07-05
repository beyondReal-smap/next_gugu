import React, { useRef, useState } from 'react';
import { motion, AnimatePresence, useMotionValue } from 'framer-motion';
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
    generateNewProblem: () => void;
}

const SettingsModal: React.FC<SettingsModalProps> = ({
    show,
    onClose,
    gameMode,
    selectedTable,
    setSelectedTable,
    timeAttackLevel,
    masteredLevel,
    totalAttempts,
    successfulAttempts,
    practiceStats,
    onResetRecords,
}) => {
    const modalRef = useRef<HTMLDivElement>(null);
    const [isDragging, setIsDragging] = useState(false);
    const [exitVelocity, setExitVelocity] = useState(0);
    const dragY = useMotionValue(0);
    const windowHeight = typeof window !== 'undefined' ? window.innerHeight : 1000;

    const getExitAnimation = () => {
        return {
            y: windowHeight,
            transition: {
                type: 'spring',
                damping: Math.max(15, 30 - Math.abs(exitVelocity / 100)),
                stiffness: Math.min(300, 200 + Math.abs(exitVelocity)),
                velocity: exitVelocity,
                duration: Math.max(0.3, Math.min(0.8, Math.abs(exitVelocity) / 2000))
            }
        };
    };

    const modalVariants = {
        hidden: { y: windowHeight },
        visible: {
            y: 0,
            transition: {
                type: 'spring',
                damping: 30,
                stiffness: 300
            }
        },
        exit: getExitAnimation()
    };

    const handleDrag = (_: any, info: { velocity: { y: number }; offset: { y: number } }) => {
        const speed = Math.abs(info.velocity.y);
        const offset = info.offset.y;

        if (modalRef.current) {
            const opacity = Math.max(0, 1 - (offset / (windowHeight * 0.5)));
            modalRef.current.style.opacity = opacity.toString();
        }

        if (speed > 500 && offset > 50) {
            setExitVelocity(info.velocity.y);
            onClose();
        }
    };

    const handleDragEnd = (_: any, info: { offset: { y: number }; velocity: { y: number } }) => {
        setIsDragging(false);

        const threshold = windowHeight * 0.2;

        if (info.offset.y > threshold || (info.velocity.y > 50 && info.offset.y > 50)) {
            setExitVelocity(info.velocity.y);
            onClose();
        } else {
            if (modalRef.current) {
                modalRef.current.style.opacity = '1';
            }
        }
    };

    const baseCardStyle = `
        relative overflow-hidden
        clay-card-sm
        transition-all duration-300
        group
    `;

    const labelStyle = "text-xs font-suite font-medium text-clay-muted";
    const valueStyle = "num-display text-lg font-suite font-bold text-clay-blue";
    const iconBaseStyle = "w-5 h-5 transition-transform duration-300 group-hover:scale-110";

    return (
        <AnimatePresence mode="wait">
            {show && (
                <motion.div
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    className="fixed inset-0 z-50 flex items-end justify-center"
                >
                    <motion.div
                        className="fixed inset-0 bg-clay-ink/40 backdrop-blur-sm"
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        exit={{ opacity: 0 }}
                        onClick={() => {
                            setExitVelocity(0);
                            onClose();
                        }}
                    />
                    <motion.div
                        ref={modalRef}
                        style={{ y: dragY }}
                        variants={modalVariants}
                        initial="hidden"
                        animate="visible"
                        exit="exit"
                        drag="y"
                        dragDirectionLock
                        dragConstraints={{ top: 0 }}
                        dragElastic={{ top: 0, bottom: 0.7 }}
                        onDragStart={() => setIsDragging(true)}
                        onDrag={handleDrag}
                        onDragEnd={handleDragEnd}
                        className={`
                            relative w-full max-w-md bg-white rounded-t-clay border-t-[3px] border-x-[3px] border-white shadow-clay
                            overflow-hidden touch-none select-none
                            ${isDragging ? 'cursor-grabbing' : 'cursor-grab'}
                        `}
                    >
                        {/* 드래그 핸들 */}
                        <div className="flex justify-center pt-4 pb-2">
                            <div className="w-12 h-1.5 bg-clay-border rounded-full" />
                        </div>

                        {/* 헤더 */}
                        <div className="px-6 pb-4">
                            <h3 className="text-xl font-suite font-bold text-clay-blue">설정</h3>
                        </div>

                        {/* 콘텐츠 */}
                        <div className="px-6 pb-6 space-y-6 max-h-[70vh] overflow-y-auto">
                            {/* 모드별 통계 */}
                            <div className="space-y-4">
                                <h4 className="text-sm font-suite font-semibold text-clay-ink mb-3">
                                    {gameMode === 'practice' ? '연습모드 통계' : '도전모드 통계'}
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
                                                    <Trophy className={`${iconBaseStyle} text-clay-blue`} />
                                                </div>

                                                {/* practiceStats[selectedTable] 조건부 렌더링 제거 */}
                                                <div className="grid grid-cols-3 gap-4 mt-2">
                                                    <div>
                                                        <p className={labelStyle}>시도</p>
                                                        <div className="flex items-center gap-1 mt-1">
                                                            <BarChart2 className="w-4 h-4 text-clay-purple" />
                                                            <p className="num-display text-sm font-suite font-semibold text-clay-ink">
                                                                {practiceStats[selectedTable]?.attempts ?? 0}회
                                                            </p>
                                                        </div>
                                                    </div>
                                                    <div>
                                                        <p className={labelStyle}>정답</p>
                                                        <div className="flex items-center gap-1 mt-1">
                                                            <Check className="w-4 h-4 text-clay-mint" />
                                                            <p className="num-display text-sm font-suite font-semibold text-clay-ink">
                                                                {practiceStats[selectedTable]?.correct ?? 0}회
                                                            </p>
                                                        </div>
                                                    </div>
                                                    <div>
                                                        <p className={labelStyle}>정확도</p>
                                                        <div className="flex items-center gap-1 mt-1">
                                                            <Star className="w-4 h-4 text-clay-yellow" />
                                                            <p className="num-display text-sm font-suite font-semibold text-clay-ink">
                                                                {practiceStats[selectedTable]?.attempts
                                                                    ? Math.round((practiceStats[selectedTable].correct / practiceStats[selectedTable].attempts) * 100)
                                                                    : 0}%
                                                            </p>
                                                        </div>
                                                    </div>
                                                </div>
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
                                                                aspect-square rounded-clay-sm flex items-center justify-center num-display text-xs font-suite font-bold border-2 border-white
                                                                transition-all duration-200
                                                                ${table === selectedTable ? 'ring-2 ring-clay-blue ring-offset-2' : ''}
                                                                ${practiceStats[table]?.attempts > 0
                                                                    ? practiceStats[table].correct / practiceStats[table].attempts >= 0.8
                                                                        ? 'bg-clay-mint-light text-clay-mint-dark shadow-clay-sm hover:brightness-105'
                                                                        : 'bg-clay-yellow-light text-clay-yellow-dark shadow-clay-sm hover:brightness-105'
                                                                    : 'bg-clay-bg text-clay-muted hover:brightness-105'
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
                                        {/* 도전모드 통계 카드들 */}
                                        <div className="grid grid-cols-2 gap-4">
                                            <div className={baseCardStyle}>
                                                <div className="p-4">
                                                    <p className={labelStyle}>현재 레벨</p>
                                                    <div className="flex items-center justify-between mt-1">
                                                        <p className={valueStyle}>{timeAttackLevel}단</p>
                                                        <Trophy className={`${iconBaseStyle} text-clay-blue`} />
                                                    </div>
                                                </div>
                                            </div>

                                            <div className={baseCardStyle}>
                                                <div className="p-4">
                                                    <p className={labelStyle}>최고 레벨</p>
                                                    <div className="flex items-center justify-between mt-1">
                                                        <p className={valueStyle}>{masteredLevel}단</p>
                                                        <Award className={`${iconBaseStyle} text-clay-yellow`} />
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
                                                            <Medal className="w-5 h-5 text-clay-purple" />
                                                            <p className="text-xs font-suite font-medium text-clay-muted">
                                                                총 도전
                                                            </p>
                                                        </div>
                                                        <p className={`${valueStyle} text-clay-purple`}>{totalAttempts}회</p>
                                                    </div>
                                                    <div className="text-center">
                                                        <div className="flex flex-col items-center gap-1 mb-1">
                                                            <Check className="w-5 h-5 text-clay-mint" />
                                                            <p className="text-xs font-suite font-medium text-clay-muted">
                                                                성공
                                                            </p>
                                                        </div>
                                                        <p className={`${valueStyle} text-clay-mint`}>{successfulAttempts}회</p>
                                                    </div>
                                                    <div className="text-center">
                                                        <div className="flex flex-col items-center gap-1 mb-1">
                                                            <Star className="w-5 h-5 text-clay-yellow" />
                                                            <p className="text-xs font-suite font-medium text-clay-muted">
                                                                성공률
                                                            </p>
                                                        </div>
                                                        <p className={`${valueStyle} text-clay-yellow`}>
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
                            <div className="border-t border-clay-border" />

                            {/* 초기화 버튼 */}
                            <div className="space-y-4">
                                <div className="bg-clay-pink-light/30 p-4 rounded-clay-sm border-2 border-white shadow-clay-sm">
                                    <h4 className="text-base font-suite font-bold text-clay-pink-dark mb-2 flex items-center gap-2">
                                        <AlertCircle className="w-5 h-5" />
                                        기록 초기화
                                    </h4>
                                    <p className="text-sm text-clay-pink-dark mb-4">
                                        * 초기화 시 현재 모드의 모든 기록이 삭제됩니다
                                    </p>
                                    <Button
                                        variant="destructive"
                                        onClick={onResetRecords}
                                        className="w-full h-11 clay-btn bg-clay-pink
                                        hover:brightness-105 text-white font-suite font-extrabold
                                        flex items-center justify-center gap-2 shadow-clay-pink"
                                    >
                                        <Delete className="w-5 h-5" />
                                        기록 초기화하기
                                    </Button>
                                </div>
                            </div>
                        </div>
                    </motion.div>
                </motion.div>
            )}
        </AnimatePresence>
    );
};

export default SettingsModal;