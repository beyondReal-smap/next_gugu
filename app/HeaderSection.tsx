import React, { useRef, useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Button } from "./components/ui/button";
import {
    BarChart2, Target, BookOpen, Clock, Medal,
    Trophy, Cog, Check, X, AlertCircle, Hash,
    Percent, Info, Star, Award, PlayCircle, Lock, Crown,
    ArrowRight
} from "lucide-react";
import TimeAttackTableSelectModal from './TimeAttackTableSelectModal';
import PracticeTableSelectModal from './PracticeTableSelectModal';
import PurchaseManager from './lib/purchaseManager';
import PremiumModal from '../components/PremiumModal';  // Import 추가


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
    setSelectedTable: (table: number) => void; // 추가된 prop
    isPremium: boolean;
    setIsPremium: (value: boolean) => void;
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

// PremiumModalContainer 컴포넌트를 새로 만들어 상태 관리를 분리
const PremiumModalContainer = React.memo(({
    isPremium,
    setIsPremium,
    showAlert
}: {
    isPremium: boolean;
    setIsPremium: (value: boolean) => void;
    showAlert: (message: string, type: 'success' | 'error' | 'info' | 'warning') => void;
}) => {
    const [showPremiumModal, setShowPremiumModal] = useState(false);

    const handlePurchase = async () => {
        try {
            const success = await PurchaseManager.savePurchaseStatus(true);
            if (success) {
                setIsPremium(true);
                showAlert('프리미엄으로 업그레이드 되었습니다! 🎉', 'success');
                setShowPremiumModal(false);
            }
        } catch (error) {
            showAlert('구매 중 오류가 발생했습니다', 'error');
        }
    };

    // Premium 상태 체크
    useEffect(() => {
        const checkPremiumStatus = async () => {
            try {
                const isPremiumUser = await PurchaseManager.getPurchaseStatus();
                if (isPremiumUser !== isPremium) {
                    setIsPremium(isPremiumUser);
                }
            } catch (error) {
                console.error('Failed to check premium status:', error);
            }
        };

        if (!isPremium) {
            checkPremiumStatus();
        }
    }, [isPremium, setIsPremium]);

    if (isPremium) {
        return (
            <div className="h-12 w-12 rounded-xl bg-white border border-gray-200
                flex items-center justify-center shadow-sm">
                <Crown className="w-7 h-7 text-amber-500" />
            </div>
        );
    }

    return (
        <>
            <motion.button
                onClick={() => setShowPremiumModal(true)}
                className="h-12 w-12 rounded-xl overflow-hidden
                    bg-gradient-to-r from-amber-400 to-orange-400
                    text-white shadow-sm hover:shadow-md
                    transition-all duration-300 flex items-center justify-center"
                whileHover={{ scale: 1.02 }}
                whileTap={{ scale: 0.95 }}
            >
                <Crown className="w-7 h-7" />
            </motion.button>
            <AnimatePresence>
                {showPremiumModal && (
                    <PremiumModal
                        show={showPremiumModal}
                        onClose={() => setShowPremiumModal(false)}
                        onPurchase={handlePurchase}
                    />
                )}
            </AnimatePresence>
        </>
    );
});

PremiumModalContainer.displayName = 'PremiumModalContainer';

// BaseModal 컴포넌트
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
                    top: `${(rect?.bottom || 0) + window.scrollY + 16}px`, // 16px로 증가
                    left: `${rect?.left || 0}px`,
                    width: rect?.width || '100%',
                    maxHeight: 'calc(100vh - 200px)', // 최대 높이 제한 추가
                    overflowY: 'auto' // 내용이 많을 경우 스크롤 가능하도록
                }}
            >
                <motion.div
                    initial={{ opacity: 0, y: -10 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -10 }}
                    className="w-full bg-white rounded-xl shadow-xl"
                    onClick={(e) => e.stopPropagation()}
                >
                    <div className="relative"> {/* 상대 위치 컨테이너 추가 */}
                        {/* 모달 화살표 추가 */}
                        <div
                            className="absolute -top-2 left-1/2 transform -translate-x-1/2 w-4 h-4 bg-white rotate-45"
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
                    className="w-full max-w-sm bg-white rounded-xl shadow-xl mx-4"
                    onClick={(e) => e.stopPropagation()}
                >
                    {children}
                </motion.div>
            </div>
        </>
    );
};

const InfoModal: React.FC<InfoModalProps> = ({ show, onClose, title, children, cardRef }) => {
    if (!show) return null;

    return (
        <div className="fixed inset-0 flex items-start justify-center z-50 pt-20">
            <div className="absolute inset-0 bg-black/20 backdrop-blur-sm" onClick={onClose} />
            <motion.div
                initial={{ opacity: 0, y: -20 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -20 }}
                className="relative bg-gray-50 rounded-2xl shadow-xl w-full max-w-md mx-4 overflow-hidden"
            >
                {/* 헤더 */}
                <div className="bg-white px-6 py-4 border-b border-gray-100 flex justify-between items-center">
                    <h3 className="text-lg font-suite font-bold text-gray-900">{title}</h3>
                    <button
                        onClick={onClose}
                        className="text-gray-400 hover:text-gray-600 transition-colors"
                    >
                        <X className="h-5 w-5" />
                    </button>
                </div>

                {/* 콘텐츠 */}
                <div className="p-6 space-y-4 max-h-[calc(100vh-200px)] overflow-y-auto">
                    <div className="text-gray-600">
                        {children}
                    </div>
                </div>
            </motion.div>
        </div>
    );
};

// TimerSettingsModal 컴포넌트
const TimerSettingsModal: React.FC<TimerSettingsModalProps> = ({
    show,
    selectedTime,
    onClose,
    onTimeSelect
}) => {
    if (!show) return null;

    const timeOptions = [45, 50, 55, 60];

    return (
        <CenterModal
            show={show}
            onClose={onClose}
        >
            <div className="p-4">
                <div className="flex justify-between items-center mb-4">
                    <h4 className="text-lg font-suite font-bold text-gray-900">타이머 설정</h4>
                    <button
                        onClick={onClose}
                        className="text-gray-400 hover:text-gray-600 transition-colors"
                    >
                        <X className="h-4 w-4" />
                    </button>
                </div>
                <div className="space-y-2">
                    {timeOptions.map((time) => (
                        <Button
                            key={time}
                            variant={selectedTime === time ? "default" : "outline"}
                            onClick={() => {
                                onTimeSelect(time);
                                onClose();
                            }}
                            className={`
                                w-full flex items-center justify-between px-4 h-10
                                ${selectedTime === time ? 'bg-indigo-500 hover:bg-indigo-600 text-white' : 'hover:bg-gray-50'}
                            `}
                        >
                            <div className="flex items-center gap-2">
                                {selectedTime === time && (
                                    <Check className="w-4 h-4 flex-shrink-0" />
                                )}
                                <span className="text-sm">{time}초</span>
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
    isPremium,
    setIsPremium,
}) => {
    const handlePurchase = async () => {
        try {
            const success = await PurchaseManager.savePurchaseStatus(true);
            if (success) {
                setIsPremium(true);
                showAlert('프리미엄으로 업그레이드 되었습니다! 🎉', 'success');
                setShowPremiumModal(false);
            }
        } catch (error) {
            showAlert('구매 중 오류가 발생했습니다', 'error');
        }
    };

    const [showPremiumModal, setShowPremiumModal] = useState(false);

    // Premium 상태 체크를 위한 useEffect 수정
    useEffect(() => {
        const checkPremiumStatus = async () => {
            try {
                const isPremiumUser = await PurchaseManager.getPurchaseStatus();
                if (isPremiumUser !== isPremium) {  // 상태가 다를 때만 업데이트
                    setIsPremium(isPremiumUser);
                }
            } catch (error) {
                console.error('Failed to check premium status:', error);
            }
        };

        // 컴포넌트 마운트 시에만 체크
        if (!isPremium) {
            checkPremiumStatus();
        }
    }, [isPremium, setIsPremium]); // isPremium과 setIsPremium을 의존성 배열에 추가

    const scoreCardRef = useRef<HTMLDivElement>(null);
    const streakCardRef = useRef<HTMLDivElement>(null);
    const tableCardRef = useRef<HTMLDivElement>(null);

    const handleTableSelect = (table: number) => {
        if (gameMode === 'practice') {
            setTimeAttackLevel(table); // 연습 모드에서도 같은 함수를 사용
            generateNewProblem();
            showAlert(`${table}단 연습을 시작합니다! 💪`, 'success');
        }
        setShowTableSelectModal(false);
    };

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

    const baseCardStyle = `
        relative overflow-hidden
        bg-white rounded-xl
        shadow-lg shadow-indigo-100/50
        border-2 border-indigo-100
        hover:border-indigo-300
        transition-all duration-300
        group
        cursor-pointer
    `;

    const labelStyle = "text-xs font-suite font-bold text-gray-700";
    const valueStyle = "text-lg font-suite font-bold text-indigo-700";
    const iconBaseStyle = "w-6 h-6 transition-transform duration-300 group-hover:scale-110";

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

    return (
        <div className="relative mb-2">
            {/* 모달 렌더링 부분 - AnimatePresence로 감싸서 렌더링 */}
            <AnimatePresence>
                {showTableSelectModal && (
                    gameMode === 'practice' ? (
                        <PracticeTableSelectModal
                            show={showTableSelectModal}
                            onClose={() => setShowTableSelectModal(false)}
                            currentTable={selectedTable}
                            practiceStats={practiceStats}
                            onTableSelect={(table: number) => {
                                setSelectedTable(table); // 선택한 단수 설정
                                generateNewProblem(); // 새로운 문제 생성
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
                        <ul className="space-y-4 text-base text-black"> {/* 글자 크기와 간격 증가 */}
                            <li className="flex items-center gap-3 bg-white p-3 rounded-lg border border-gray-100 shadow-sm">
                                <Check className="w-5 h-5 text-green-500" />
                                <span>정답: +10점</span>
                            </li>
                            <li className="flex items-center gap-3 bg-white p-3 rounded-lg border border-gray-100 shadow-sm">
                                <X className="w-5 h-5 text-red-500" />
                                <span>오답: -15점</span>
                            </li>
                            <li className="flex items-center gap-3 bg-white p-3 rounded-lg border border-gray-100 shadow-sm">
                                <AlertCircle className="w-5 h-5 text-blue-500" />
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
                        <div className="space-y-4 text-base text-black"> {/* 글자 크기와 간격 증가 */}
                            <div className="flex items-center gap-3 bg-white p-3 rounded-lg border border-gray-100 shadow-sm">
                                <Trophy className="w-5 h-5 text-yellow-500" />
                                <span>최고기록: {maxStreak}회</span>
                            </div>
                            <div className="flex items-center gap-3 bg-white p-3 rounded-lg border border-gray-100 shadow-sm">
                                <Target className="w-5 h-5 text-red-500" />
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
                <div className="mb-4">
                    {/* 모드 선택 세트 */}
                    <div className="flex w-full gap-2 items-center">
                        <div className="flex rounded-xl p-1 bg-white flex-1 gap-1 border border-gray-200 shadow-sm">
                            {/* 연습 모드 버튼 */}
                            <Button
                                variant="ghost"
                                onClick={() => onModeChange('practice')}
                                className={`flex-1 h-12 flex items-center justify-center px-3 gap-2
            rounded-lg relative transition-all duration-200
            ${gameMode === 'practice'
                                        ? 'bg-blue-500 text-white'
                                        : 'bg-white hover:bg-gray-50'}`}
                            >
                                <BookOpen className={`w-7 h-7 flex-shrink-0 
            ${gameMode === 'practice' ? 'text-white' : 'text-gray-500'}`}
                                />
                                <span className={`text-base font-suite font-medium
            ${gameMode === 'practice' ? 'text-white' : 'text-gray-600'}`}
                                >
                                    연습
                                </span>
                            </Button>

                            {/* 도전 모드 버튼 */}
                            <Button
                                variant="ghost"
                                onClick={() => onModeChange('timeAttack')}
                                className={`flex-1 h-12 flex items-center justify-center px-3 gap-2
            rounded-lg relative transition-all duration-200
            ${gameMode === 'timeAttack'
                                        ? 'bg-blue-500 text-white'
                                        : 'bg-white hover:bg-gray-50'}`}
                            >
                                <Clock className={`w-7 h-7 flex-shrink-0
            ${gameMode === 'timeAttack' ? 'text-white' : 'text-gray-500'}`}
                                />
                                <span className={`text-base font-suite font-medium
            ${gameMode === 'timeAttack' ? 'text-white' : 'text-gray-600'}`}
                                >
                                    도전
                                </span>
                            </Button>
                        </div>

                        {/* PremiumModalContainer로 교체 */}
                        <PremiumModalContainer
                            isPremium={isPremium}
                            setIsPremium={setIsPremium}
                            showAlert={showAlert}
                        />

                        {/* 설정 버튼 */}
                        <motion.button
                            onClick={onSettingsClick}
                            whileHover={{ scale: 1.02 }}
                            whileTap={{ scale: 0.95 }}
                            className="h-12 w-12 flex items-center justify-center rounded-xl
          bg-white hover:bg-gray-50 border border-gray-200 shadow-sm"
                        >
                            <Cog className="w-7 h-7 text-gray-600" />
                        </motion.button>
                    </div>
                </div>

                {/* 상태 표시 카드들 컨테이너 */}
                <div className="bg-white/50 p-3 rounded-xl backdrop-blur-sm mb-2 relative shadow-lg border border-indigo-100/50 z-[2]">
                    <div className="grid grid-cols-12 gap-3">
                        {gameMode === 'practice' ? (
                            // 연습 모드 카드들
                            <>
                                {/* 점수 카드 */}
                                <motion.div variants={itemVariants} className="col-span-4 relative" ref={scoreCardRef}>
                                    <div
                                        className={`${baseCardStyle} h-[108px]`}
                                        onClick={onScoreClick}
                                    >
                                        <div className="p-4 h-full flex flex-col">
                                            <p className={labelStyle}>점수</p>
                                            <div className="mt-2 flex-1 flex flex-col justify-between">
                                                <div className="flex items-center justify-between">
                                                    <span className={valueStyle}>{score}</span>
                                                    <BarChart2 className={`${iconBaseStyle} text-rose-500`} />
                                                </div>
                                            </div>
                                        </div>
                                        <div className="absolute inset-0 bg-gradient-to-r from-rose-500/10 to-transparent opacity-0 group-hover:opacity-100 transition-opacity rounded-xl" />
                                    </div>
                                </motion.div>

                                {/* 연속 정답 카드 */}
                                <motion.div variants={itemVariants} className="col-span-4 relative" ref={streakCardRef}>
                                    <div
                                        className={`${baseCardStyle} h-[108px]`}
                                        onClick={onStreakClick}
                                    >
                                        <div className="p-4 h-full flex flex-col">
                                            <p className={labelStyle}>연속 정답</p>
                                            <div className="mt-2 flex-1 flex flex-col justify-between">
                                                <div className="flex items-center justify-between">
                                                    <span className={valueStyle}>{streak}</span>
                                                    <Target className={`${iconBaseStyle} text-amber-500`} />
                                                </div>
                                            </div>
                                        </div>
                                        <div className="absolute inset-0 bg-gradient-to-r from-amber-500/10 to-transparent opacity-0 group-hover:opacity-100 transition-opacity rounded-xl" />
                                    </div>
                                </motion.div>

                                {/* 연습 모드의 학습 중 카드 부분 */}
                                <motion.div variants={itemVariants} className="col-span-4 relative">
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
                                                    <BookOpen className={`${iconBaseStyle} text-indigo-500`} />
                                                </div>
                                            </div>
                                        </div>
                                        <div className="absolute inset-0 bg-gradient-to-r from-indigo-500/10 to-transparent opacity-0 group-hover:opacity-100 transition-opacity rounded-xl" />
                                    </div>
                                </motion.div>

                            </>
                        ) : (
                            // 타임어택 모드 카드들
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
                                                    <span className={`${valueStyle} ${timeLeft <= 10 ? 'text-rose-600' : ''}`}>
                                                        {timeLeft}초
                                                    </span>
                                                    <motion.div
                                                        animate={timeLeft <= 10 ? {
                                                            scale: [1, 1.2, 1],
                                                            transition: { duration: 1, repeat: Infinity }
                                                        } : {}}
                                                    >
                                                        <Clock className={`${iconBaseStyle} ${timeLeft <= 10 ? 'text-rose-500' : 'text-blue-500'}`} />
                                                    </motion.div>
                                                </div>
                                                {/* 시작 버튼 표시 조건 수정 */}
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
                                bg-indigo-500 hover:bg-indigo-600 text-white rounded 
                                transition-colors text-xs font-suite font-medium"
                                                    >
                                                        <PlayCircle className="w-3 h-3" />
                                                        시작
                                                    </motion.button>
                                                )}
                                            </div>
                                        </div>
                                        <div className={`
            absolute inset-0 bg-gradient-to-r 
            ${timeLeft <= 10 ? 'from-rose-500/10' : 'from-blue-500/10'} 
            to-transparent opacity-0 group-hover:opacity-100 transition-opacity
            rounded-xl
        `} />
                                    </div>
                                    <AnimatePresence>
                                        {showTimerSettings && (
                                            <TimerSettingsModal
                                                show={showTimerSettings}
                                                selectedTime={selectedTime}
                                                onClose={onTimerSettingsClose}
                                                onTimeSelect={(time) => {
                                                    onTimeSelect(time);
                                                    setTimeLeft(time);
                                                    setTimerActive(false);
                                                    setIsPaused(true);
                                                    if (solvedProblems === 0) {
                                                        setSolvedProblems(0);
                                                        setIsTimeAttackComplete(false);
                                                    }
                                                }}
                                            />
                                        )}
                                    </AnimatePresence>
                                </motion.div>

                                {/* 진행도 카드 */}
                                <motion.div variants={itemVariants} className="col-span-4 relative">
                                    <div
                                        className={`${baseCardStyle} h-[108px]`}
                                        onClick={onProblemCountClick}
                                    >
                                        <div className="p-4 h-full flex flex-col">
                                            <p className={labelStyle}>문제</p>
                                            <div className="mt-2">
                                                <div className="flex items-center justify-between">
                                                    <div className="flex items-center gap-1">
                                                        <span className={valueStyle}>{solvedProblems}</span>
                                                        <span className="text-gray-400">/</span>
                                                        <span className="text-gray-500">{requiredProblems}</span>
                                                    </div>
                                                    <Medal className={`${iconBaseStyle} text-emerald-500`} />
                                                </div>
                                                <div className="h-1 bg-gray-100 rounded-full overflow-hidden mt-2">
                                                    <motion.div
                                                        initial={{ width: 0 }}
                                                        animate={{ width: `${(solvedProblems / requiredProblems) * 100}%` }}
                                                        transition={{ duration: 0.5 }}
                                                        className="h-full bg-emerald-500 rounded-full"
                                                    />
                                                </div>
                                            </div>
                                        </div>
                                        <div className="absolute inset-0 bg-gradient-to-r from-emerald-500/10 to-transparent opacity-0 group-hover:opacity-100 transition-opacity" />
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
                                                    <Trophy className={`${iconBaseStyle} text-purple-500`} />
                                                </div>
                                                <div className="flex items-center gap-1 mt-2">
                                                    <Award className="w-4 h-4 text-yellow-500" />
                                                    <span className={labelStyle}>
                                                        최고:{masteredLevel}단
                                                    </span>
                                                </div>
                                            </div>
                                        </div>
                                        <div className="absolute inset-0 bg-gradient-to-r from-purple-500/10 to-transparent opacity-0 group-hover:opacity-100 transition-opacity" />
                                    </div>
                                </motion.div>
                            </>
                        )}
                    </div>
                </div>
            </motion.div>
        </div>
    );
};

export default HeaderSection;