"use client"
import React, { useState, useEffect, useRef, useCallback } from "react";
import { Button } from "./components/ui/button";
import { triggerHapticFeedback, HAPTIC_TYPES } from '../src/utils/hapticFeedback';
import { showInterstitialAd } from '../src/utils/adManager';
import {
  BarChart2, Target, BookOpen, Clock, Medal,
  Trophy, Cog, X, Check, XCircle, Hash,
  Percent, Activity, Award, Star, Info,
  AlertCircle, PlayCircle, PauseCircle,
  Lock, Delete, ShoppingBag, Crown, Sparkles,
  Ban, Zap
} from "lucide-react";
import { Alert, AlertDescription } from "./components/ui/alert";
import { motion, AnimatePresence } from "framer-motion";
import HeaderSection from './HeaderSection';
import SettingsModal from "./SettingsModal";
import RollingBanner from './RollingBanner';
import PurchaseManager from './lib/purchaseManager';
import { BannerItem } from './types/banner';
import ComboAnimation from './ComboAnimation';
import { Map as MapIcon, Volume2, VolumeX, Flame } from "lucide-react";
import { useGameProgress } from './game/useGameProgress';
import Confetti from './game/Confetti';
import Mascot, { MascotMood } from './game/Mascot';
import XPBar from './game/XPBar';
import ComboGauge from './game/ComboGauge';
import LevelUpOverlay from './game/LevelUpOverlay';
import AchievementToast from './game/AchievementToast';
import ProgressMap from './game/ProgressMap';
import { AchievementDef } from './game/achievements';
import * as sound from './game/soundManager';
// import PremiumModal from './components/PremiumModal';
import * as gtag from '../src/utils/gtag'

// 배너 아이템 데이터
const bannerItems: BannerItem[] = [
  {
    type: 'content' as const,
    text: "우리 아이 위치를 실시간으로 확인하세요",
    icon: "📍",
    link: "https://smap.co.kr",
    backgroundColor: "bg-blue-50",
    textColor: "text-blue-700"
  },
  {
    type: 'content' as const,
    text: "자녀의 등하교 도착 알림을 받아보세요",
    icon: "🏫",
    link: "https://smap.co.kr/function",
    backgroundColor: "bg-emerald-50",
    textColor: "text-emerald-700"
  },
  {
    type: 'content' as const,
    text: "학원, 학교 스케줄을 한눈에 관리해요",
    icon: "📅",
    link: "https://smap.co.kr/function",
    backgroundColor: "bg-rose-50",
    textColor: "text-rose-700"
  },
  {
    type: 'content' as const,
    text: "안전한 등하교 경로 추천받기",
    icon: "🚸",
    link: "https://smap.co.kr/function",
    backgroundColor: "bg-amber-50",
    textColor: "text-amber-700"
  },
  {
    type: 'content' as const,
    text: "우리 아이 이동 기록 한눈에 보기",
    icon: "📱",
    link: "https://smap.co.kr",
    backgroundColor: "bg-purple-50",
    textColor: "text-purple-700"
  },
  {
    type: 'content' as const,
    text: "SMAP으로 우리 가족 안전 지키기",
    icon: "👨‍👩‍👧‍👦",
    link: "https://apps.apple.com/us/app/smap-location-history-plans/id6480279658",
    backgroundColor: "bg-pink-50",
    textColor: "text-pink-700"
  },
  {
    type: 'content' as const,
    text: "아이 스케줄 시작 전 알림 받기",
    icon: "⏰",
    link: "https://smap.co.kr/function",
    backgroundColor: "bg-indigo-50",
    textColor: "text-indigo-700"
  },
  {
    type: 'content' as const,
    text: "자녀 안전, SMAP과 함께하세요",
    icon: "💝",
    link: "https://apps.apple.com/us/app/smap-location-history-plans/id6480279658",
    backgroundColor: "bg-teal-50",
    textColor: "text-teal-700"
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

// 키패드 숫자 버튼 색상 (Clay 토이 톤 순환) — 완전한 클래스명으로 작성해 Tailwind purge 방지
const PAD_TONES = [
  'bg-clay-blue shadow-clay-blue',
  'bg-clay-yellow shadow-clay-yellow',
  'bg-clay-pink shadow-clay-pink',
];

// 마스코트 말풍선 메시지
const PRAISE_MESSAGES = ['최고야! 🎉', '대단해!', '잘했어! ✨', '정답!', '멋지다!', '완벽해!'];
const ENCOURAGE_MESSAGES = ['괜찮아, 다시!', '할 수 있어!', '천천히 해보자', '거의 다 왔어!'];

// 진행률에 따른 색상을 결정하는 함수
const getProgressColor = (solved: number, required: number) => {
  const percentage = (solved / required) * 100;

  if (percentage < 30) {
    return {
      bg: 'bg-rose-500',
      from: 'from-rose-500/10',
      text: 'text-rose-500'
    };
  } else if (percentage < 60) {
    return {
      bg: 'bg-amber-500',
      from: 'from-amber-500/10',
      text: 'text-amber-500'
    };
  } else if (percentage < 90) {
    return {
      bg: 'bg-emerald-500',
      from: 'from-emerald-500/10',
      text: 'text-emerald-500'
    };
  } else {
    return {
      bg: 'bg-indigo-500',
      from: 'from-indigo-500/10',
      text: 'text-indigo-500'
    };
  }
};

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
      <div className="absolute inset-0 bg-clay-ink/40 backdrop-blur-sm" />
      <motion.div
        initial={{ opacity: 0, scale: 0.8, y: 20 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.8, y: 20 }}
        transition={{ type: 'spring', stiffness: 300, damping: 20 }}
        className="clay-card relative p-6 max-w-sm mx-4"
      >
        <div className="mb-4">
          {success ? (
            <motion.div
              animate={{ rotate: [-8, 8, -8] }}
              transition={{ duration: 0.8, repeat: Infinity, ease: 'easeInOut' }}
              className="w-20 h-20 mx-auto mb-4 rounded-3xl bg-clay-mint-light/40 border-[3px] border-white shadow-clay-mint flex items-center justify-center"
            >
              <Trophy className="w-10 h-10 text-clay-mint-dark" />
            </motion.div>
          ) : (
            <div className="w-20 h-20 mx-auto mb-4 rounded-3xl bg-clay-yellow-light/40 border-[3px] border-white shadow-clay-yellow flex items-center justify-center">
              <Clock className="w-10 h-10 text-clay-yellow-dark" />
            </div>
          )}
          <h3 className="text-2xl font-suite font-extrabold text-center mb-2 text-clay-ink">
            {success ? '축하합니다! 🎉' : '아쉽네요! 😢'}
          </h3>
          <p className="text-center text-clay-muted font-suite font-medium whitespace-pre-line">{message}</p>
        </div>

        {/* 진행률 표시 */}
        <div className="mb-6">
          <div className="flex justify-between text-sm mb-1">
            <span className="text-clay-muted font-bold">진행률</span>
            <span className="num-display font-extrabold text-clay-blue">{solvedProblems}/{requiredProblems}</span>
          </div>
          <div className="w-full h-3 bg-clay-bg rounded-full overflow-hidden border-2 border-white shadow-clay-pressed">
            <motion.div
              className={`h-full rounded-full gauge-stripes ${success ? 'bg-clay-mint' : 'bg-clay-yellow'}`}
              initial={{ width: 0 }}
              animate={{ width: `${progressPercentage}%` }}
              transition={{ type: 'spring', stiffness: 120, damping: 18 }}
            />
          </div>
        </div>

        <div className="flex flex-col gap-3">
          {success ? (
            <>
              <Button
                variant="ghost"
                onClick={onNext}
                className="clay-btn w-full bg-clay-mint text-white shadow-clay-mint font-suite py-3"
              >
                {timeAttackLevel + 1}단 도전하기
              </Button>
              <Button
                variant="outline"
                onClick={onClose}
                className="clay-btn w-full bg-white text-clay-mint-dark shadow-clay-sm"
              >
                연습 모드로 돌아가기
              </Button>
            </>
          ) : (
            <>
              <Button
                variant="ghost"
                onClick={onRetry}
                className="clay-btn w-full bg-clay-blue text-white shadow-clay-blue font-suite py-3"
              >
                다시 도전하기
              </Button>
              <Button
                variant="outline"
                onClick={onClose}
                className="clay-btn w-full bg-white text-clay-muted shadow-clay-sm"
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

TimeAttackResultDialog.displayName = 'TimeAttackResultDialog';

// 별도의 컴포넌트로 분리
const ProblemCountSettings = React.memo(({
  requiredProblems,
  onClose,
  onSelect,
  problemCountRef
}: ProblemCountSettingsProps) => {
  const countOptions = [10, 15, 20];

  return (
    <motion.div
      ref={problemCountRef}
      initial={{ opacity: 0, y: -20 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -20 }}
      className="absolute top-full left-0 mt-2 bg-white p-4 rounded-t-2xl rounded-b-lg shadow-lg z-50 w-48 border-2 border-indigo-100"
    >
      {/* 상단 장식 바 추가 */}
      <div className="absolute -top-1 left-1/2 transform -translate-x-1/2">
        <div className="w-12 h-1 bg-gray-300 rounded-full" />
      </div>

      <div className="flex justify-between items-center mb-4">
        <h4 className="text-lg font-suite font-bold text-indigo-600">문제 수 설정</h4>
        <button
          onClick={onClose}
          className="text-gray-400 hover:text-gray-600 transition-colors"
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
              ${requiredProblems === count
                ? 'bg-indigo-500 hover:bg-indigo-600 text-white'
                : 'hover:bg-indigo-50 border-2 border-indigo-100'}
              transition-all duration-200
            `}
          >
            <div className="flex items-center gap-2">
              {requiredProblems === count && (
                <Check className="w-4 h-4 flex-shrink-0" />
              )}
              <span className="text-sm font-medium">{count}문제</span>
            </div>
          </Button>
        ))}
      </div>
    </motion.div>
  );
});

ProblemCountSettings.displayName = 'ProblemCountSettings';

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
  const [correctAnswerCount, setCorrectAnswerCount] = useState(0);  // 추가된 부분

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
  const [combo, setCombo] = useState(0);

  // 게임 진행/연출 상태 (XP·레벨·업적·별점·사운드·연출)
  const game = useGameProgress();
  const [mascotMood, setMascotMood] = useState<MascotMood>('idle');
  const [mascotMessage, setMascotMessage] = useState<string | undefined>(undefined);
  const [confettiTrigger, setConfettiTrigger] = useState(0);
  const [levelUpShow, setLevelUpShow] = useState(false);
  const [levelUpLevel, setLevelUpLevel] = useState(1);
  const [achievementIncoming, setAchievementIncoming] = useState<AchievementDef[] | null>(null);
  const [showProgressMap, setShowProgressMap] = useState(false);
  const [soundOn, setSoundOn] = useState(true);
  const [wrongShake, setWrongShake] = useState(false);

  // 정답 연출: XP/업적/레벨업/사운드/마스코트/컨페티 통합
  const celebrateCorrect = useCallback((newCombo: number) => {
    const table = gameMode === 'practice' ? selectedTable : timeAttackLevel;
    const result = game.recordCorrect({ mode: gameMode, combo: newCombo, table });
    sound.playCorrect();
    if (newCombo >= 3) sound.playCombo(newCombo);
    setMascotMood('happy');
    if (newCombo >= 2) setMascotMessage(PRAISE_MESSAGES[newCombo % PRAISE_MESSAGES.length]);
    // 5콤보 단위로 컨페티 폭죽
    if (newCombo >= 5 && newCombo % 5 === 0) setConfettiTrigger(t => t + 1);
    if (result.unlocked.length) {
      setAchievementIncoming(result.unlocked);
      sound.playStar();
    }
    if (result.leveledUp) {
      sound.playLevelUp();
      setLevelUpLevel(result.newLevel);
      setLevelUpShow(true);
      setConfettiTrigger(t => t + 1);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [game, gameMode, selectedTable, timeAttackLevel]);

  // 오답 연출
  const onWrong = useCallback(() => {
    sound.playWrong();
    setMascotMood('sad');
    setMascotMessage(ENCOURAGE_MESSAGES[Math.floor(Math.random() * ENCOURAGE_MESSAGES.length)]);
    setWrongShake(true);
    setTimeout(() => setWrongShake(false), 500);
  }, []);

  // 사운드 토글
  const toggleSound = useCallback(() => {
    const next = !sound.isSoundEnabled();
    sound.setSoundEnabled(next);
    setSoundOn(next);
  }, []);

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
  const handleSettingsClick = useCallback(() => {
    setShowSettings(true);
    setIsPaused(true);
  }, []);

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
        <div className="absolute inset-0 bg-clay-ink/40 backdrop-blur-sm" />
        <motion.div
          initial={{ opacity: 0, scale: 0.85, y: 16 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          transition={{ type: 'spring', stiffness: 320, damping: 20 }}
          className="clay-card relative p-6 max-w-sm mx-4"
        >
          <h3 className="text-lg font-suite font-extrabold mb-5 text-clay-ink whitespace-pre-line text-center">{confirmDialog.message}</h3>
          <div className="flex justify-between gap-3">
            <Button
              variant="outline"
              onClick={confirmDialog.onCancel}
              className="clay-btn w-1/2 bg-white text-clay-muted shadow-clay-sm"
            >
              취소
            </Button>
            <Button
              variant="default"
              onClick={confirmDialog.onConfirm}
              className="clay-btn w-1/2 bg-clay-blue text-white shadow-clay-blue text-lg font-suite"
            >
              확인
            </Button>
          </div>
        </motion.div>
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
    "구구단, 어렵지 않아요!\n 지금 시작해요! 🌟",
    "재미있게 구구단을 익혀봐요! 😄",
    "자, 준비되셨나요?\n 구구단 연습을 시작해요! 🚀",
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




  // generateNewProblem 함수 수정
  const generateNewProblem = useCallback(() => {
    const currentTable = gameMode === 'practice' ? selectedTable : timeAttackLevel;

    // 최대 곱할 수 결정
    let maxMultiplier;
    if (currentTable <= 9) {
      // 2~9단은 곱하기 9까지
      maxMultiplier = 9;
    } else {
      // 11단 이상은 자기 자신까지
      maxMultiplier = currentTable;
    }

    // 곱할 수 범위 생성 (2부터 maxMultiplier까지)
    const availableNumbers = Array.from({ length: maxMultiplier - 1 }, (_, i) => i + 2)
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
    sound.playClick();
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

  // 핵심 게임 핸들러
  const handleModeChange = useCallback((newMode: 'practice' | 'timeAttack') => {
    setCombo(0);
    if (newMode === gameMode) return;

    if (newMode === 'timeAttack') {
      setGameMode('timeAttack');
      setTimeLeft(selectedTime);
      setSolvedProblems(0);
      setIsTimeAttackComplete(false);
      setTimerActive(true);
      setIsPaused(false);
      showAlert(getRandomTimeAttackMessage(), 'info');
      generateNewProblem();
      setTimerActive(false);
      setIsPaused(true);
    } else {
      setGameMode('practice');
      setTimerActive(false);
      setIsPaused(true);
      showAlert(getRandomPracticeStartMessage(), 'info', () => {
        generateNewProblem();
      });
    }
  }, [gameMode, selectedTime, generateNewProblem, showAlert]);

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
      // 남은 시간 비율로 별점 (50%+ ⭐⭐⭐, 25%+ ⭐⭐, 그 외 ⭐)
      const ratio = selectedTime > 0 ? timeLeft / selectedTime : 0;
      const stars = ratio >= 0.5 ? 3 : ratio >= 0.25 ? 2 : 1;
      const clear = game.recordTableClear(timeAttackLevel, stars);
      sound.playFanfare();
      sound.playStar();
      setConfettiTrigger(t => t + 1);
      setMascotMood('celebrate');
      if (clear.unlocked.length) setAchievementIncoming(clear.unlocked);
      const starText = '⭐'.repeat(stars);
      message = `${timeAttackLevel}단을 완벽하게 마스터했어요! ${starText}\n다음은 ${nextLevel}단이에요.\n준비되셨나요?`;
    } else {
      if (solvedProblems === 0) {
        message = `아직 문제를 풀지 못했어요.\n${timeAttackLevel}단을 천천히 시작해봐요!`;
      } else {
        message = `${solvedProblems}/${requiredProblems} 문제를 해결했어요.\n다음에 다시 도전해보세요!`;
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [timeAttackLevel, solvedProblems, requiredProblems, selectedTime, timeLeft, game]);

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

  const handleRequiredProblemsChange = useCallback((count: number) => {
    setRequiredProblems(count);
    if (gameMode === 'timeAttack' && solvedProblems >= count) {
      setIsTimeAttackComplete(true);
      handleTimeAttackEnd(true);
    }
  }, [gameMode, solvedProblems, handleTimeAttackEnd]);


  // 타이머 설정 버튼 클릭 핸들러 수정
  const handleTimeSelect = useCallback((time: number) => {
    setSelectedTime(time);
    setTimeLeft(time);
    setTimerActive(false);
    setIsPaused(true);
    setIsTimeAttackComplete(false);
    setSolvedProblems(0);
    setUsedProblems(new Set());
    generateNewProblem();
    showAlert(`${time}초로 설정되었습니다! ⏰`, 'info');
    setShowTimerSettings(false);
  }, [generateNewProblem]);

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
    // 사운드 설정 초기화
    sound.initSound();
    setSoundOn(sound.isSoundEnabled());
  }, []);

  // timeLeft가 0이 되었을 때 한 번만 실행되도록 useEffect 수정
  useEffect(() => {
    if (timeLeft === 0 && gameMode === 'timeAttack' && !isTimeAttackComplete) {
      handleTimeAttackEnd(false);
    }
  }, [timeLeft, gameMode, isTimeAttackComplete]);


  // Update checkAnswer function to save time attack records
  const checkAnswer = useCallback((answer: string = userAnswer, isAutoCheck: boolean = false) => {
    if (gameMode === 'timeAttack' && isTimeAttackComplete) return;
    if (!answer || isNaN(parseInt(answer))) return;

    const userInput = parseInt(answer);
    const correct = num1 * num2 === userInput;

    const isAlreadyAnswered = history.some(item =>
      item.problem === `${num1} × ${num2}` &&
      item.userAnswer === userInput &&
      Date.now() - new Date(item.timestamp).getTime() < 1000
    );

    if (isAlreadyAnswered) return;

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

    setTimeout(() => {
      if (correct) {
        setCombo(prev => prev + 1);
        celebrateCorrect(combo + 1);

        if ((combo + 1) % 5 === 0) {
          triggerHapticFeedback(HAPTIC_TYPES.IMPACT_HEAVY);
        } else {
          triggerHapticFeedback(HAPTIC_TYPES.SUCCESS);
        }

        // correctAnswerCount 업데이트를 함수형 업데이트로 변경
        setCorrectAnswerCount(prev => {
          const newCount = prev + 1;
          if (newCount >= 3 && !isPremium) {
            showInterstitialAd();
            return 0;  // 광고 표시 후 카운트 리셋
          }
          return newCount;
        });
      }

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
          onWrong();
          setScore(prev => Math.max(0, prev - 15));
          setStreak(0);
          setUserAnswer("");
          setCombo(0);
          if (!isAutoCheck) {
            showAlert("틀렸습니다. 다시 시도해보세요!", 'error');
          }
        }
        saveGameState();
      } else {
        if (correct) {
          if ((combo + 1) % 5 !== 0) {
            triggerHapticFeedback(HAPTIC_TYPES.SUCCESS);
          }
          setUserAnswer("");

          const nextSolvedCount = solvedProblems + 1;

          if (nextSolvedCount === requiredProblems) {
            setSolvedProblems(nextSolvedCount);
            setIsTimeAttackComplete(true);
            handleTimeAttackEnd(true);
            saveGameState();
            return;
          }

          setSolvedProblems(nextSolvedCount);
          generateNewProblem();
          saveGameState();
        } else {
          triggerHapticFeedback(HAPTIC_TYPES.ERROR);
          onWrong();
          setUserAnswer("");
          setCombo(0);
          if (!isAutoCheck) {
            showAlert("틀렸습니다. 다시 시도해보세요!", 'error');
          }
          generateNewProblem();
        }
      }
    }, 100);
  }, [
    gameMode,
    isTimeAttackComplete,
    userAnswer,
    num1,
    num2,
    history,
    combo,
    isPremium,
    selectedTable,
    solvedProblems,
    requiredProblems,
    generateNewProblem,
    handleTimeAttackEnd,
    saveGameState,
    showAlert,
    updatePracticeStats,
    celebrateCorrect,
    onWrong
  ]);

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

  const handleTimerToggle = useCallback(() => {
    if (isPaused) {
      if (timeLeft === selectedTime && solvedProblems === 0) {
        setTimeLeft(selectedTime);
        setSolvedProblems(0);
        setIsTimeAttackComplete(false);
        generateNewProblem();
      }
      setTimerActive(true);
      setIsPaused(false);
      showAlert('타이머 시작!', 'info');
    } else {
      setTimerActive(false);
      setIsPaused(true);
      showAlert('일시정지!', 'info');
    }
  }, [isPaused, timeLeft, selectedTime, solvedProblems, generateNewProblem]);

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
    <div className="max-w-md mx-auto p-4 min-h-screen">
      {/* 게임 연출 오버레이 */}
      <Confetti trigger={confettiTrigger} />
      <AchievementToast incoming={achievementIncoming} />
      <LevelUpOverlay show={levelUpShow} level={levelUpLevel} onClose={() => setLevelUpShow(false)} />
      <ProgressMap
        show={showProgressMap}
        onClose={() => setShowProgressMap(false)}
        tableStars={game.tableStars}
        onSelectTable={(t) => {
          if (gameMode !== 'practice') handleModeChange('practice');
          setSelectedTable(t);
          setShowProgressMap(false);
        }}
      />

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
      relative z-10 p-5 rounded-clay border-[3px] border-white shadow-clay max-w-sm mx-4
      ${alertModal.type === 'success' ? 'bg-clay-mint-light/60' :
                  alertModal.type === 'error' ? 'bg-clay-pink-light/60' :
                    'bg-clay-blue-light/50'}
    `}
            >
              <div className="flex items-center gap-x-3">
                {alertModal.type === 'success' ? (
                  <Check className="h-8 w-8 text-clay-mint-dark" />
                ) : alertModal.type === 'error' ? (
                  <XCircle className="h-8 w-8 text-clay-pink-dark" />
                ) : (
                  <Activity className="h-8 w-8 text-clay-blue" />
                )}
                <p className={`text-lg font-suite font-extrabold whitespace-pre-line
        ${alertModal.type === 'success' ? 'text-clay-mint-dark' :
                    alertModal.type === 'error' ? 'text-clay-pink-dark' :
                      'text-clay-blue-dark'}
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

      {/* 상단 진행 바: 레벨/XP + 데일리 + 진행맵 + 사운드 */}
      <div className="mb-3 flex items-center gap-2">
        <div className="clay-card-sm min-w-0 flex-1 p-2.5">
          <XPBar levelInfo={game.levelInfo} compact />
        </div>
        {game.dailyStreak > 0 && (
          <div className="clay-card-sm flex shrink-0 items-center gap-1 px-2.5 py-2.5" title={`${game.dailyStreak}일 연속 플레이`}>
            <Flame className="h-4 w-4 text-orange-500" fill="currentColor" />
            <span className="num-display text-sm font-extrabold text-orange-500">{game.dailyStreak}</span>
          </div>
        )}
        <button
          onClick={() => setShowProgressMap(true)}
          aria-label="구구단 모험 지도"
          className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl border-[3px] border-white bg-clay-blue text-white shadow-clay-blue transition-transform active:translate-y-0.5"
        >
          <MapIcon className="h-5 w-5" />
        </button>
        <button
          onClick={toggleSound}
          aria-label={soundOn ? '소리 끄기' : '소리 켜기'}
          className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl border-[3px] border-white bg-white text-clay-muted shadow-clay-sm transition-transform active:translate-y-0.5"
        >
          {soundOn ? <Volume2 className="h-5 w-5 text-clay-blue" /> : <VolumeX className="h-5 w-5" />}
        </button>
      </div>

      {/* 헤더 부분만 수정 */}
      <HeaderSection
        // 게임 상태 props
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
        showProblemCountSettings={showProblemCountSettings}
        selectedTime={selectedTime}
        masteredLevel={masteredLevel}
        practiceStats={practiceStats}
        history={history}
        timerActive={timerActive}
        isTimeAttackComplete={isTimeAttackComplete}

        // 함수 props
        setTimerActive={setTimerActive}
        setIsPaused={setIsPaused}
        setTimeLeft={setTimeLeft}
        setSolvedProblems={setSolvedProblems}
        setIsTimeAttackComplete={setIsTimeAttackComplete}
        setShowProblemCountSettings={setShowProblemCountSettings}
        showAlert={showAlert}
        setShowTimerSettings={setShowTimerSettings}
        onRequiredProblemsChange={handleRequiredProblemsChange}

        // 이벤트 핸들러 props
        onModeChange={handleModeChange}
        onSettingsClick={handleSettingsClick}
        onScoreInfoClose={() => setShowScoreInfo(false)}
        onStreakInfoClose={() => setShowStreakInfo(false)}
        onTableInfoClose={() => setShowTableInfo(false)}
        onTimerSettingsClose={() => setShowTimerSettings(false)}
        onTimerToggle={handleTimerToggle}
        onTimeSelect={handleTimeSelect}
        onScoreClick={() => setShowScoreInfo(true)}
        onStreakClick={() => setShowStreakInfo(true)}
        onTableClick={() => setShowTableInfo(true)}
        onTableSelectClick={() => setShowTableSelectModal(true)}
        onProblemCountClick={() => setShowProblemCountSettings(true)}
        onTimerSettingsClick={() => setShowTimerSettings(true)}

        // 기타 props
        showTableSelectModal={showTableSelectModal}
        setShowTableSelectModal={setShowTableSelectModal}
        setUsedProblems={setUsedProblems}
        resetTimeAttack={resetTimeAttack}
        generateNewProblem={generateNewProblem}
        setTimeAttackLevel={setTimeAttackLevel}
        usedProblems={usedProblems}
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

      {/* ComboAnimation 추가 */}
      <ComboAnimation combo={combo} />

      {/* 전체 키패드 섹션 */}
      <div className="relative mb-2">
        {/* 키패드 컨테이너 (Clay 카드) */}
        <div className="clay-card mb-3 p-4">
          {/* 마스코트 + 최근 기록 */}
          <div className="mb-3 flex items-end justify-between gap-2">
            <Mascot mood={mascotMood} message={mascotMessage} size={68} />
            <div className="flex max-w-[60%] flex-col items-end gap-1.5">
              <AnimatePresence mode="popLayout" initial={false}>
                {history.slice(0, 2).map((record, index) => (
                  <motion.div
                    key={`${record.timestamp}-${index}`}
                    initial={{ opacity: 0, x: 60 }}
                    animate={{ opacity: 1, x: 0 }}
                    exit={{ opacity: 0, x: 60 }}
                    layout
                    transition={{ type: "spring", stiffness: 500, damping: 30, mass: 0.8 }}
                    className={`flex shrink-0 items-center rounded-clay-sm border-2 border-white px-3 py-1.5 text-sm font-suite font-extrabold shadow-clay-sm
                      ${record.correct ? 'bg-clay-mint-light/40 text-clay-mint-dark' : 'bg-clay-pink-light/40 text-clay-pink-dark'}`}
                  >
                    {record.correct ? (
                      <Check className="mr-1.5 h-4 w-4 flex-shrink-0" />
                    ) : (
                      <X className="mr-1.5 h-4 w-4 flex-shrink-0" />
                    )}
                    <span className="num-display">{record.problem} = {record.userAnswer}</span>
                  </motion.div>
                ))}
              </AnimatePresence>
            </div>
          </div>

          {/* 문제 표시 (말랑 카드 + 전환/오답 애니메이션) */}
          <motion.div
            key={`${num1}-${num2}`}
            initial={{ scale: 0.86, opacity: 0, y: 10 }}
            animate={{ scale: 1, opacity: 1, y: 0 }}
            transition={{ type: 'spring', stiffness: 300, damping: 20 }}
            className={`relative mb-3 overflow-hidden rounded-clay border-[3px] border-white bg-gradient-to-br from-clay-bg to-white p-5 shadow-clay-sm ${wrongShake ? 'animate-shake' : ''}`}
          >
            <div className="num-display flex items-center justify-center text-center text-5xl font-extrabold leading-none">
              <span className="text-clay-blue">{num1}</span>
              <span className="mx-1.5 text-clay-pink">×</span>
              <span className="text-clay-yellow-dark">{num2}</span>
              <span className="mx-1.5 text-clay-muted">=</span>
              <motion.span
                key={userAnswer || 'empty'}
                initial={{ scale: 0.5, opacity: 0 }}
                animate={{ scale: 1, opacity: 1 }}
                transition={{ type: 'spring', stiffness: 500, damping: 18 }}
                className={userAnswer ? 'text-clay-purple' : 'text-clay-border'}
              >
                {userAnswer || '?'}
              </motion.span>
            </div>
          </motion.div>

          {/* 콤보 게이지 */}
          <ComboGauge combo={combo} />

          {/* 키패드 그리드 (컬러 토이 버튼) */}
          <div className="grid grid-cols-3 gap-2.5">
            {/* 1-9 숫자 버튼 */}
            {[1, 2, 3, 4, 5, 6, 7, 8, 9].map((num) => {
              const tone = PAD_TONES[(num - 1) % 3];
              return (
                <motion.button
                  key={num}
                  whileTap={{ scale: 0.9, y: 3 }}
                  className={`clay-btn num-display h-16 text-2xl text-white ${tone}`}
                  onClick={() => handleNumberInput(num)}
                >
                  {num}
                </motion.button>
              );
            })}

            {/* 지우기 버튼 */}
            <motion.button
              whileTap={{ scale: 0.9, y: 3 }}
              className="clay-btn flex h-16 items-center justify-center bg-clay-pink-light text-clay-pink-dark shadow-clay-sm"
              onClick={() => setUserAnswer(userAnswer.slice(0, -1))}
              aria-label="지우기"
            >
              <Delete className="h-6 w-6" />
            </motion.button>

            {/* 0 버튼 */}
            <motion.button
              whileTap={{ scale: 0.9, y: 3 }}
              className="clay-btn num-display h-16 text-2xl text-white bg-clay-blue shadow-clay-blue"
              onClick={() => handleNumberInput(0)}
            >
              0
            </motion.button>

            {/* 확인 버튼 */}
            <motion.button
              whileTap={{ scale: 0.9, y: 3 }}
              className={`clay-btn h-16 text-xl font-extrabold ${userAnswer
                ? 'bg-clay-mint text-white shadow-clay-mint'
                : 'bg-clay-bg text-clay-border shadow-clay-pressed cursor-not-allowed'
                }`}
              onClick={() => checkAnswer()}
              disabled={!userAnswer}
            >
              확인
            </motion.button>
          </div>
        </div>

        {/* RollingBanner는 키패드 섹션 아래에 별도로 배치 */}
        <RollingBanner items={bannerItems} />
      </div>
    </div >
  );
};

// displayName 추가
MultiplicationGame.displayName = 'MultiplicationGame';

export default MultiplicationGame;