import React, { useRef, useState } from 'react';
import { Card } from './components/ui/card';
import { Button } from './components/ui/button';
import { Lock, Medal, Trophy, Check, Target } from 'lucide-react';
import { motion, AnimatePresence, PanInfo, useMotionValue } from 'framer-motion';

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
}: TimeAttackTableSelectModalProps) => {
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

    const handleDrag = (_: any, info: PanInfo) => {
        const speed = Math.abs(info.velocity.y);
        const offset = info.offset.y;
        
        if (modalRef.current) {
            const opacity = Math.max(0, 1 - (offset / (windowHeight * 0.5)));
            modalRef.current.style.opacity = opacity.toString();
        }

        if (speed > 500 && offset > 50) {
            setExitVelocity(info.velocity.y);
            setShowTableSelectModal(false);
        }
    };

    const handleDragEnd = (_: any, info: { offset: { y: number }; velocity: { y: number } }) => {
        setIsDragging(false);
        
        const threshold = windowHeight * 0.2;
        
        if (info.offset.y > threshold || (info.velocity.y > 50 && info.offset.y > 50)) {
            setExitVelocity(info.velocity.y);
            setShowTableSelectModal(false);
        } else {
            if (modalRef.current) {
                modalRef.current.style.opacity = '1';
            }
        }
    };

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
        <AnimatePresence mode="wait">
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
                        setShowTableSelectModal(false);
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
                        relative w-full bg-clay-bg rounded-t-clay border-[3px] border-white shadow-clay overflow-hidden
                        max-w-xl touch-none select-none
                        ${isDragging ? 'cursor-grabbing' : 'cursor-grab'}
                    `}
                >
                    <div className="flex justify-center pt-4 pb-2">
                        <div className="w-12 h-1.5 bg-clay-border rounded-full"/>
                    </div>

                    <div className="px-6 pb-2 flex justify-between items-center">
                        <div>
                            <h2 className="text-xl font-suite font-bold text-clay-blue">단 선택</h2>
                            <p className="text-sm text-clay-muted mt-0.5">
                                도전할 구구단을 선택하세요
                            </p>
                        </div>
                        <div className="flex items-center gap-2 bg-clay-blue-light/30 px-3 py-1.5 rounded-clay-sm shadow-clay-sm">
                            <Medal className="w-4 h-4 text-clay-blue" />
                            <span className="text-sm font-suite font-medium text-clay-blue">
                                최고기록: {masteredLevel}단
                            </span>
                        </div>
                    </div>

                    <div className="p-6 pt-2">
                        <div className="grid grid-cols-3 sm:grid-cols-4 gap-2.5 max-h-[45vh] overflow-y-auto">
                            {Array.from({ length: 18 }, (_, i) => i + 2).map((table) => {
                                const status = getTableStatus(table);
                                return (
                                    <motion.div
                                        key={table}
                                        whileHover={{ scale: 1.02 }}
                                        whileTap={{ scale: 0.98 }}
                                    >
                                        <Card
                                            className={`
                                                relative overflow-hidden cursor-pointer rounded-clay-sm border-[3px] border-white
                                                transition-all duration-300
                                                ${status === 'current' ? 'bg-clay-blue text-white shadow-clay-blue' :
                                                    status === 'mastered' ? 'bg-clay-mint-light/40 shadow-clay-mint' :
                                                    status === 'available' ? 'bg-clay-yellow-light/40 shadow-clay-yellow' :
                                                    'bg-clay-bg shadow-clay-sm'}
                                            `}
                                            onClick={() => handleTableSelect(table)}
                                        >
                                            <div className="p-3">
                                                <div className="flex justify-between items-start mb-1.5">
                                                    <span className={`num-display text-lg font-bold ${status === 'current' ? 'text-white' : 'text-clay-ink'}`}>{table}단</span>
                                                    {status === 'current' && <Check className="w-4 h-4 text-white" />}
                                                    {status === 'mastered' && <Trophy className="w-4 h-4 text-clay-mint-dark" />}
                                                    {status === 'available' && <Target className="w-4 h-4 text-clay-yellow-dark" />}
                                                    {status === 'locked' && <Lock className="w-4 h-4 text-clay-muted" />}
                                                </div>

                                                <div className="text-xs">
                                                    {status === 'current' && <span className="text-white/90">도전 중</span>}
                                                    {status === 'mastered' && <span className="text-clay-mint-dark">마스터</span>}
                                                    {status === 'available' && <span className="text-clay-yellow-dark">도전 가능</span>}
                                                    {status === 'locked' && <span className="text-clay-muted">잠김</span>}
                                                </div>
                                            </div>
                                        </Card>
                                    </motion.div>
                                );
                            })}
                        </div>
                    </div>
                </motion.div>
            </motion.div>
        </AnimatePresence>
    );
};

export default TimeAttackTableSelectModal;