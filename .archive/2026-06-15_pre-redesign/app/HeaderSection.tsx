import React, { useRef, useState, useEffect, useMemo, useCallback } from 'react';
import { motion, AnimatePresence, useMotionValue } from 'framer-motion';
import { Button } from "./components/ui/button";
import {
    BarChart2, Target, BookOpen, Clock, Medal,
    Trophy, Cog, Check, X, AlertCircle, Hash,
    Percent, Info, Star, Award, PlayCircle, Lock, Crown,
    ArrowRight
} from "lucide-react";
import TimeAttackTableSelectModal from './TimeAttackTableSelectModal';
import PracticeTableSelectModal from './PracticeTableSelectModal';
import { usePremium } from '@/contexts/PremiumContext';
import { PremiumModal } from './PremiumModal';
import ProblemCountModal from './ProblemCountModal'; 

interface HeaderSectionProps {
    gameMode: 'practice' | 'timeAttack';
    score: number;
    streak: number;
    selectedTable: number;
    timeLeft: number;
    solvedProblems: number;
    requiredProblems: number;
    timeAttackLevel: number;
    isPaused: boolean;
    showScoreInfo: boolean;
    showStreakInfo: boolean;
    showTableInfo: boolean;
    showTimerSettings: boolean;
    selectedTime: number;
    masteredLevel: number;
    practiceStats: {
        [key: number]: {
            attempts: number;
            correct: number;
            lastPlayed: Date | null;
        }
    };
    history: Array<{
        correct: boolean;
        timestamp: Date;
    }>;
    timerActive: boolean;
    isTimeAttackComplete: boolean;
    setTimerActive: (active: boolean) => void;
    setIsPaused: (paused: boolean) => void;
    setTimeLeft: (time: number) => void;
    setSolvedProblems: (problems: number) => void;
    setIsTimeAttackComplete: (complete: boolean) => void;
    showAlert: (message: string, type: 'success' | 'error' | 'info' | 'warning') => void;
    showProblemCountSettings: boolean;
    setShowTimerSettings: (show: boolean) => void;
    setShowProblemCountSettings: (show: boolean) => void;
    onModeChange: (mode: 'practice' | 'timeAttack') => void;
    onSettingsClick: () => void;
    onScoreClick: () => void;
    onStreakClick: () => void;
    onTableClick: () => void;
    onTableSelectClick: () => void;
    onProblemCountClick: () => void;
    onTimerSettingsClick: () => void;
    onTimerToggle: (e: React.MouseEvent) => void;
    onTimeSelect: (time: number) => void;
    onScoreInfoClose: () => void;
    onStreakInfoClose: () => void;
    onTableInfoClose: () => void;
    onTimerSettingsClose: () => void;
    showTableSelectModal: boolean;
    setShowTableSelectModal: (show: boolean) => void;
    setUsedProblems: (problems: Set<string>) => void;
    resetTimeAttack: () => void;
    generateNewProblem: () => void;
    setTimeAttackLevel: (level: number) => void;
    usedProblems: Set<string>;
    setSelectedTable: (table: number) => void;
    isPremium: boolean;
    setIsPremium: React.Dispatch<React.SetStateAction<boolean>>;
    onRequiredProblemsChange: (count: number) => void;
}

interface InfoModalProps {
    show: boolean;
    onClose: () => void;
    title: string;
    children: React.ReactNode;
    cardRef?: React.RefObject<HTMLDivElement>;
    style?: React.CSSProperties;
}

interface TimerSettingsModalProps {
    show: boolean;
    selectedTime: number;
    onClose: () => void;
    onTimeSelect: (time: number) => void;
}

const PremiumModalContainer = React.memo(() => {
    const [showPremiumModal, setShowPremiumModal] = useState(false);
    const {
        isPremium,
        handlePurchase,
        handleModalOpen: contextHandleModalOpen,
        handleModalClose: contextHandleModalClose
    } = usePremium();

    const handleModalOpen = useCallback(() => {
        setShowPremiumModal(true);
        if (contextHandleModalOpen) {
            contextHandleModalOpen();
        }
    }, [contextHandleModalOpen]);

    const handleModalClose = useCallback(() => {
        setShowPremiumModal(false);
        if (contextHandleModalClose) {
            contextHandleModalClose();
        }
    }, [contextHandleModalClose]);

    return (
        <>
            <motion.button
                data-component="premium-button"
                onClick={handleModalOpen}
                className={`h-12 w-12 clay-btn
                    ${isPremium
                        ? 'bg-white shadow-clay-sm'
                        : 'bg-clay-yellow shadow-clay-yellow'
                    }
                    flex items-center justify-center
                    transition-all duration-300`}
                whileHover={{ scale: 1.02 }}
                whileTap={{ scale: 0.95 }}
            >
                <Crown
                    className={`w-5 h-5
                        ${isPremium ? 'text-clay-yellow' : 'text-white'}`}
                />
            </motion.button>

            <AnimatePresence>
                {showPremiumModal && (
                    <PremiumModal
                        show={showPremiumModal}
                        onClose={handleModalClose}
                    />
                )}
            </AnimatePresence>
        </>
    );
});

PremiumModalContainer.displayName = 'PremiumModalContainer';

const BaseModal: React.FC<{
    show: boolean;
    onClose: () => void;
    children: React.ReactNode;
    cardRef: React.RefObject<HTMLDivElement>;
    width?: string;
    position?: string;
}> = ({ show, onClose, children, cardRef, width, position }) => {
    if (!show) return null;

    const rect = cardRef.current?.getBoundingClientRect();

    return (
        <>
            <div
                className="fixed inset-0 bg-black/50 backdrop-blur-sm z-[100]"
                onClick={onClose}
            />
            <div
                className="absolute z-[101]"
                style={{
                    top: `${(rect?.bottom || 0) + window.scrollY + 16}px`,
                    left: `${rect?.left || 0}px`,
                    width: rect?.width || '100%',
                    maxHeight: 'calc(100vh - 200px)',
                    overflowY: 'auto'
                }}
            >
                <motion.div
                    initial={{ opacity: 0, y: -10 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -10 }}
                    className="w-full clay-card-sm shadow-clay"
                    onClick={(e) => e.stopPropagation()}
                >
                    <div className="relative">
                        <div
                            className="absolute -top-2 left-1/2 transform -tranblue-x-1/2 w-4 h-4 bg-white rotate-45"
                            style={{
                                boxShadow: '-2px -2px 2px rgba(0,0,0,0.03)'
                            }}
                        />
                        {children}
                    </div>
                </motion.div>
            </div>
        </>
    );
};

const CenterModal: React.FC<{
    show: boolean;
    onClose: () => void;
    children: React.ReactNode;
}> = ({ show, onClose, children }) => {
    if (!show) return null;

    return (
        <>
            <div
                className="fixed inset-0 bg-black/50 backdrop-blur-sm z-[100]"
                onClick={onClose}
            />
            <div className="fixed inset-0 flex items-center justify-center z-[101]">
                <motion.div
                    initial={{ opacity: 0, y: -20 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -20 }}
                    className="w-full max-w-sm clay-card shadow-clay mx-4"
                    onClick={(e) => e.stopPropagation()}
                >
                    {children}
                </motion.div>
            </div>
        </>
    );
};

const InfoModal: React.FC<InfoModalProps> = ({ show, onClose, title, children }) => {
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
                        className="fixed inset-0 bg-black/50 backdrop-blur-sm"
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
                            overflow-hidden touch-none select-none mb-safe
                            ${isDragging ? 'cursor-grabbing' : 'cursor-grab'}
                        `}
                    >
                        {/* 드래그 핸들 */}
                        <div className="flex justify-center pt-4 pb-2">
                            <div className="w-12 h-1.5 bg-clay-border rounded-full" />
                        </div>

                        {/* 헤더 */}
                        <div className="px-6 pb-4">
                            <h3 className="text-xl font-suite font-extrabold text-clay-blue-dark">{title}</h3>
                        </div>

                        {/* 컨텐츠 */}
                        <div className="px-6 pb-6">
                            <div className="space-y-4">
                                {children}
                            </div>
                        </div>
                    </motion.div>
                </motion.div>
            )}
        </AnimatePresence>
    );
};

const TimerSettingsModal: React.FC<TimerSettingsModalProps> = ({
    show,
    selectedTime,
    onClose,
    onTimeSelect
}) => {
    if (!show) return null;

    const timeOptions = [45, 50, 55, 60];

    return (
        <CenterModal show={show} onClose={onClose}>
            <div className="p-4">
                <div className="bg-clay-blue -m-4 mb-4 px-6 py-4 flex justify-between items-center">
                    <h4 className="text-lg font-suite font-extrabold text-white">타이머 설정</h4>
                    <button
                        onClick={onClose}
                        className="text-white/80 hover:text-white transition-colors"
                    >
                        <X className="h-5 w-5" />
                    </button>
                </div>
                <div className="space-y-2 px-2">
                    {timeOptions.map((time) => (
                        <Button
                            key={time}
                            variant={selectedTime === time ? "default" : "outline"}
                            onClick={() => {
                                onTimeSelect(time);
                                onClose();
                            }}
                            className={`
                                w-full flex items-center justify-between px-4 h-11
                                clay-btn
                                ${selectedTime === time
                                    ? 'bg-clay-blue shadow-clay-blue text-white'
                                    : 'bg-white shadow-clay-sm text-clay-muted hover:bg-clay-bg'}
                            `}
                        >
                            <div className="flex items-center gap-2">
                                {selectedTime === time && (
                                    <Check className="w-4 h-4 flex-shrink-0" />
                                )}
                                <span className="num-display text-sm">{time}초</span>
                            </div>
                        </Button>
                    ))}
                </div>
            </div>
        </CenterModal>
    );
};

const HeaderSection: React.FC<HeaderSectionProps> = ({
    gameMode,
    score,
    streak,
    selectedTable,
    timeLeft,
    solvedProblems,
    requiredProblems,
    timeAttackLevel,
    isPaused,
    showScoreInfo,
    showStreakInfo,
    showTableInfo,
    showTimerSettings,
    selectedTime,
    masteredLevel,
    practiceStats,
    history,
    timerActive,
    isTimeAttackComplete,
    setTimerActive,
    setIsPaused,
    setTimeLeft,
    setSolvedProblems,
    setIsTimeAttackComplete,
    setShowTimerSettings,
    setShowProblemCountSettings,
    showAlert,
    showTableSelectModal,
    setShowTableSelectModal,
    setUsedProblems,
    resetTimeAttack,
    generateNewProblem,
    setTimeAttackLevel,
    usedProblems,
    onModeChange,
    onSettingsClick,
    onScoreClick,
    onStreakClick,
    onTableClick,
    onTableSelectClick,
    onProblemCountClick,
    onTimerSettingsClick,
    onTimerToggle,
    onTimeSelect,
    onScoreInfoClose,
    onStreakInfoClose,
    onTableInfoClose,
    onTimerSettingsClose,
    setSelectedTable,
    onRequiredProblemsChange
}) => {
    const [showProblemCountModal, setShowProblemCountModal] = useState(false);

    const updateLocalStorage = useCallback((count: number) => {
        const savedState = JSON.parse(localStorage.getItem('multiplicationGame') || '{}');
        localStorage.setItem('multiplicationGame', JSON.stringify({
            ...savedState,
            requiredProblems: count,
            lastUpdated: new Date().toISOString()
        }));
    }, []);

    const handleProblemCountSelect = useCallback((count: number) => {
        onRequiredProblemsChange(count);
        updateLocalStorage(count);
        showAlert(`문제 수가 ${count}개로 설정되었습니다.`, 'success');
    }, [onRequiredProblemsChange, updateLocalStorage, showAlert]);

    useEffect(() => {
        const savedState = JSON.parse(localStorage.getItem('multiplicationGame') || '{}');
        if (savedState.requiredProblems) {
            onRequiredProblemsChange(savedState.requiredProblems);
        }
    }, [onRequiredProblemsChange]);

    useEffect(() => {
        if (gameMode === 'timeAttack') {
            setSolvedProblems(0);
            setIsTimeAttackComplete(false);
            setTimerActive(false);
            setIsPaused(true);
            setTimeLeft(selectedTime);
        }
    }, [
        requiredProblems,
        gameMode,
        selectedTime,
        setIsPaused,
        setIsTimeAttackComplete,
        setSolvedProblems,
        setTimeLeft,
        setTimerActive
    ]);

    const scoreCardRef = useRef<HTMLDivElement>(null);
    const streakCardRef = useRef<HTMLDivElement>(null);

    const containerVariants = {
        hidden: { opacity: 0, y: -20 },
        visible: {
            opacity: 1,
            y: 0,
            transition: {
                duration: 0.5,
                staggerChildren: 0.1
            }
        }
    };

    const itemVariants = {
        hidden: { opacity: 0, y: -10 },
        visible: { opacity: 1, y: 0 }
    };

    const getProgressColor = (solved: number, required: number) => {
        const percentage = (solved / required) * 100;

        if (percentage < 30) {
            return {
                bg: 'bg-clay-pink',
                from: 'from-clay-pink/10',
                text: 'text-clay-pink'
            };
        } else if (percentage < 60) {
            return {
                bg: 'bg-clay-yellow',
                from: 'from-clay-yellow/10',
                text: 'text-clay-yellow'
            };
        } else if (percentage < 90) {
            return {
                bg: 'bg-clay-mint',
                from: 'from-clay-mint/10',
                text: 'text-clay-mint'
            };
        } else {
            return {
                bg: 'bg-clay-blue',
                from: 'from-clay-blue/10',
                text: 'text-clay-blue'
            };
        }
    };

    const maxStreak = history.length > 0
        ? Math.max(...history.reduce((acc: number[], curr, index) => {
            if (curr.correct) {
                if (index === 0 || !history[index - 1].correct) {
                    acc.push(1);
                } else {
                    acc.push(acc[acc.length - 1] + 1);
                }
            } else {
                acc.push(0);
            }
            return acc;
        }, [0]))
        : 0;

    const baseCardStyle = `
        relative overflow-hidden
        clay-card-sm
        transition-all duration-300
        group
        cursor-pointer
    `;

    const labelStyle = "text-xs font-suite font-bold text-clay-muted";
    const valueStyle = "num-display text-lg text-clay-blue-dark";
    const iconBaseStyle = "w-5 h-5 transition-transform duration-300 group-hover:scale-110";

    const modeButtonStyle = (isActive: boolean) => `
        flex-1 h-12 flex items-center justify-center px-3 gap-2
        rounded-clay-sm relative transition-all duration-200
        ${isActive
            ? 'bg-clay-blue text-white shadow-clay-blue'
            : 'bg-white text-clay-muted shadow-clay-sm'
        }
    `;

    const progressPercentage = useMemo(() => {
        return (solvedProblems / requiredProblems) * 100;
    }, [solvedProblems, requiredProblems]);

    const [showPremiumModal, setShowPremiumModal] = useState(false);
    const {
        isPremium,
        handleModalOpen: contextHandleModalOpen,
        handleModalClose: contextHandleModalClose
    } = usePremium();

    return (
        <div className="relative mb-2">
            <AnimatePresence>
                {showTableSelectModal && (
                    gameMode === 'practice' ? (
                        <PracticeTableSelectModal
                            show={showTableSelectModal}
                            onClose={() => setShowTableSelectModal(false)}
                            currentTable={selectedTable}
                            practiceStats={practiceStats}
                            onTableSelect={(table: number) => {
                                setSelectedTable(table);
                                generateNewProblem();
                                const savedState = JSON.parse(localStorage.getItem('multiplicationGame') || '{}');
                                localStorage.setItem('multiplicationGame', JSON.stringify({
                                    ...savedState,
                                    selectedTable: table,
                                    lastPracticeDate: new Date().toISOString()
                                }));
                            }}
                            showAlert={showAlert}
                        />
                    ) : (
                        <TimeAttackTableSelectModal
                            masteredLevel={masteredLevel}
                            timeAttackLevel={timeAttackLevel}
                            setTimeAttackLevel={setTimeAttackLevel}
                            setShowTableSelectModal={setShowTableSelectModal}
                            setUsedProblems={setUsedProblems}
                            showAlert={showAlert}
                            resetTimeAttack={resetTimeAttack}
                            generateNewProblem={generateNewProblem}
                            gameMode={gameMode}
                            setIsPaused={setIsPaused}
                            isTimeAttackComplete={isTimeAttackComplete}
                            timerActive={timerActive}
                        />
                    )
                )}
            </AnimatePresence>

            <AnimatePresence>
                {showScoreInfo && (
                    <InfoModal
                        show={showScoreInfo}
                        onClose={onScoreInfoClose}
                        title="점수 기준"
                        cardRef={scoreCardRef}
                    >
                        <ul className="space-y-4 text-base text-clay-ink">
                            <li className="flex items-center gap-3 clay-card-sm p-3">
                                <Check className="w-5 h-5 text-clay-mint" />
                                <span>정답: +10점</span>
                            </li>
                            <li className="flex items-center gap-3 clay-card-sm p-3">
                                <X className="w-5 h-5 text-clay-pink" />
                                <span>오답: -15점</span>
                            </li>
                            <li className="flex items-center gap-3 clay-card-sm p-3">
                                <AlertCircle className="w-5 h-5 text-clay-blue" />
                                <span>최저점: 0점</span>
                            </li>
                        </ul>
                    </InfoModal>
                )}
            </AnimatePresence>

            <AnimatePresence>
                {showStreakInfo && (
                    <InfoModal
                        show={showStreakInfo}
                        onClose={onStreakInfoClose}
                        title="연속 정답"
                        cardRef={streakCardRef}
                    >
                        <div className="space-y-4 text-base text-clay-ink">
                            <div className="flex items-center gap-3 clay-card-sm p-3">
                                <Trophy className="w-5 h-5 text-clay-yellow" />
                                <span>최고기록: {maxStreak}회</span>
                            </div>
                            <div className="flex items-center gap-3 clay-card-sm p-3">
                                <Target className="w-5 h-5 text-clay-pink" />
                                <span>현재기록: {streak}회</span>
                            </div>
                        </div>
                    </InfoModal>
                )}
            </AnimatePresence>

            <motion.div
                variants={containerVariants}
                initial="hidden"
                animate="visible"
            >
                {/* 상단 버튼 영역 */}
                <div className="clay-card-sm p-3 mb-2">
                    <div className="flex items-center gap-2">
                        {/* 모드 선택 버튼 그룹 */}
                        <div className="flex-[3] bg-clay-bg p-1.5 rounded-clay-sm border-2 border-white h-14">
                            <div className="grid grid-cols-2 gap-1.5 h-full">
                                {/* 연습 모드 버튼 */}
                                <Button
                                    variant="ghost"
                                    onClick={() => onModeChange('practice')}
                                    className={`
                        h-full w-full relative clay-btn !px-2
                        transition-all duration-300
                        ${gameMode === 'practice'
                                            ? 'bg-clay-blue shadow-clay-blue'
                                            : 'bg-white shadow-clay-sm hover:bg-clay-bg'
                                        }
                    `}
                                >
                                    <div className="flex items-center justify-center gap-1.5 whitespace-nowrap">
                                        <BookOpen className={`w-5 h-5 ${gameMode === 'practice' ? 'text-white' : 'text-clay-blue'
                                            }`} />
                                        <span className={`text-sm font-suite font-extrabold ${gameMode === 'practice' ? 'text-white' : 'text-clay-muted'
                                            }`}>
                                            연습
                                        </span>
                                    </div>
                                </Button>

                                {/* 도전 모드 버튼 */}
                                <Button
                                    variant="ghost"
                                    onClick={() => onModeChange('timeAttack')}
                                    className={`
                        h-full w-full relative clay-btn !px-2
                        transition-all duration-300
                        ${gameMode === 'timeAttack'
                                            ? 'bg-clay-blue shadow-clay-blue'
                                            : 'bg-white shadow-clay-sm hover:bg-clay-bg'
                                        }
                    `}
                                >
                                    <div className="flex items-center justify-center gap-1.5 whitespace-nowrap">
                                        <Clock className={`w-5 h-5 ${gameMode === 'timeAttack' ? 'text-white' : 'text-clay-blue'
                                            }`} />
                                        <span className={`text-sm font-suite font-extrabold ${gameMode === 'timeAttack' ? 'text-white' : 'text-clay-muted'
                                            }`}>
                                            도전
                                        </span>
                                    </div>
                                </Button>
                            </div>
                        </div>

                        {/* 작은 버튼들 (프리미엄, 설정) */}
                        {/* 작은 버튼들 (프리미엄, 설정) */}
                        <div className="flex gap-2">
                            {/* 프리미엄 버튼 */}
                            <div className="flex-1">
                                <motion.button
                                    data-component="premium-button"
                                    onClick={() => {
                                        if (contextHandleModalOpen) {
                                            contextHandleModalOpen();
                                        }
                                        setShowPremiumModal(true);
                                    }}
                                    className={`
                w-14 h-14 clay-btn
                flex items-center justify-center
                transition-all duration-300
                ${isPremium
                                            ? 'bg-white shadow-clay-sm'
                                            : 'bg-clay-yellow shadow-clay-yellow'
                                        }
            `}
                                    whileHover={{ scale: 1.02 }}
                                    whileTap={{ scale: 0.95 }}
                                >
                                    <Crown className={`w-5 h-5 ${isPremium ? 'text-clay-yellow' : 'text-white'}`} />
                                </motion.button>

                                <AnimatePresence>
                                    {showPremiumModal && (
                                        <PremiumModal
                                            show={showPremiumModal}
                                            onClose={() => {
                                                if (contextHandleModalClose) {
                                                    contextHandleModalClose();
                                                }
                                                setShowPremiumModal(false);
                                            }}
                                        />
                                    )}
                                </AnimatePresence>
                            </div>

                            {/* 설정 버튼 */}
                            <motion.button
                                onClick={onSettingsClick}
                                whileHover={{ scale: 1.02 }}
                                whileTap={{ scale: 0.95 }}
                                className="w-14 h-14 clay-btn bg-white
            shadow-clay-sm transition-all duration-300
            flex items-center justify-center"
                            >
                                <Cog className="w-5 h-5 text-clay-blue" />
                            </motion.button>
                        </div>
                    </div>
                </div>

                {/* 카드 컨테이너 */}
                <div className="clay-card-sm p-3 mb-2">
                    <div className="grid grid-cols-12 gap-3">
                        {gameMode === 'practice' ? (
                            <>
                                {/* 점수 카드 */}
                                <motion.div data-component="score-card" variants={itemVariants} className="col-span-4 relative" ref={scoreCardRef}>
                                    <div
                                        className={`${baseCardStyle} h-[108px]`}
                                        onClick={onScoreClick}
                                    >
                                        <div className="p-4 h-full flex flex-col">
                                            <p className={labelStyle}>점수</p>
                                            <div className="mt-2 flex-1 flex flex-col justify-between">
                                                <div className="flex items-center justify-between">
                                                    <span className={valueStyle}>{score}</span>
                                                    <BarChart2 className={`${iconBaseStyle} text-clay-pink`} />
                                                </div>
                                            </div>
                                        </div>
                                        <div className="absolute inset-0 bg-gradient-to-r from-clay-pink/10 to-transparent opacity-0 group-hover:opacity-100 transition-opacity rounded-clay-sm" />
                                    </div>
                                </motion.div>

                                {/* 연속 정답 카드 */}
                                <motion.div data-component="streak-card" variants={itemVariants} className="col-span-4 relative" ref={streakCardRef}>
                                    <div
                                        className={`${baseCardStyle} h-[108px]`}
                                        onClick={onStreakClick}
                                    >
                                        <div className="p-4 h-full flex flex-col">
                                            <p className={labelStyle}>연속 정답</p>
                                            <div className="mt-2 flex-1 flex flex-col justify-between">
                                                <div className="flex items-center justify-between">
                                                    <span className={valueStyle}>{streak}</span>
                                                    <Target className={`${iconBaseStyle} text-clay-yellow`} />
                                                </div>
                                            </div>
                                        </div>
                                        <div className="absolute inset-0 bg-gradient-to-r from-clay-yellow/10 to-transparent opacity-0 group-hover:opacity-100 transition-opacity rounded-clay-sm" />
                                    </div>
                                </motion.div>

                                {/* 학습 중 카드 */}
                                <motion.div data-component="learning-card" variants={itemVariants} className="col-span-4 relative">
                                    <div
                                        className={`${baseCardStyle} h-[108px]`}
                                        onClick={() => {
                                            setShowTableSelectModal(true);
                                        }}
                                    >
                                        <div className="p-4 h-full flex flex-col">
                                            <p className={labelStyle}>학습 중</p>
                                            <div className="mt-2 flex-1 flex flex-col justify-between">
                                                <div className="flex items-center justify-between">
                                                    <span className={valueStyle}>{selectedTable}단</span>
                                                    <BookOpen className={`${iconBaseStyle} text-clay-blue`} />
                                                </div>
                                            </div>
                                            <motion.div
                                                data-component="progress-card"
                                                initial={{ opacity: 0.5, y: 0 }}
                                                animate={{
                                                    opacity: [0.5, 1, 0.5],
                                                    y: [0, -3, 0]
                                                }}
                                                transition={{
                                                    duration: 1.5,
                                                    repeat: Infinity,
                                                    ease: "easeInOut"
                                                }}
                                                className="flex items-center justify-center gap-1 text-clay-blue"
                                            >
                                                <span className="text-xs font-medium">Click</span>
                                                <motion.div
                                                    data-component="level-card"
                                                    animate={{ rotate: [0, 15, 0] }}
                                                    transition={{
                                                        duration: 1.5,
                                                        repeat: Infinity,
                                                        ease: "easeInOut"
                                                    }}
                                                >
                                                </motion.div>
                                            </motion.div>
                                        </div>
                                        <div className="absolute inset-0 bg-gradient-to-r from-clay-blue/10 to-transparent opacity-0 group-hover:opacity-100 transition-opacity rounded-clay-sm" />
                                    </div>
                                </motion.div>
                            </>
                        ) : (
                            <>
                                {/* 타이머 카드 */}
                                <motion.div variants={itemVariants} className="col-span-4 relative">
                                    <div
                                        className={`${baseCardStyle} h-[108px]`}
                                        onClick={onTimerToggle}
                                    >
                                        <div className="p-4 h-full flex flex-col">
                                            <p className={labelStyle}>남은 시간</p>
                                            <div className="mt-2 flex-1 flex flex-col justify-between">
                                                <div className="flex items-center justify-between">
                                                    <span className={`${valueStyle} ${timeLeft <= 10 ? 'text-clay-pink-dark' : ''}`}>
                                                        {timeLeft}초
                                                    </span>
                                                    <motion.div
                                                        animate={timeLeft <= 10 ? {
                                                            scale: [1, 1.2, 1],
                                                            transition: { duration: 1, repeat: Infinity }
                                                        } : {}}
                                                    >
                                                        <Clock className={`${iconBaseStyle} ${timeLeft <= 10 ? 'text-clay-pink' : 'text-clay-blue'}`} />
                                                    </motion.div>
                                                </div>
                                                {(!timerActive || isPaused) && !isTimeAttackComplete && (
                                                    <motion.button
                                                        initial={{ opacity: 0 }}
                                                        animate={{ opacity: 1 }}
                                                        exit={{ opacity: 0 }}
                                                        onClick={(e) => {
                                                            e.stopPropagation();
                                                            setTimerActive(true);
                                                            setIsPaused(false);
                                                            showAlert('타이머 시작!', 'success');
                                                            if (solvedProblems === 0) {
                                                                setTimeLeft(selectedTime);
                                                            }
                                                        }}
                                                        className="mt-1 self-center flex items-center gap-1.5 px-3 py-1
                                                            clay-btn bg-clay-blue shadow-clay-blue text-white rounded-clay-sm
                                                            transition-all text-xs font-suite font-extrabold"
                                                    >
                                                        <PlayCircle className="w-3 h-3" />
                                                        시작
                                                    </motion.button>
                                                )}
                                            </div>
                                        </div>
                                        <div className={`
                                            absolute inset-0 bg-gradient-to-r
                                            ${timeLeft <= 10 ? 'from-clay-pink/10' : 'from-clay-blue/10'}
                                            to-transparent opacity-0 group-hover:opacity-100 transition-opacity
                                            rounded-clay-sm
                                        `} />
                                    </div>
                                </motion.div>

                                {/* 타임어택 진행도 카드 */}
                                <motion.div variants={itemVariants} className="col-span-4 relative">
                                    <div
                                        className={`${baseCardStyle} h-[108px]`}
                                        onClick={() => setShowProblemCountModal(true)}
                                    >
                                        <div className="p-4 h-full flex flex-col">
                                            <p className={labelStyle}>문제</p>
                                            <div className="mt-2">
                                                <div className="flex items-center justify-between">
                                                    <div className="flex items-center gap-1">
                                                        <span className={valueStyle}>{solvedProblems}</span>
                                                        <span className="num-display text-clay-muted">/</span>
                                                        <span className="num-display text-clay-muted">{requiredProblems}</span>
                                                    </div>
                                                    <Medal className={`${iconBaseStyle} ${getProgressColor(solvedProblems, requiredProblems).text}`} />
                                                </div>
                                                <div className="h-1.5 bg-clay-bg rounded-full overflow-hidden mt-2">
                                                    <motion.div
                                                        key={`${solvedProblems}-${requiredProblems}`}
                                                        initial={{ width: 0 }}
                                                        animate={{ width: `${progressPercentage}%` }}
                                                        transition={{
                                                            duration: 0.5,
                                                            ease: "easeInOut"
                                                        }}
                                                        className={`h-full rounded-full ${getProgressColor(solvedProblems, requiredProblems).bg}`}
                                                    />
                                                </div>
                                            </div>
                                        </div>
                                        <div className={`absolute inset-0 bg-gradient-to-r ${getProgressColor(solvedProblems, requiredProblems).from} to-transparent opacity-0 group-hover:opacity-100 transition-opacity rounded-clay-sm`} />
                                    </div>
                                </motion.div>

                                {/* 도전 중 카드 */}
                                <motion.div variants={itemVariants} className="col-span-4 relative">
                                    <div
                                        className={`${baseCardStyle} h-[108px]`}
                                        onClick={onTableSelectClick}
                                    >
                                        <div className="p-4 h-full flex flex-col">
                                            <p className={labelStyle}>도전 중</p>
                                            <div className="mt-2">
                                                <div className="flex items-center justify-between">
                                                    <span className={valueStyle}>{timeAttackLevel}단</span>
                                                    <Trophy className={`${iconBaseStyle} text-clay-purple`} />
                                                </div>
                                                <div className="flex items-center gap-1 mt-2">
                                                    <Award className="w-4 h-4 text-clay-yellow" />
                                                    <span className={labelStyle}>
                                                        최고:{masteredLevel}단
                                                    </span>
                                                </div>
                                            </div>
                                        </div>
                                        <div className="absolute inset-0 bg-gradient-to-r from-clay-purple/10 to-transparent opacity-0 group-hover:opacity-100 transition-opacity rounded-clay-sm" />
                                    </div>
                                </motion.div>
                            </>
                        )}
                    </div>
                </div>

                <AnimatePresence>
                    {showProblemCountModal && (
                        <ProblemCountModal
                            show={showProblemCountModal}
                            onClose={() => setShowProblemCountModal(false)}
                            onCountSelect={handleProblemCountSelect}
                            currentCount={requiredProblems}
                        />
                    )}
                </AnimatePresence>
            </motion.div>
        </div>
    );
};

export default HeaderSection;