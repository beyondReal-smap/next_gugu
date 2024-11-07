"use client"
import React, { useState, useEffect, useRef, useCallback } from "react";
import { Card, CardContent } from "./components/ui/card";
import { Button } from "./components/ui/button";
import { triggerHapticFeedback, HAPTIC_TYPES } from '../src/utils/hapticFeedback';
import {
  BarChart2, Target, BookOpen, Clock, Medal,
  Trophy, Cog, X, Check, XCircle, Hash,
  Percent, Activity, Award, Star, Info,
  AlertCircle, PlayCircle, PauseCircle,
  Lock, Delete, ShoppingBag, Crown, Sparkles,
  Ban, Infinity, Zap
} from "lucide-react";
import { Alert, AlertDescription } from "./components/ui/alert";
import { motion, AnimatePresence } from "framer-motion";
import HeaderSection from './HeaderSection';
import SettingsModal from "./SettingsModal";
import RollingBanner from './RollingBanner';
import PurchaseManager from './lib/purchaseManager';
import { BannerItem } from './types/banner';
// import PremiumModal from './components/PremiumModal';

// 배너 아이템 데이터
const bannerItems: BannerItem[] = [
  {
    type: 'content' as const,
    text: "곱셈의 순서를 바꾸어도 결과는 같아요!",
    icon: "🎯",
    backgroundColor: "bg-blue-50",
    textColor: "text-blue-700"
  },
  {
    type: 'content' as const,
    text: "구구단 학습 방법 알아보기",
    icon: "📚",
    link: "https://smap.co.kr",
    backgroundColor: "bg-emerald-50",
    textColor: "text-emerald-700"
  },
  {
    type: 'content' as const,
    text: "0을 곱하면 항상 0이 되어요!",
    icon: "💡",
    backgroundColor: "bg-amber-50",
    textColor: "text-amber-700"
  },
  {
    type: 'content' as const,
    text: "1을 곱하면 수가 변하지 않아요",
    icon: "✨",
    backgroundColor: "bg-purple-50",
    textColor: "text-purple-700"
  },
  {
    type: 'content' as const,
    text: "2의 곱은 두 번 더하기와 같아요",
    icon: "🎨",
    link: "https://smap.co.kr/multiply-tips",
    backgroundColor: "bg-pink-50",
    textColor: "text-pink-700"
  },
  {
    type: 'content' as const,
    text: "5의 곱은 끝자리가 0 또는 5예요",
    icon: "🌟",
    backgroundColor: "bg-indigo-50",
    textColor: "text-indigo-700"
  },
  {
    type: 'ad' as const,
    adUnitId: 'your-ad-unit-id' // adUnitId 추가
  },
  {
    type: 'content' as const,
    text: "9의 곱? 10을 곱하고 1번 빼보세요!",
    icon: "🎮",
    backgroundColor: "bg-teal-50",
    textColor: "text-teal-700"
  },
  {
    type: 'content' as const,
    text: "오늘의 구구단 퀴즈 풀어보기",
    icon: "🎯",
    link: "https://smap.co.kr/quiz",
    backgroundColor: "bg-rose-50",
    textColor: "text-rose-700"
  },
  {
    type: 'content' as const,
    text: "매일 조금씩, 꾸준히 연습해요!",
    icon: "⭐",
    backgroundColor: "bg-orange-50",
    textColor: "text-orange-700"
  },
  {
    type: 'content' as const,
    text: "틀려도 괜찮아요, 다시 도전해보세요!",
    icon: "🌈",
    backgroundColor: "bg-cyan-50",
    textColor: "text-cyan-700"
  }
];

interface TableStats {
  [key: number]: {
    totalAttempts: number;
    correctAnswers: number;
    highScore: number;
  }
}

interface ConfirmDialogProps {
  show: boolean;
  message: string;
  onConfirm: () => void;
  onCancel: () => void;
}
interface AlertModal {
  show: boolean;
  message: string;
  type: 'success' | 'error' | 'info' | 'warning';
}

interface GameState {
  practiceHighestTable: number;
  timeAttackLevel: number;
  history: HistoryItem[];
  achievements: Achievement[];
}

interface HistoryItem {
  problem: string;
  userAnswer: number;
  correct: boolean;
  timestamp: Date;
  timeTaken: number;
  mode: 'practice' | 'timeAttack';
  table: number;
}

interface Achievement {
  name: string;
  description: string;
  unlocked: boolean;
}

interface ProblemCountSettingsProps {
  requiredProblems: number;
  onClose: () => void;
  onSelect: (count: number) => void;
  problemCountRef: React.RefObject<HTMLDivElement>;
}

interface TimerSettingsModalProps {
  show: boolean;
  selectedTime: number;
  onClose: () => void;
  onTimeSelect: (time: number) => void;
}

interface TimeAttackResultDialogProps {
  show: boolean;
  success: boolean;
  message: string;
  timeAttackLevel: number;
  solvedProblems: number;
  requiredProblems: number;
  onClose: () => void;
  onRetry: () => void;
  onNext?: () => void;
}

const TimeAttackResultDialog = ({
  show,
  success,
  message,
  timeAttackLevel,
  solvedProblems,
  requiredProblems,
  onClose,
  onRetry,
  onNext
}: TimeAttackResultDialogProps) => {
  if (!show) return null;

  const progressPercentage = (solvedProblems / requiredProblems) * 100;

  return (
    <div className="fixed inset-0 flex items-center justify-center z-[100]">
      <div className="absolute inset-0 bg-black bg-opacity-50" />
      <motion.div
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        exit={{ opacity: 0, scale: 0.95 }}
        className="relative bg-white rounded-xl p-6 max-w-sm mx-4 shadow-xl"
      >
        <div className="mb-4">
          {success ? (
            <div className="w-16 h-16 mx-auto mb-4 rounded-full bg-green-100 flex items-center justify-center">
              <Trophy className="w-8 h-8 text-green-500" />
            </div>
          ) : (
            <div className="w-16 h-16 mx-auto mb-4 rounded-full bg-amber-100 flex items-center justify-center">
              <Clock className="w-8 h-8 text-amber-500" />
            </div>
          )}
          <h3 className="text-xl font-suite font-bold text-center mb-2">
            {success ? '축하합니다! 🎉' : '아쉽네요! 😢'}
          </h3>
          <p className="text-center text-gray-600 whitespace-pre-line">{message}</p>
        </div>

        {/* 진행률 표시 */}
        <div className="mb-6">
          <div className="flex justify-between text-sm mb-1">
            <span className="text-gray-500">진행률</span>
            <span className="font-suite font-medium text-indigo-600">{solvedProblems}/{requiredProblems}</span>
          </div>
          <div className="w-full h-2 bg-gray-100 rounded-full overflow-hidden">
            <div
              className={`h-full rounded-full transition-all duration-500 ${success ? 'bg-green-500' : 'bg-amber-500'
                }`}
              style={{ width: `${progressPercentage}%` }}
            />
          </div>
        </div>

        <div className="flex flex-col gap-3">
          {success ? (
            <>
              <Button
                variant="ghost"
                onClick={onNext}
                className="w-full bg-green-500 hover:bg-green-600 text-white font-suite font-bold py-3"
              >
                {timeAttackLevel + 1}단 도전하기
              </Button>
              <Button
                variant="outline"
                onClick={onClose}
                className="w-full border-green-500 text-green-600 hover:bg-green-50"
              >
                연습 모드로 돌아가기
              </Button>
            </>
          ) : (
            <>
              <Button
                variant="ghost"
                onClick={onRetry}
                className="w-full bg-indigo-500 hover:bg-indigo-600 text-white font-suite font-bold py-3"
              >
                다시 도전하기
              </Button>
              <Button
                variant="outline"
                onClick={onClose}
                className="w-full border-gray-300 text-gray-700 hover:bg-gray-50"
              >
                연습 모드로 돌아가기
              </Button>
            </>
          )}
        </div>
      </motion.div>
    </div>
  );
};

// 별도의 컴포넌트로 분리
const ProblemCountSettings = React.memo(({
  requiredProblems,
  onClose,
  onSelect,
  problemCountRef
}: ProblemCountSettingsProps) => {
  const countOptions = [10, 15, 20];
  ProblemCountSettings.displayName = 'ProblemCountSettings'; // display name 추가
  return (
    <motion.div
      ref={problemCountRef}  // ref 전달
      initial={{ opacity: 0, y: -20 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -20 }}
      className="absolute top-full left-0 mt-2 bg-white p-4 rounded-lg shadow-lg z-50 w-48"
    >
      <div className="flex justify-between items-center mb-4">
        <h4 className="text-lg font-suite font-bold text-black">문제 수 설정</h4>
        <button
          onClick={onClose}
          className="text-gray-500 hover:text-gray-700"
        >
          <X className="h-4 w-4" />
        </button>
      </div>
      <div className="space-y-2">
        {countOptions.map((count) => (
          <Button
            key={count}
            variant={requiredProblems === count ? "default" : "outline"}
            onClick={() => onSelect(count)}
            className={`
              w-full flex items-center justify-between px-4 h-10
              ${requiredProblems === count ? 'bg-blue-500 hover:bg-blue-600 text-white' : 'hover:bg-gray-50'}
            `}
          >
            <div className="flex items-center gap-2">
              {requiredProblems === count && (
                <Check className="w-4 h-4 flex-shrink-0" />
              )}
              <span className="text-sm">{count}문제</span>
            </div>
          </Button>
        ))}
      </div>
    </motion.div>
  );
});

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
}

const MultiplicationGame = () => {
  // 정보 모달 상태들
  const [showScoreInfo, setShowScoreInfo] = useState(false);
  const [showStreakInfo, setShowStreakInfo] = useState(false);
  const [showTableInfo, setShowTableInfo] = useState(false);
  const [showTimerSettings, setShowTimerSettings] = useState(false);

  // 모달 외부 클릭 핸들러
  const handleClickOutside = useCallback((event: MouseEvent) => {
    if (showScoreInfo || showStreakInfo || showTableInfo || showTimerSettings) {
      setShowScoreInfo(false);
      setShowStreakInfo(false);
      setShowTableInfo(false);
      setShowTimerSettings(false);
    }
  }, [showScoreInfo, showStreakInfo, showTableInfo, showTimerSettings]);

  // 외부 클릭 이벤트 리스너
  useEffect(() => {
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [handleClickOutside]);

  // 새로운 색상 팔레트
  const colors = {
    primary: {
      light: '#6366f1', // Indigo-500
      DEFAULT: '#4f46e5', // Indigo-600
      dark: '#4338ca', // Indigo-700
    },
    secondary: {
      light: '#a855f7', // Purple-500
      DEFAULT: '#9333ea', // Purple-600
      dark: '#7e22ce', // Purple-700
    },
    accent: {
      light: '#ec4899', // Pink-500
      DEFAULT: '#db2777', // Pink-600
      dark: '#be185d', // Pink-700
    }
  };

  // 애니메이션 variants
  const fadeIn = {
    initial: { opacity: 0 },
    animate: { opacity: 1 },
    exit: { opacity: 0 }
  };

  const slideIn = {
    initial: { y: -20, opacity: 0 },
    animate: { y: 0, opacity: 1 },
    exit: { y: -20, opacity: 0 }
  };

  const buttonHover = {
    scale: 1.05,
    transition: { type: "spring", stiffness: 400, damping: 10 }
  };
  // 기본 게임 상태
  const [num1, setNum1] = useState(2);
  const [num2, setNum2] = useState(1);
  const [userAnswer, setUserAnswer] = useState("");
  const [score, setScore] = useState(0);
  const [streak, setStreak] = useState(0);
  const [selectedTable, setSelectedTable] = useState(2);
  const [showSettings, setShowSettings] = useState(false);
  const [showErrorAlert, setShowErrorAlert] = useState(false);
  const [errorAlertMessage, setErrorAlertMessage] = useState("");
  const [isClient, setIsClient] = useState(false);

  // 타임어택 관련 상태
  const [gameMode, setGameMode] = useState<'practice' | 'timeAttack'>('practice');
  const [timeLeft, setTimeLeft] = useState(45); // 45초로 변경
  const [solvedProblems, setSolvedProblems] = useState(0);
  const [isTimeAttackComplete, setIsTimeAttackComplete] = useState(false);

  // 사용된 문제 추적을 위한 state 수정
  const [usedProblems, setUsedProblems] = useState<Set<string>>(new Set());

  // 저장소 관련 상태
  const [practiceHighestTable, setPracticeHighestTable] = useState(2);
  const [timeAttackLevel, setTimeAttackLevel] = useState(2);
  const [history, setHistory] = useState<HistoryItem[]>([]);
  const [achievements, setAchievements] = useState<Achievement[]>([]);

  const [isPaused, setIsPaused] = useState(false);
  const [timerActive, setTimerActive] = useState(false);

  // 타임어택 통계를 위한 state 추가
  const [totalAttempts, setTotalAttempts] = useState(0);
  const [successfulAttempts, setSuccessfulAttempts] = useState(0);

  // 상태 추가
  const [selectedTime, setSelectedTime] = useState(45); // 기본값 45초

  // 최고 마스터 레벨 상태 추가
  const [masteredLevel, setMasteredLevel] = useState(1);

  const [showTableSelectModal, setShowTableSelectModal] = useState(false);  // 추가

  const [confirmDialog, setConfirmDialog] = useState<ConfirmDialogProps>({
    show: false,
    message: '',
    onConfirm: () => { },
    onCancel: () => { }
  });

  // 문제 수 설정 상태 
  const [showProblemCountSettings, setShowProblemCountSettings] = useState(false);
  const [requiredProblems, setRequiredProblems] = useState(15);
  const problemCountRef = useRef<HTMLDivElement>(null);
  // 모달 ref 추가
  const scoreInfoRef = useRef<HTMLDivElement>(null);
  const streakInfoRef = useRef<HTMLDivElement>(null);
  const tableInfoRef = useRef<HTMLDivElement>(null);
  const timerSettingsRef = useRef<HTMLDivElement>(null);
  const settingsRef = useRef<HTMLDivElement>(null); // 추가된 부분
  const tableSelectRef = useRef<HTMLDivElement>(null); // 추가  
  const cardRef = useRef<HTMLDivElement>(null);

  const prevTimeAttackLevel = useRef(timeAttackLevel);
  const prevGameMode = useRef(gameMode);

  const [isPremium, setIsPremium] = useState(false);

  // Premium 구매 핸들러 추가
  // const handlePurchase = async () => {
  //   try {
  //     const success = await PurchaseManager.savePurchaseStatus(true);
  //     if (success) {
  //       setIsPremium(true);
  //       showAlert('프리미엄으로 업그레이드 되었습니다! 🎉', 'success');
  //       setShowPremiumModal(false);
  //     }
  //   } catch (error) {
  //     showAlert('구매 중 오류가 발생했습니다', 'error');
  //   }
  // };

  // // Premium 상태 체크 effect 추가
  // useEffect(() => {
  //   const checkPurchaseStatus = async () => {
  //     const premium = await PurchaseManager.getPurchaseStatus();
  //     setIsPremium(premium);
  //   };

  //   checkPurchaseStatus();
  // }, []);

  // 타임어택 결과 다이얼로그 상태 추가
  const [timeAttackResult, setTimeAttackResult] = useState<{
    show: boolean;
    success: boolean;
    message: string;
  }>({
    show: false,
    success: false,
    message: '',
  });

  const getModalPosition = () => {
    if (cardRef.current) {
      const rect = cardRef.current.getBoundingClientRect();
      return {
        '--modal-top': `${rect.bottom + window.scrollY}px`,
        '--modal-left': `${rect.left}px`
      } as React.CSSProperties;
    }
    return {};
  };

  // 숫자패드 버튼에 애니메이션 추가를 위한 variants 설정
  const buttonVariants = {
    initial: { scale: 1 },
    hover: { scale: 1.05 },
    tap: { scale: 0.95 }
  };


  // 설정 핸들러
  const handleSettingsClick = () => {
    setShowSettings(true);
    setIsPaused(true); // 게임 일시정지
  };

  const handleCloseSettings = () => {
    setShowSettings(false);
    if (gameMode === 'timeAttack' && !isTimeAttackComplete) {
      setIsPaused(false); // 타임어택 모드에서만 재개
    }
  };

  const handleResetRecords = () => {
    showConfirmDialog(
      `${gameMode === 'practice' ? '연습모드' : '타임어택 모드'}의 모든 기록을 초기화하시겠습니까?`,
      () => {
        if (gameMode === 'practice') {
          setPracticeStats({});
          setScore(0);
          setStreak(0);
        } else {
          setTimeAttackLevel(2);
          setMasteredLevel(1);
          setTotalAttempts(0);
          setSuccessfulAttempts(0);
        }
        showAlert('기록이 초기화되었습니다.', 'info');

        // 확인 대화상자 닫기
        setConfirmDialog(prev => ({ ...prev, show: false }));
        // 설정 모달 닫기
        setShowSettings(false);
        // 게임 상태 저장
        saveGameState();
        // 일시정지 해제
        setIsPaused(false);
      }
    );
  };
  // 모달 외부 클릭 이벤트 리스너
  useEffect(() => {
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [handleClickOutside]);

  // 문제 수 표시 부분
  const handleProblemCountClick = useCallback(() => {
    setShowProblemCountSettings(true);
  }, []);


  const TimerSettingsModal: React.FC<TimerSettingsModalProps> = ({
    show,
    selectedTime,
    onClose,
    onTimeSelect
  }) => {
    if (!show) return null;

    const timeOptions = [45, 50, 55, 60];

    return (
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: -20 }}
        className="absolute top-full left-0 mt-2 bg-white p-4 rounded-xl shadow-lg z-50 w-48 border-2 border-indigo-100"
      >
        <div className="flex justify-between items-center mb-4">
          <h4 className="text-lg font-suite font-bold text-indigo-600">타이머 설정</h4>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600"
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
                ${selectedTime === time
                  ? 'bg-indigo-500 hover:bg-indigo-600 text-white'
                  : 'hover:bg-indigo-50 border-2'
                }
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
      </motion.div>
    );
  };

  // Update ScoreInfoModal component
  const ScoreInfoModal = () => (
    <div
      ref={scoreInfoRef}
      className="absolute top-full left-0 mt-2 bg-white p-4 rounded-lg shadow-lg z-50 w-64"
    >
      <div className="flex justify-between items-center mb-4">
        <h4 className="text-lg font-suite font-bold text-black">점수 기준</h4>
        <button
          onClick={() => setShowScoreInfo(false)}
          className="text-gray-500 hover:text-gray-700"
        >
          <X className="h-4 w-4" />
        </button>
      </div>
      <ul className="space-y-2 text-sm text-black">
        <li className="flex items-center gap-2">
          <Check className="w-4 h-4 text-green-500" />
          <span>정답: +10점</span>
        </li>
        <li className="flex items-center gap-2">
          <X className="w-4 h-4 text-red-500" />
          <span>오답: -15점</span>
        </li>
        <li className="flex items-center gap-2">
          <AlertCircle className="w-4 h-4 text-blue-500" />
          <span>최저 점수: 0점</span>
        </li>
      </ul>
    </div>
  );

  // Update StreakInfoModal component
  const StreakInfoModal = () => {
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
      <div
        ref={streakInfoRef}
        className="absolute top-full left-0 mt-2 bg-white p-4 rounded-lg shadow-lg z-50 w-64"
      >
        <div className="flex justify-between items-center mb-4">
          <h4 className="text-lg font-suite font-bold text-black">연속 정답</h4>
          <button
            onClick={() => setShowStreakInfo(false)}
            className="text-gray-500 hover:text-gray-700"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
        <div className="space-y-2 text-sm text-black">
          <div className="flex items-center gap-2">
            <Trophy className="w-4 h-4 text-yellow-500" />
            <span>최고 기록: {maxStreak}회</span>
          </div>
          <div className="flex items-center gap-2">
            <Target className="w-4 h-4 text-red-500" />
            <span>현재: {streak}회</span>
          </div>
        </div>
      </div>
    );
  };

  // Update TableInfoModal component
  const TableInfoModal = () => {
    const stats = practiceStats[selectedTable] || { attempts: 0, correct: 0 };
    const accuracy = stats.attempts > 0 ? Math.round((stats.correct / stats.attempts) * 100) : 0;

    return (
      <div
        ref={tableInfoRef}
        className="absolute top-full left-0 mt-2 bg-white p-4 rounded-lg shadow-lg z-50 w-64"
      >
        <div className="flex justify-between items-center mb-4">
          <h4 className="text-lg font-suite font-bold text-black">{selectedTable}단 통계</h4>
          <button
            onClick={() => setShowTableInfo(false)}
            className="text-gray-500 hover:text-gray-700"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
        <div className="space-y-2 text-sm text-black">
          <div className="flex items-center gap-2">
            <Hash className="w-4 h-4 text-violet-500" />
            <span>총 시도: {stats.attempts}회</span>
          </div>
          <div className="flex items-center gap-2">
            <Check className="w-4 h-4 text-green-500" />
            <span>정답: {stats.correct}회</span>
          </div>
          <div className="flex items-center gap-2">
            <Percent className="w-4 h-4 text-purple-500" />
            <span>정확도: {accuracy}%</span>
          </div>
        </div>
      </div>
    );
  };

  // 알림 모달 상태
  const [alertModal, setAlertModal] = useState<AlertModal>({
    show: false,
    message: '',
    type: 'info'
  });

  // 확인 대화상 표시 함수
  const showConfirmDialog = (message: string, onConfirm: () => void) => {
    setConfirmDialog({
      show: true,
      message,
      onConfirm,
      onCancel: () => {
        setConfirmDialog(prev => ({ ...prev, show: false }));
        setIsPaused(false);
      }
    });
  };

  // 연습 모드 통계를 위한 state 추가
  const [practiceStats, setPracticeStats] = useState<{
    [key: number]: {
      attempts: number;
      correct: number;
      lastPlayed: Date | null;
    }
  }>({});

  // 확인 대화상자 컴포넌트 수정
  const ConfirmDialog = () => {
    if (!confirmDialog.show) return null;

    return (
      <div className="fixed inset-0 flex items-center justify-center z-[100]">
        <div className="absolute inset-0 bg-black bg-opacity-50" />
        <div className="relative bg-white rounded-lg p-6 max-w-sm mx-4 shadow-xl">
          <h3 className="text-lg font-suite font-bold mb-4 text-black">{confirmDialog.message}</h3> {/* 텍스트 색상 변경 */}
          <div className="flex justify-between gap-4"> {/* 버튼 배치 수정 */}
            <Button
              variant="outline"
              onClick={confirmDialog.onCancel}
              className="w-1/2 px-4 border-blue-500 text-blue-500" // 파란색 border 추가
            >
              취소
            </Button>
            <Button
              variant="default"
              onClick={confirmDialog.onConfirm} // onConfirm 수정
              className="w-1/2 bg-blue-500 text-white hover:bg-blue-700 text-xl font-suite font-bold" // 파란색 배경 및 흰색 텍스트
            >
              확인
            </Button>
          </div>
        </div>
      </div>
    );
  };

  // showAlert 함수 수정
  const showAlert = (message: string, type: 'success' | 'error' | 'info' | 'warning' = 'info', onClose?: () => void) => {
    setAlertModal({
      show: true,
      message,
      type
    });

    setTimeout(() => {
      setAlertModal(prev => ({ ...prev, show: false }));
      onClose?.(); // 알림창이 닫힐 때 콜백 실행
    }, 1200); // 2000ms에서 1200ms로 줄임
  };

  // 클라이언트 사이드 마운트 확인
  useEffect(() => {
    setIsClient(true);
  }, []);

  // localStorage에서 게임 상태 불러올 때 연습 모드 통계도 함께 불러오기
  useEffect(() => {
    if (isClient) {
      try {
        const savedState = localStorage.getItem('multiplicationGame');
        if (savedState) {
          const state = JSON.parse(savedState);
          setMasteredLevel(state.masteredLevel || 1);
          setPracticeStats(state.practiceStats || {});
          setRequiredProblems(state.requiredProblems || 15); // requiredProblems 불러오기 추가
        }
      } catch (error) {
        console.error('Failed to load game state:', error);
      }
    }
  }, [isClient]);

  // 연습 모드 시작 메시지 배열 수정
  const practiceStartMessages = [
    "오늘도 구구단 연습 시작볼까요? 😊",
    "천천히 함께 연습해봐요! 📚",
    "구구단, 어렵지 않아요! 지금 시작해요! 🌟",
    "재미있게 구구단을 익혀봐요! 😄",
    "자, 준비되셨나요? 구구단 연습을 시작해요! 🚀",
  ];

  // 격려 메시지 배열 수정
  const encouragingMessages = [
    "훌륭해요! 이제 {n}단을 도전해봐요! ",
    "{n}단 연습을 시작합니다!\n함께 해봐요! 🎉",
    "{n}단, 어렵지 않아요!\n지금부터 시작해요! 🌟",
    "{n}단 마스터를 향해!\n힘내세요! 💪",
    "좋은 선택이에요!\n{n}단을 익혀봅시다! 😊",
  ];
  const timeAttackMessages = [
    "도전모드 시작!\n지금 바로 도전해보세요! ⏱️",
    "새로운 기록에 도전!\n자신의 한계를 시험해보세요! ⚡",
    "도전 정신을 발휘할 시간!\n최고 기록에 도전하세요! 🚀",
    "짜릿한 도전모드!\n준비되셨나요? 🏃‍♂️",
    "최고의 실력을 보여주세요!\n파이팅! 💥",
  ];



  // 연습 모드 시작 메시지 선택 함수
  const getRandomPracticeStartMessage = () => {
    const randomIndex = Math.floor(Math.random() * practiceStartMessages.length);
    return practiceStartMessages[randomIndex];
  };

  const [showHistoryReset, setShowHistoryReset] = useState(false);

  // 격려 메시지 선택 함수
  const getRandomEncouragingMessage = (tableNumber: number) => {
    const randomIndex = Math.floor(Math.random() * encouragingMessages.length);
    return encouragingMessages[randomIndex].replace('{n}', tableNumber.toString());
  };

  // 타임어택 시작 메시지
  const getRandomTimeAttackMessage = () => {
    const randomIndex = Math.floor(Math.random() * timeAttackMessages.length);
    return timeAttackMessages[randomIndex];
  };

  // 정답 체크 시 통계 업데이트
  const updatePracticeStats = (tableNumber: number, isCorrect: boolean) => {
    setPracticeStats(prev => {
      const currentStats = prev[tableNumber] || { attempts: 0, correct: 0, lastPlayed: null };
      return {
        ...prev,
        [tableNumber]: {
          attempts: currentStats.attempts + 1,
          correct: currentStats.correct + (isCorrect ? 1 : 0),
          lastPlayed: new Date()
        }
      };
    });
  };

  // saveGameState 함수도 수정하여 masteredLevel이 포함되도록
  const saveGameState = () => {
    if (isClient) {
      try {
        const state = {
          practiceHighestTable,
          timeAttackLevel,
          masteredLevel,
          history,
          achievements,
          totalAttempts,
          successfulAttempts,
          practiceStats,
          requiredProblems,
        };
        localStorage.setItem('multiplicationGame', JSON.stringify(state));
      } catch (error) {
        console.error('Failed to save game state:', error);
      }
    }
  };

  const handleCloseTableSelectModal = () => {
    setShowTableSelectModal(false);
    if (gameMode === 'timeAttack' && !isTimeAttackComplete) {
      setIsPaused(false); // 모달 닫힐 때 타이머 재개 (타임어택 완료되지 않은 경우에만)
    }
  };

  // handleModeChange 함수 수정
  const handleModeChange = (newMode: 'practice' | 'timeAttack') => {
    if (newMode === gameMode) return;

    if (newMode === 'timeAttack') {
      setGameMode('timeAttack');
      setTimeLeft(selectedTime);  // 선택된 시간으로 설정
      setSolvedProblems(0);
      setIsTimeAttackComplete(false);
      setTimerActive(true);
      setIsPaused(false);
      showAlert(getRandomTimeAttackMessage(), 'info');
      generateNewProblem();
    } else {
      setGameMode('practice');
      setTimerActive(false);
      setIsPaused(true);
      showAlert(getRandomPracticeStartMessage(), 'info', () => {
        generateNewProblem();
      });
    }
  };
  // generateNewProblem 함수 수정
  const generateNewProblem = useCallback(() => {
    const currentTable = gameMode === 'practice' ? selectedTable : timeAttackLevel;
    const availableNumbers = Array.from({ length: 18 }, (_, i) => i + 2)
      .filter(n => !usedProblems.has(`${currentTable}-${n}`));

    if (availableNumbers.length === 0) {
      const newNum2 = Math.floor(Math.random() * 18) + 2;
      setNum1(currentTable);
      setNum2(newNum2);
      setUsedProblems(new Set([`${currentTable}-${newNum2}`]));
    } else {
      const randomIndex = Math.floor(Math.random() * availableNumbers.length);
      const newNum2 = availableNumbers[randomIndex];
      setNum1(currentTable);
      setNum2(newNum2);
      const updatedUsedProblems = new Set(usedProblems);
      updatedUsedProblems.add(`${currentTable}-${newNum2}`);
      setUsedProblems(updatedUsedProblems);
    }
    setUserAnswer("");
  }, [gameMode, selectedTable, timeAttackLevel, usedProblems]);

  const handleNumberInput = (num: number) => {
    // 타임어택 모드에서 일시정지 상태일 때 자동 시작
    if (gameMode === 'timeAttack' && isPaused && !isTimeAttackComplete) {
      setIsPaused(false);
      setTimerActive(true);
    }

    if (userAnswer.length < 3) {
      const newAnswer = userAnswer + num;
      setUserAnswer(newAnswer);

      // setTimeout을 사용하여 상태 업데이트가 UI에 반영될 시간을 줍니다
      setTimeout(() => {
        const currentAnswer = parseInt(newAnswer);
        const correctAnswer = num1 * num2;

        // 입력한 숫자가 정답과 자릿수가 같거나 더 큰 경우에만 자동 체크
        if (newAnswer.length >= correctAnswer.toString().length) {
          checkAnswer(newAnswer, true);
        }
      }, 100); // 100ms의 지연 시간을 줍니다
    }
  };

  // 타이머 토글 함수 수정
  const toggleTimer = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();

    if (isPaused) {
      // 처음 시작할 때만 45초로 초기화 (timeLeft가 45초일 때)
      if (timeLeft === 45 && solvedProblems === 0) {
        setTimeLeft(45);
        setSolvedProblems(0);
        setIsTimeAttackComplete(false);
        generateNewProblem();
      }
      setTimerActive(true);
      setIsPaused(false);
      showAlert('타이머 시작!', 'info');
    } else {
      // 일시정지할 때는 현재 시간 유지
      setTimerActive(false);
      setIsPaused(true);
      showAlert('일시정지!', 'info');
    }
  };

  const handleTimeAttackLevelSelect = useCallback(() => {
    if (gameMode !== 'timeAttack') return;
    setShowTableSelectModal(true);
    setIsPaused(true);
  }, [gameMode, setShowTableSelectModal, setIsPaused]);

  // resetTimeAttack 함수 수정
  const resetTimeAttack = useCallback(() => {
    setTimeLeft(selectedTime);
    setSolvedProblems(0);
    setIsTimeAttackComplete(false);
    setUsedProblems(new Set());
    setIsPaused(true);
    setTimerActive(false);
  }, [selectedTime]);

  // handleTimeAttackEnd 함수 수정
  const handleTimeAttackEnd = useCallback((success: boolean) => {
    console.log('handleTimeAttackEnd 호출됨', { success }); // 디버깅용

    setTimerActive(false);
    setIsPaused(true);
    setIsTimeAttackComplete(true);
    setTotalAttempts(prev => prev + 1);

    let message = '';
    if (success) {
      setSuccessfulAttempts(prev => prev + 1);
      const nextLevel = timeAttackLevel + 1;
      setMasteredLevel(current => Math.max(current, timeAttackLevel));
      message = `${timeAttackLevel}단을 완벽하게 마스터했어요!\n다음은 ${nextLevel}단이에요.\n준비되셨나요?`;
    } else {
      if (solvedProblems === 0) {
        message = `아직 문제를 풀지 못했어요.\n${timeAttackLevel}단을 천천히 시작해봐요!`;
      } else {
        message = `아쉽네요! ${solvedProblems}/${requiredProblems} 문제를 해결했어요.\n다음에 다시 도전해보세요!`;
      }
    }

    // 결과 다이얼로그 표시
    setTimeAttackResult({
      show: true,
      success,
      message,
    });

    // 게임 상태 저장
    saveGameState();
  }, [timeAttackLevel, solvedProblems, requiredProblems]);

  // 결과 다이얼로그 핸들러들 수정
  const handleCloseTimeAttackResult = useCallback(() => {
    setTimeAttackResult({ show: false, success: false, message: '' });
    setGameMode('practice');
    resetTimeAttack();
    generateNewProblem();
  }, [resetTimeAttack, generateNewProblem]);

  const handleRetryTimeAttack = useCallback(() => {
    setTimeAttackResult({ show: false, success: false, message: '' });
    resetTimeAttack();
    generateNewProblem();
  }, [resetTimeAttack, generateNewProblem]);

  const handleNextLevel = useCallback(() => {
    setTimeAttackResult({ show: false, success: false, message: '' });
    setTimeAttackLevel(prev => prev + 1);
    resetTimeAttack();
    generateNewProblem();
  }, [resetTimeAttack, generateNewProblem]);

  // Modify the useEffect to handle problem generation
  useEffect(() => {
    if (gameMode === 'timeAttack' && !isTimeAttackComplete) {
      generateNewProblem();
    } else if (gameMode === 'practice') {
      generateNewProblem();
    }
  }, [timeAttackLevel, gameMode, selectedTable, isTimeAttackComplete]);


  // 타이머 설정 버튼 클릭 핸들러 수정
  const handleTimeSelect = (time: number) => {
    setSelectedTime(time);
    setTimeLeft(time);  // 즉시 현재 타이머 값 변경
    setTimerActive(false);  // 타이머 일시 정지
    setIsPaused(true);
    setIsTimeAttackComplete(false);
    setSolvedProblems(0);
    setUsedProblems(new Set());
    generateNewProblem();
    showAlert(`${time}초로 설정되었습니다! ⏰`, 'info');
    setShowTimerSettings(false);
  };

  const handleCountSelect = useCallback((count: number) => {
    if (gameMode === 'timeAttack' && !isPaused && !isTimeAttackComplete) {
      showAlert('게임 진행 중에는\n문제 수를 변경할 수 없어요!', 'warning');
      return;
    }
    setRequiredProblems(count);
    setShowProblemCountSettings(false);
    showAlert(`목표 문제 수가 ${count}개로 변경되었습니다! 🎯`, 'info');
    if (gameMode === 'timeAttack') {
      resetTimeAttack();
    }
  }, [gameMode, isPaused, isTimeAttackComplete, resetTimeAttack]);

  const handleProblemCountClose = useCallback(() => {
    setShowProblemCountSettings(false);
  }, []);

  const handleProblemCountSelect = useCallback((count: number) => {
    setRequiredProblems(count);
    setShowProblemCountSettings(false);

    // 게임 진행 중일 때의 처리
    if (gameMode === 'timeAttack') {
      if (solvedProblems >= count) {
        // 이미 새로운 목표를 달성한 경우
        setIsTimeAttackComplete(true);
        handleTimeAttackEnd(true);
      } else {
        // 아직 목표를 달성하지 못한 경우
        showAlert(`목표가 ${count}개로 변경되었습니다! 🎯`, 'info');
      }
    } else {
      showAlert(`목표 문제 수가 ${count}개로 변경되었습니다! 🎯`, 'info');
    }

    // 게임 상태 저장
    const updatedGameState = {
      ...JSON.parse(localStorage.getItem('multiplicationGame') || '{}'),
      requiredProblems: count
    };
    localStorage.setItem('multiplicationGame', JSON.stringify(updatedGameState));
  }, [gameMode, solvedProblems, handleTimeAttackEnd]);

  useEffect(() => {
    // 컴포넌트 마운트 시 테스트
    triggerHapticFeedback(HAPTIC_TYPES.IMPACT_HEAVY);
  }, []);

  // timeLeft가 0이 되었을 때 한 번만 실행되도록 useEffect 수정
  useEffect(() => {
    if (timeLeft === 0 && gameMode === 'timeAttack' && !isTimeAttackComplete) {
      handleTimeAttackEnd(false);
    }
  }, [timeLeft, gameMode, isTimeAttackComplete]);


  // Update checkAnswer function to save time attack records
  const checkAnswer = (answer: string = userAnswer, isAutoCheck: boolean = false) => {
    // 타임어택 모드에서 이미 완료된 경우 추가 답변 처리하지 않음
    if (gameMode === 'timeAttack' && isTimeAttackComplete) {
      return;
    }

    if (!answer || isNaN(parseInt(answer))) return;

    const userInput = parseInt(answer);
    const correct = num1 * num2 === userInput;

    // Check if the answer was already processed
    const isAlreadyAnswered = history.some(item =>
      item.problem === `${num1} × ${num2}` &&
      item.userAnswer === userInput &&
      Date.now() - new Date(item.timestamp).getTime() < 1000
    );

    if (isAlreadyAnswered) return;

    // Save record
    const newHistory: HistoryItem = {
      problem: `${num1} × ${num2}`,
      userAnswer: userInput,
      correct,
      timestamp: new Date(),
      timeTaken: 0,
      mode: gameMode,
      table: num1
    };

    setHistory(prev => [newHistory, ...prev]);

    // 약간의 지연 후에 다음 동작을 실행합니다
    setTimeout(() => {
      if (gameMode === 'practice') {
        updatePracticeStats(selectedTable, correct);

        if (correct) {
          triggerHapticFeedback(HAPTIC_TYPES.SUCCESS);
          setScore(prev => prev + 10);
          setStreak(prev => prev + 1);
          setUserAnswer("");
          generateNewProblem();
        } else {
          triggerHapticFeedback(HAPTIC_TYPES.ERROR);
          setScore(prev => Math.max(0, prev - 15));
          setStreak(0);
          setUserAnswer("");
          if (!isAutoCheck) {
            showAlert("틀렸습니다. 다시 시도해보세요!", 'error');
          }
        }
      } else { // 타임어택 모드
        if (correct) {
          triggerHapticFeedback(HAPTIC_TYPES.SUCCESS);
          setUserAnswer("");

          // 다음 문제 수를 먼저 계산
          const nextSolvedCount = solvedProblems + 1;

          // 목표 달성 체크
          if (nextSolvedCount === requiredProblems) {
            setSolvedProblems(nextSolvedCount);
            setIsTimeAttackComplete(true);
            handleTimeAttackEnd(true);
            saveGameState();
            return;
          }

          // 아직 목표에 도달하지 않은 경우
          setSolvedProblems(nextSolvedCount);
          generateNewProblem();

          // Save time attack progress
          saveGameState();
        } else {
          triggerHapticFeedback(HAPTIC_TYPES.ERROR);
          setUserAnswer("");
          if (!isAutoCheck) {
            showAlert("틀렸습니다. 다시 시도해보세요!", 'error');
          }
          generateNewProblem();
        }
      }

      // practice 모드일 때만 마지막에 저장
      if (gameMode === 'practice') {
        saveGameState();
      }
    }, 100); // 100ms의 지연 시간을 줍니다
  };

  // 키보드 입력 핸들러 수정
  useEffect(() => {
    const handleKeyPress = (event: KeyboardEvent) => {
      if (event.key >= '0' && event.key <= '9') {
        if (userAnswer.length < 3) {
          const newKey = parseInt(event.key);
          handleNumberInput(newKey); // handleNumberInput 함수를 재사용
        }
      } else if (event.key === 'Backspace') {
        setUserAnswer(prev => prev.slice(0, -1));
      } else if (event.key === 'Enter' && userAnswer) {
        checkAnswer();
      }
    };

    window.addEventListener('keydown', handleKeyPress);
    return () => window.removeEventListener('keydown', handleKeyPress);
  }, [userAnswer, num1, num2]); // 의존성 추가

  // Combine effects into one
  useEffect(() => {
    // Initial setup
    setIsClient(true);

    // Problem generation
    if (isClient) {
      if (
        gameMode === 'timeAttack' &&
        !isTimeAttackComplete &&
        (timeAttackLevel !== prevTimeAttackLevel.current ||
          gameMode !== prevGameMode.current)
      ) {
        generateNewProblem();
      }
      prevTimeAttackLevel.current = timeAttackLevel;
      prevGameMode.current = gameMode;
    }

    // Timer logic
    let timer: NodeJS.Timeout;

    const shouldRunTimer =
      gameMode === 'timeAttack' &&
      !isPaused &&
      timeLeft > 0 &&
      !isTimeAttackComplete;

    if (shouldRunTimer) {
      timer = setInterval(() => {
        setTimeLeft(prev => {
          const next = prev - 1;
          if (next === 0) {
            handleTimeAttackEnd(false);
          }
          return next;
        });
      }, 1000);
    }

    return () => {
      if (timer) {
        clearInterval(timer);
      }
    };
  }, [
    isClient,
    gameMode,
    timeAttackLevel,
    isPaused,
    timeLeft,
    isTimeAttackComplete,
    generateNewProblem,
    handleTimeAttackEnd
  ]);

  if (!isClient) {
    return null; // 또는 로딩 상태를 표시
  }


  // UI 렌더링
  return (
    <div className="max-w-md mx-auto p-4 min-h-screen bg-gradient-to-b from-gray-50 to-gray-100">
      {/* Alert 모달에서 Activity 아이콘 사용 */}
      <AnimatePresence>
        {alertModal.show && (
          // Alert 모달 스타일 개선 (더 부드러운 전환을 위해)
          <motion.div
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.95 }}
            transition={{ duration: 0.15 }}
            className="fixed inset-0 flex items-center justify-center z-50"
          >
            <div className="absolute inset-0 bg-black bg-opacity-50" />
            <motion.div
              initial={{ opacity: 0, y: -10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              transition={{ duration: 0.15 }}
              className={`
      relative z-10 p-6 rounded-xl shadow-lg max-w-sm mx-4 backdrop-blur-sm
      ${alertModal.type === 'success' ? 'bg-green-100/90 border border-green-300' :
                  alertModal.type === 'error' ? 'bg-red-100/90 border border-red-300' :
                    'bg-blue-100/90 border border-blue-300'}
    `}
            >
              <div className="flex items-center gap-x-3">
                {alertModal.type === 'success' ? (
                  <Check className="h-8 w-8 text-green-500" />
                ) : alertModal.type === 'error' ? (
                  <XCircle className="h-8 w-8 text-red-500" />
                ) : (
                  <Activity className="h-8 w-8 text-violet-500" />
                )}
                <p className={`text-lg font-suite font-medium whitespace-pre-line
        ${alertModal.type === 'success' ? 'text-green-700' :
                    alertModal.type === 'error' ? 'text-red-700' :
                      'text-blue-700'}
      `}>
                  {alertModal.message}
                </p>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* 확인 대화상자 */}
      <ConfirmDialog />

      {/* 타임어택 결과 다이얼로그 추가 */}
      <AnimatePresence>
        {timeAttackResult.show && (
          <TimeAttackResultDialog
            show={timeAttackResult.show}
            success={timeAttackResult.success}
            message={timeAttackResult.message}
            timeAttackLevel={timeAttackLevel}
            solvedProblems={solvedProblems}
            requiredProblems={requiredProblems}
            onClose={handleCloseTimeAttackResult}
            onRetry={handleRetryTimeAttack}
            onNext={timeAttackResult.success ? handleNextLevel : undefined}
          />
        )}
      </AnimatePresence>

      {/* 헤더 부분만 수정 */}
      <HeaderSection
        // 상태 props
        gameMode={gameMode}
        score={score}
        streak={streak}
        selectedTable={selectedTable}
        timeLeft={timeLeft}
        solvedProblems={solvedProblems}
        requiredProblems={requiredProblems}
        timeAttackLevel={timeAttackLevel}
        isPaused={isPaused}
        showScoreInfo={showScoreInfo}
        showStreakInfo={showStreakInfo}
        showTableInfo={showTableInfo}
        showTimerSettings={showTimerSettings}
        selectedTime={selectedTime}
        masteredLevel={masteredLevel}
        practiceStats={practiceStats}
        history={history}
        timerActive={timerActive}
        isTimeAttackComplete={isTimeAttackComplete}
        setTimerActive={setTimerActive}
        setIsPaused={setIsPaused}
        setTimeLeft={setTimeLeft}
        setSolvedProblems={setSolvedProblems}
        setIsTimeAttackComplete={setIsTimeAttackComplete}
        showAlert={showAlert}

        // 이벤트 핸들러 props
        onModeChange={handleModeChange}
        onSettingsClick={handleSettingsClick}
        onScoreClick={() => setShowScoreInfo(true)}
        onStreakClick={() => setShowStreakInfo(true)}
        onTableClick={() => setShowTableInfo(true)}
        onTableSelectClick={handleTimeAttackLevelSelect}
        onProblemCountClick={handleProblemCountClick}
        onTimerSettingsClick={() => setShowTimerSettings(true)}
        onTimerToggle={toggleTimer}
        onTimeSelect={handleTimeSelect}
        onScoreInfoClose={() => setShowScoreInfo(false)}
        onStreakInfoClose={() => setShowStreakInfo(false)}
        onTableInfoClose={() => setShowTableInfo(false)}
        onTimerSettingsClose={() => setShowTimerSettings(false)}
        showProblemCountSettings={showProblemCountSettings}
        setShowTimerSettings={setShowTimerSettings}
        setShowProblemCountSettings={setShowProblemCountSettings}

        // 누락된 props 추가
        showTableSelectModal={showTableSelectModal}
        setShowTableSelectModal={setShowTableSelectModal}
        setUsedProblems={setUsedProblems}
        resetTimeAttack={resetTimeAttack}
        generateNewProblem={generateNewProblem}
        usedProblems={usedProblems}
        setTimeAttackLevel={setTimeAttackLevel}
        setSelectedTable={setSelectedTable}
        isPremium={isPremium}
        setIsPremium={setIsPremium}
      />

      <AnimatePresence>
        {showSettings && (
          <SettingsModal
            show={showSettings}
            onClose={() => setShowSettings(false)}
            gameMode={gameMode}
            selectedTable={selectedTable}
            setSelectedTable={setSelectedTable}
            timeAttackLevel={timeAttackLevel}
            masteredLevel={masteredLevel}
            totalAttempts={totalAttempts}
            successfulAttempts={successfulAttempts}
            practiceStats={practiceStats}
            onResetRecords={handleResetRecords}
            generateNewProblem={generateNewProblem}  // 추가
          />
        )}
      </AnimatePresence>

      <div className="bg-white/50 p-3 rounded-xl backdrop-blur-sm mb-4 relative shadow-lg border border-indigo-100/50 z-[1]">
        <div className="bg-white/80 rounded-lg p-4 shadow-sm">
          {/* 최근 기록 표시 - 카드 형태로 변경 */}
          <div className="h-7 mb-1"> {/* 이 살짝 증가 */}
            {history.length > 0 && (
              <motion.div
                initial={{ opacity: 0, y: -10 }}
                animate={{ opacity: 1, y: 0 }}
                className={`
                        inline-flex items-center px-2.5 py-1 rounded-lg text-sm font-suite font-semibold
                        ${history[0].correct
                    ? 'bg-green-50 text-green-700 border border-green-200'
                    : 'bg-red-50 text-red-700 border border-red-200'
                  }
                    `}
              >
                {history[0].correct ? (
                  <Check className="w-4 h-4 mr-1.5 flex-shrink-0" />
                ) : (
                  <X className="w-4 h-4 mr-1.5 flex-shrink-0" />
                )}
                <span>
                  {history[0].problem} = {history[0].userAnswer}
                </span>
              </motion.div>
            )}
          </div>

          {/* 문제 표시 */}
          <div className="text-5xl font-suite font-bold text-center mb-4 py-2 text-indigo-600">
            {num1} × {num2} = {userAnswer || "_"}
          </div>

          {/* 키패드 그리드 */}
          <div className="grid grid-cols-3 gap-2 scale-90 transform origin-top">
            {/* 1-9까지 숫자 */}
            {[1, 2, 3, 4, 5, 6, 7, 8, 9].map((num) => (
              <motion.button
                key={num}
                variants={buttonVariants}
                initial="initial"
                whileHover="hover"
                whileTap="tap"
                className="h-12 bg-gradient-to-b from-white to-indigo-50 
              text-indigo-600 rounded-lg text-xl font-suite font-bold
              shadow-sm hover:shadow-md border-2 border-indigo-100
              hover:border-indigo-300 hover:from-indigo-50 
              hover:to-indigo-100 active:scale-95 transition-all"
                onClick={() => handleNumberInput(num)}
              >
                {num}
              </motion.button>
            ))}

            {/* 지우기 버튼 */}
            <motion.button
              variants={buttonVariants}
              initial="initial"
              whileHover="hover"
              whileTap="tap"
              className="h-12 bg-gradient-to-b from-white to-rose-50 
          text-rose-600 rounded-lg text-xl font-suite font-bold shadow-sm 
          hover:shadow-md border-2 border-rose-200
          hover:border-rose-300 hover:from-rose-50 hover:to-rose-100"
              onClick={() => setUserAnswer(userAnswer.slice(0, -1))}
            >
              <Delete className="w-5 h-5 mx-auto" />
            </motion.button>

            {/* 0 버튼 */}
            <motion.button
              variants={buttonVariants}
              initial="initial"
              whileHover="hover"
              whileTap="tap"
              className="h-12 bg-gradient-to-b from-white to-indigo-50 
          text-indigo-600 rounded-lg text-xl font-suite font-bold shadow-sm 
          hover:shadow-md border-2 border-indigo-100
          hover:border-indigo-300 hover:from-indigo-50 
          hover:to-indigo-100"
              onClick={() => handleNumberInput(0)}
            >
              0
            </motion.button>

            {/* 확인 버튼 */}
            <motion.button
              variants={buttonVariants}
              initial="initial"
              whileHover="hover"
              whileTap="tap"
              className={`h-12 rounded-lg text-xl font-suite font-bold shadow-sm 
        hover:shadow-md transition-all border-2
        ${userAnswer
                  ? 'bg-gradient-to-b from-indigo-500 to-indigo-600 text-white border-indigo-400 hover:border-indigo-500 hover:from-indigo-600 hover:to-indigo-700'
                  : 'bg-gradient-to-b from-gray-50 to-gray-100 text-gray-400 border-gray-200 cursor-not-allowed'
                }`}
              onClick={() => checkAnswer()}
              disabled={!userAnswer}
            >
              확인
            </motion.button>

          </div>
          <RollingBanner items={bannerItems} />
        </div>
      </div>
    </div >
  );
};

export default MultiplicationGame;