"use client"
import React, { useState, useEffect, useRef } from "react";
import { Card, CardContent } from "./components/ui/card";
import { Button } from "./components/ui/button";
import {
  BarChart2,
  Target,
  BookOpen,
  Clock,
  Medal,
  Trophy,
  Cog,
  X,
  Check,
  XCircle,
  Hash,
  Percent,
  Activity,
  Award,
  Star,
  Info,  // Info 아이콘 추가
  AlertCircle,  // 대체 아이콘 옵션
  PlayCircle,  // Play 아이콘 수정
  PauseCircle  // Pause 아이콘 수정
} from "lucide-react";
import { Alert, AlertDescription } from "./components/ui/alert";
import { motion, AnimatePresence } from "framer-motion";

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
  type: 'success' | 'error' | 'info';
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

const MultiplicationGame = () => {
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

  // 추가: 스코어 기준 설명을 위한 state
  const [showScoreInfo, setShowScoreInfo] = useState(false);
  const [showStreakInfo, setShowStreakInfo] = useState(false);
  const [showTableInfo, setShowTableInfo] = useState(false);

  const [confirmDialog, setConfirmDialog] = useState<ConfirmDialogProps>({
    show: false,
    message: '',
    onConfirm: () => { },
    onCancel: () => { }
  });

  // 모달 ref 추가
  const scoreInfoRef = useRef<HTMLDivElement>(null);
  const streakInfoRef = useRef<HTMLDivElement>(null);
  const tableInfoRef = useRef<HTMLDivElement>(null);

  // 모달 외부 클릭 감지를 위한 effect
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (showScoreInfo && scoreInfoRef.current && !scoreInfoRef.current.contains(event.target as Node)) {
        setShowScoreInfo(false);
      }
      if (showStreakInfo && streakInfoRef.current && !streakInfoRef.current.contains(event.target as Node)) {
        setShowStreakInfo(false);
      }
      if (showTableInfo && tableInfoRef.current && !tableInfoRef.current.contains(event.target as Node)) {
        setShowTableInfo(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [showScoreInfo, showStreakInfo, showTableInfo]);

  // ScoreInfoModal 컴포넌트 수정
  const ScoreInfoModal = () => (
    <div
      ref={scoreInfoRef}
      className="absolute top-full left-0 mt-2 bg-white p-4 rounded-lg shadow-lg z-50 w-64"
    >
      <h4 className="font-bold mb-2">점수 기준</h4>
      <ul className="space-y-2 text-sm">
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

  // 스트릭 정보 모달 수정
  const StreakInfoModal = () => {
    // 스트릭 계산 로직 수정
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
        <h4 className="font-bold mb-2">연속 정답</h4>
        <div className="space-y-2 text-sm">
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

  // 현재 단 정보 모달
  const TableInfoModal = () => {
    const stats = practiceStats[selectedTable] || { attempts: 0, correct: 0 };
    const accuracy = stats.attempts > 0 ? Math.round((stats.correct / stats.attempts) * 100) : 0;

    return (
      <div
        ref={tableInfoRef}
        className="absolute top-full left-0 mt-2 bg-white p-4 rounded-lg shadow-lg z-50 w-64"
      >
        <h4 className="font-bold mb-2">{selectedTable}단 통계</h4>
        <div className="space-y-2 text-sm">
          <div className="flex items-center gap-2">
            <Hash className="w-4 h-4 text-blue-500" />
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

  // 확인 대화상자 표시 함수
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

  // 확인 대화상자 컴포넌트
  const ConfirmDialog = () => {
    if (!confirmDialog.show) return null;

    return (
      <div className="fixed inset-0 flex items-center justify-center z-50">
        <div className="absolute inset-0 bg-black bg-opacity-50" />
        <div className="relative bg-white rounded-lg p-6 max-w-sm mx-4 shadow-xl">
          <h3 className="text-lg font-bold mb-4">{confirmDialog.message}</h3>
          <div className="flex justify-end gap-3">
            <Button
              variant="outline"
              onClick={confirmDialog.onCancel}
              className="px-4"
            >
              취소
            </Button>
            <Button
              variant="destructive"
              onClick={() => {
                confirmDialog.onConfirm();
                setConfirmDialog(prev => ({ ...prev, show: false }));
              }}
              className="px-4"
            >
              확인
            </Button>
          </div>
        </div>
      </div>
    );
  };

  // showAlert 함수 수정
  const showAlert = (message: string, type: 'success' | 'error' | 'info' = 'info', onClose?: () => void) => {
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
          // ... (이전 상태 복원)
          setPracticeStats(state.practiceStats || {});
        }
      } catch (error) {
        console.error('Failed to load game state:', error);
      }
    }
  }, [isClient]);

  // 연습 모드 시작 메시지 배열 수정
  const practiceStartMessages = [
    "차근차근 연습해볼까요? 👋",
    "실수해도 괜찮아요! 배우는 과정이니까요 💫",
    "오늘도 즐거운 구구단 연습! 함께 해봐요 ⭐",
    "천천히 해도 좋아요. 정확하게 풀어보아요! 🌟",
    "열심히 연습하다 보면 어느새 구구단 마스터! 💪",
    "편안한 마음으로 시작해볼까요? 🎯",
    "매일매일 조금씩! 오늘도 파이팅! ✨",
    "실력이 조금씩 늘어나고 있어요! 계속 해봐요! 🚀"
  ];

  // 연습 모드 시작 메시지 선택 함수
  const getRandomPracticeStartMessage = () => {
    const randomIndex = Math.floor(Math.random() * practiceStartMessages.length);
    return practiceStartMessages[randomIndex];
  };

  const [showHistoryReset, setShowHistoryReset] = useState(false);

  const encouragingMessages = [
    "좋아요! 이제 {n}단을 마스터해봐요! 💪",
    "{n}단을 선택하셨네요! 차근차근 해봐요! ⭐",
    "훌륭해요! {n}단 연습을 시작해볼까요? 🌟",
    "자신감을 가지세요! {n}단도 잘 할 수 있어요! 🎯",
    "천천히 해도 괜찮아요! {n}단을 함께 연습해보아요! 🌈",
    "잘 선택했어요! {n}단을 정복해봐요! 🚀",
    "한 문제씩 해결하다 보면 {n}단은 식은 죽 먹기! 🎮",
    "{n}단, 이제 시작해볼까요? 할 수 있어요! ✨",
  ];

  // 격려 메시지 선택 함수
  const getRandomEncouragingMessage = (tableNumber: number) => {
    const randomIndex = Math.floor(Math.random() * encouragingMessages.length);
    return encouragingMessages[randomIndex].replace('{n}', tableNumber.toString());
  };

  const timeAttackMessages = [
    "도전자가 나타났다! 🔥 45초 안에 15문제를 해결하라!",
    "진정한 구구단 마스터를 향한 도전! 준비됐나요? 💪",
    "시간과의 레이스 시작! 당신의 한계를 뛰어넘어보세요! ⚡",
    "구구단 챔피언에 도전하세요! 승리는 당신의 것! 🏆",
    "스피드와 정확성의 완벽한 조화를 보여주세요! 🎯",
    "45초의 운명을 건 대결! 당신의 실력을 증명하세요! ⭐",
    "시간제한 도전! 긴장된다고? 더 짜릿하지 않나요? 🚀",
    "더 높은 단계로 가는 길! 15문제를 정복하세요! 🌟",
    "진정한 구구단 고수의 길로! 이 도전을 받아들이시겠습니까? 🔥",
    "시간이 당신의 적이 될 수는 없습니다! 도전하세요! ✨"
  ];

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

  // 타이머 효과 수정
  useEffect(() => {
    let lastTickTime = Date.now();
    let timer: NodeJS.Timeout;

    const shouldRunTimer =
      gameMode === 'timeAttack' &&
      timerActive &&
      !isPaused &&
      !showSettings &&
      timeLeft > 0 &&
      !isTimeAttackComplete;

    if (shouldRunTimer) {
      lastTickTime = Date.now();

      timer = setInterval(() => {
        const now = Date.now();
        const deltaTime = now - lastTickTime;
        lastTickTime = now;

        setTimeLeft((prevTime) => {
          const decrease = Math.floor(deltaTime / 1000);
          const newTime = Math.max(0, prevTime - decrease);

          if (newTime <= 0) {
            clearInterval(timer);
            handleTimeAttackEnd(false);
            return 0;
          }
          return newTime;
        });
      }, 100);
    }

    return () => {
      if (timer) {
        clearInterval(timer);
      }
    };
  }, [gameMode, timerActive, isPaused, showSettings, isTimeAttackComplete]);

  // 저장 함수 수정
  const saveGameState = () => {
    if (isClient) {
      try {
        const state = {
          practiceHighestTable,
          timeAttackLevel,
          history,
          achievements,
          totalAttempts,
          successfulAttempts,
          practiceStats
        };
        localStorage.setItem('multiplicationGame', JSON.stringify(state));
      } catch (error) {
        console.error('Failed to save game state:', error);
      }
    }
  };

  // 설정 버튼 클릭 핸들러 수정
  const handleSettingsClick = () => {
    setShowSettings(true);
  };

  // 설정 닫기 핸들러 수정
  const handleCloseSettings = () => {
    setShowSettings(false);
  };

  // 모드 변경 핸들러 수정 (타이머 자동 시작)
  const handleModeChange = (newMode: 'practice' | 'timeAttack') => {
    if (newMode === gameMode) return;

    setUsedProblems(new Set());

    if (newMode === 'timeAttack') {
      setGameMode('timeAttack');
      setTimeLeft(45);
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

  // 타이머 효과 수정 (더 간단하게)
  useEffect(() => {
    let timer: NodeJS.Timeout;

    const shouldRunTimer =
      gameMode === 'timeAttack' &&
      !showSettings &&
      timeLeft > 0 &&
      !isTimeAttackComplete;

    if (shouldRunTimer) {
      timer = setInterval(() => {
        setTimeLeft(prev => {
          if (prev <= 0) {
            clearInterval(timer);
            handleTimeAttackEnd(false);
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    }

    return () => {
      if (timer) {
        clearInterval(timer);
      }
    };
  }, [gameMode, showSettings, isTimeAttackComplete]);

  // 새로운 문제 생성 함수 수정
  const generateNewProblem = () => {
    const currentTable = gameMode === 'practice' ? selectedTable : timeAttackLevel;

    // 가능한 숫자들 (1-15) 중에서 아직 사용하지 않은 것들만 필터링
    const availableNumbers = Array.from({ length: 15 }, (_, i) => i + 1)
      .filter(n => !usedProblems.has(`${currentTable}-${n}`));

    // 모든 숫자를 다 사용했다면 초기화
    if (availableNumbers.length === 0) {
      setUsedProblems(new Set());
      const newNum2 = Math.floor(Math.random() * 15) + 1;
      setNum1(currentTable);
      setNum2(newNum2);
      setUsedProblems(new Set([`${currentTable}-${newNum2}`]));
    } else {
      // 사용하지 않은 숫자 중 랜덤 선택
      const randomIndex = Math.floor(Math.random() * availableNumbers.length);
      const newNum2 = availableNumbers[randomIndex];
      setNum1(currentTable);
      setNum2(newNum2);
      setUsedProblems(prev => new Set([...prev, `${currentTable}-${newNum2}`]));
    }

    setUserAnswer("");
  };

  // 모드나 단 변경시 사용된 문제 초기화
  useEffect(() => {
    setUsedProblems(new Set());
    generateNewProblem();
  }, [selectedTable, gameMode, timeAttackLevel]);

  // 틀린 답 처리 수정
  const handleWrongAnswer = () => {
    const newHistory: HistoryItem = {
      problem: `${num1} × ${num2}`,
      userAnswer: parseInt(userAnswer),
      correct: false,
      timestamp: new Date(),
      timeTaken: 0,
      mode: gameMode,
      table: num1
    };

    setHistory(prev => [newHistory, ...prev]);
    showAlert("틀렸습니다. 다시 시도해보세요!", 'error');

    if (gameMode === 'practice') {
      setStreak(0);
    } else {
      // 타임어택 모드에서는 틀려도 계속 진행
      generateNewProblem();
    }

    setUserAnswer("");
    saveGameState();
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
  // 타임어택 종료 핸들러 수정
  const handleTimeAttackEnd = (success: boolean) => {
    setTimerActive(false);
    setIsPaused(true);
    setIsTimeAttackComplete(true);
    setTotalAttempts(prev => prev + 1);

    if (success) {
      setSuccessfulAttempts(prev => prev + 1);
      const nextLevel = timeAttackLevel + 1;
      showAlert(`축하합니다! ${timeAttackLevel}단을 마스터했습니다!\n다음 레벨: ${nextLevel}단`, 'success', () => {
        setTimeAttackLevel(nextLevel);
        resetTimeAttack();
      });
    } else {
      // 실패 메시지 개선
      const message = solvedProblems === 15
        ? '아쉽습니다! 시간이 초과되었습니다.'
        : `시간 초과! ${solvedProblems}개 문제 해결\n목표: 15개`;

      showAlert(message, 'error', () => {
        resetTimeAttack();
      });
    }

    saveGameState();
  };

  // 타임어택 리셋 함수 수정
  const resetTimeAttack = () => {
    setTimeLeft(45);
    setSolvedProblems(0);
    setIsTimeAttackComplete(false);
    setUsedProblems(new Set());
    setIsPaused(true); // 항상 일시정지 상태로 시작
    setTimerActive(false); // 타이머 비활성화
    generateNewProblem();
  };

  useEffect(() => {
    // 시간이 0이 되었을 때 자동으로 게임 종료
    if (timeLeft === 0 && gameMode === 'timeAttack' && !isTimeAttackComplete) {
      handleTimeAttackEnd(false);
    }
  }, [timeLeft, gameMode, isTimeAttackComplete]);
  // checkAnswer 함수 수정
  const checkAnswer = () => {
    if (!userAnswer) return;

    const correct = num1 * num2 === parseInt(userAnswer);
    const newHistory: HistoryItem = {
      problem: `${num1} × ${num2}`,
      userAnswer: parseInt(userAnswer),
      correct,
      timestamp: new Date(),
      timeTaken: 0,
      mode: gameMode,
      table: num1
    };

    if (gameMode === 'practice') {
      updatePracticeStats(selectedTable, correct);

      // 점수 계산 로직 수정
      if (correct) {
        setScore(prev => prev + 10); // 정답 시 10점 추가
        setStreak(prev => prev + 1);
        setUserAnswer("");
        generateNewProblem();
      } else {
        setScore(prev => Math.max(0, prev - 15)); // 오답 시 15점 감소, 최소값 0
        setStreak(0);
        handleWrongAnswer();
      }
    } else {
      // 타임어택 모드 로직은 그대로 유지
      if (correct) {
        const newSolved = solvedProblems + 1;
        setSolvedProblems(newSolved);
        setUserAnswer("");

        if (newSolved >= 15) {
          setIsTimeAttackComplete(true);
          handleTimeAttackEnd(true);
        } else {
          generateNewProblem();
        }
      } else {
        handleWrongAnswer();
      }
    }

    setHistory(prev => [newHistory, ...prev]);
    saveGameState();
  };

  // 기록 초기화 시 타이머 처리 추가
  const handleResetRecords = () => {
    setIsPaused(true);
    setTimerActive(false);
    showConfirmDialog(
      '정말 모든 기록을 초기화하시겠습니까?',
      () => {
        setTimeAttackLevel(2);
        setHistory([]);
        localStorage.setItem('multiplicationGame', JSON.stringify({
          practiceHighestTable,
          timeAttackLevel: 2,
          history: [],
          achievements
        }));
        showAlert('모든 기록이 초기화되었습니다.', 'info', () => {
          setShowSettings(false);
          setIsPaused(false);
          handleModeChange('timeAttack');
        });
      }
    );
  };


  // 키보드 입력 처리
  useEffect(() => {
    const handleKeyPress = (event: KeyboardEvent) => {
      if (event.key >= '0' && event.key <= '9') {
        setUserAnswer(prev => prev.length < 3 ? prev + event.key : prev);
      } else if (event.key === 'Backspace') {
        setUserAnswer(prev => prev.slice(0, -1));
      } else if (event.key === 'Enter') {
        checkAnswer();
      }
    };

    window.addEventListener('keydown', handleKeyPress);
    return () => window.removeEventListener('keydown', handleKeyPress);
  }, [userAnswer]);

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
                  <Activity className="h-8 w-8 text-blue-500" />
                )}
                <p className={`text-lg font-medium whitespace-pre-line
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

      {/* 헤더 부분만 수정 */}
      <header className="flex items-center gap-4 mb-8">
        <div className="grid grid-cols-12 gap-2 w-full">
          {gameMode === 'practice' ? (
            <>
              <div className="col-span-3 relative"> {/* relative 추가 */}
                <Button
                  variant="ghost"
                  className="w-full h-[54px] bg-white"
                  onClick={(e: React.MouseEvent) => {
                    e.stopPropagation();
                    setShowScoreInfo(!showScoreInfo);
                    setShowStreakInfo(false);
                    setShowTableInfo(false);
                  }}
                >
                  <div className="flex items-center justify-center w-full gap-3">
                    <BarChart2 className="w-6 h-6 text-indigo-500 flex-shrink-0" />
                    <span className="text-sm font-medium text-black tabular-nums">{score}</span>
                  </div>
                </Button>
                {showScoreInfo && <ScoreInfoModal />}
              </div>
              <div className="col-span-3 relative"> {/* relative 추가 */}
                <Button
                  variant="ghost"
                  className="w-full h-[54px] bg-white"
                  onClick={(e: React.MouseEvent) => {
                    e.stopPropagation();
                    setShowStreakInfo(!showStreakInfo);
                    setShowScoreInfo(false);
                    setShowTableInfo(false);
                  }}
                >
                  <div className="flex items-center justify-center w-full gap-3">
                    <Target className="w-6 h-6 text-red-500 flex-shrink-0" />
                    <span className="text-sm font-medium text-black">{streak}</span>
                  </div>
                </Button>
                {showStreakInfo && <StreakInfoModal />}
              </div>
              <div className="col-span-4 relative"> {/* relative 추가 */}
                <Button
                  variant="ghost"
                  className="w-full h-[54px] bg-white"
                  onClick={(e: React.MouseEvent) => {
                    e.stopPropagation();
                    setShowTableInfo(!showTableInfo);
                    setShowScoreInfo(false);
                    setShowStreakInfo(false);
                  }}
                >
                  <div className="flex items-center justify-center w-full gap-3">
                    <BookOpen className="w-6 h-6 text-blue-500 flex-shrink-0" />
                    <span className="text-sm font-medium text-black">{selectedTable}단</span>
                  </div>
                </Button>
                {showTableInfo && <TableInfoModal />}
              </div>
              <div className="col-span-2 flex justify-end">
                <Button
                  variant="outline"
                  size="icon"
                  onClick={handleSettingsClick}
                  className="h-[54px] w-[54px] flex items-center justify-center bg-white"
                >
                  <Cog className="h-6 w-6 text-black" />
                </Button>
              </div>
            </>
          ) : (
            <>
              <div className="col-span-3">
                <div className="flex items-center gap-3 bg-white h-[54px] px-4 rounded-lg shadow-sm justify-center">
                  <Clock className="w-6 h-6 text-red-500 flex-shrink-0" />
                  <span className="text-sm font-medium text-black tabular-nums">
                    {timeLeft}s
                  </span>
                </div>
              </div>
              <div className="col-span-3">
                <div className="flex items-center gap-3 bg-white h-[54px] px-4 rounded-lg shadow-sm justify-center">
                  <Medal className="w-6 h-6 text-amber-500 flex-shrink-0" />
                  <span className="text-sm font-medium text-black tabular-nums">{solvedProblems}/15</span>
                </div>
              </div>
              <div className="col-span-4">
                <div className="flex items-center gap-3 bg-white h-[54px] px-4 rounded-lg shadow-sm justify-center">
                  <Trophy className="w-6 h-6 text-indigo-500 flex-shrink-0" />
                  <span className="text-sm font-medium text-black">{timeAttackLevel}단</span>
                </div>
              </div>
              <div className="col-span-2 flex justify-end">
                <Button
                  variant="outline"
                  size="icon"
                  onClick={handleSettingsClick}
                  className="h-[54px] w-[54px] flex items-center justify-center bg-white"
                >
                  <Cog className="h-6 w-6 text-black" />
                </Button>
              </div>
            </>
          )}
        </div>
      </header>

      {/* 모드 선택 영역 - 간단하게 유지 */}
      <div className="mb-8">
        <div className="flex justify-center gap-4">
          <Button
            onClick={() => handleModeChange('practice')}
            variant={gameMode === 'practice' ? "default" : "outline"}
            className="px-6 py-2 min-w-[120px]"
          >
            연습모드
          </Button>
          <Button
            onClick={() => handleModeChange('timeAttack')}
            variant={gameMode === 'timeAttack' ? "default" : "outline"}
            className="px-6 py-2 min-w-[120px]"
          >
            타임어택
          </Button>
        </div>
      </div>

      {/* 설정 패널 수정 */}
      <AnimatePresence>
        {showSettings && (
          <motion.div
            initial={{ opacity: 0, y: -20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -20 }}
            className="fixed inset-x-0 top-0 z-40 mx-auto max-w-md p-4"
          >
            {gameMode === 'practice' ? (
              // 연습 모드 설정 패널 수정
              <Card className="bg-white/95 backdrop-blur shadow-lg">
                <CardContent className="p-4">
                  <div className="flex justify-between items-center mb-4">
                    <h3 className="text-lg font-bold text-black">구구단 선택</h3>
                    <button
                      onClick={handleCloseSettings}
                      className="w-8 h-8 rounded-full bg-gray-100 hover:bg-gray-200 flex items-center justify-center transition-colors"
                    >
                      ✕
                    </button>

                  </div>
                  {/* 현재 단 통계 */}
                  {practiceStats[selectedTable] && (
                    <div className="mb-6 bg-gray-50 rounded-lg p-4">
                      <h4 className="text-md font-semibold mb-3 text-gray-700">{selectedTable}단 통계</h4>
                      <div className="grid grid-cols-3 gap-4">
                        <div className="text-center">
                          <p className="text-sm text-black">시도</p>
                          <div className="flex items-center justify-center gap-1 mt-1">
                            <Hash className="w-4 h-4 text-blue-500" />
                            <p className="text-lg font-bold text-black">
                              {practiceStats[selectedTable].attempts}
                            </p>
                          </div>
                        </div>
                        <div className="text-center">
                          <p className="text-sm text-black">정답</p>
                          <div className="flex items-center justify-center gap-1 mt-1">
                            <Check className="w-4 h-4 text-green-500" />
                            <p className="text-lg font-bold text-black">
                              {practiceStats[selectedTable].correct}
                            </p>
                          </div>
                        </div>
                        <div className="text-center">
                          <p className="text-sm text-black">정확도</p>
                          <div className="flex items-center justify-center gap-1 mt-1">
                            <Percent className="w-4 h-4 text-purple-500" />
                            <p className="text-lg font-bold text-black">
                              {practiceStats[selectedTable].attempts > 0
                                ? Math.round((practiceStats[selectedTable].correct / practiceStats[selectedTable].attempts) * 100)
                                : 0}
                            </p>
                          </div>
                        </div>
                      </div>
                    </div>
                  )}
                  {/* 단 선택 버튼들 */}
                  <div className="grid grid-cols-4 gap-3 mb-6">
                    {Array.from({ length: 18 }, (_, i) => i + 2).map((table) => (
                      <Button
                        key={table}
                        variant={selectedTable === table ? "default" : "outline"}
                        onClick={() => {
                          setSelectedTable(table);
                          setShowSettings(false);
                          setUsedProblems(new Set());
                          showAlert(getRandomEncouragingMessage(table), 'success');
                          generateNewProblem();
                        }}
                        className={`
            h-12 text-base relative
            ${selectedTable === table ? 'bg-indigo-500 text-white' : ''}
          `}
                      >
                        <span>{table}단</span>
                        {practiceStats[table] && (
                          <span className="absolute -top-1 -right-1 w-3 h-3 bg-green-500 rounded-full"
                            title={`정확도: ${Math.round((practiceStats[table].correct / practiceStats[table].attempts) * 100)}%`}
                          />
                        )}
                      </Button>
                    ))}
                  </div>
                  {/* 초기화 버튼 추가 */}
                  <div className="border-t pt-4">
                    <Button
                      variant="destructive"
                      className="w-full"
                      onClick={() => {
                        setIsPaused(true);
                        showConfirmDialog(
                          '연습 모드의 모든 기록을 초기화하시겠습니까?\n(전체 히스토리는 유지됩니다)',
                          () => {
                            // 현재 히스토리 데이터 백업
                            const currentHistory = [...history];

                            // 연습 모드 데이터만 초기화
                            setPracticeStats({});
                            setScore(0);
                            setStreak(0);

                            // localStorage에 저장 - 히스토리는 유지
                            localStorage.setItem('multiplicationGame', JSON.stringify({
                              practiceHighestTable,
                              timeAttackLevel,
                              history: currentHistory,
                              achievements,
                              practiceStats: {},
                              totalAttempts,
                              successfulAttempts
                            }));

                            showAlert('연습 모드의 기록이 초기화되었습니다.', 'info');
                            setShowSettings(false);
                            setIsPaused(false);
                            generateNewProblem();
                          }
                        );
                      }}
                    >
                      연습 기록 초기화
                    </Button>
                  </div>
                </CardContent>
              </Card>
            ) : (
              <Card className="bg-white/95 backdrop-blur shadow-lg">
                <CardContent className="p-4">
                  <div className="flex justify-between items-center mb-4">
                    <h3 className="text-lg font-bold text-black">타임어택 기록</h3>
                    <button
                      onClick={handleCloseSettings}
                      className="w-8 h-8 rounded-full bg-gray-100 hover:bg-gray-200 flex items-center justify-center transition-colors"
                    >
                      ✕
                    </button>

                  </div>
                  <div className="space-y-4">
                    <div className="grid grid-cols-2 gap-4">
                      <div className="bg-gray-50 p-3 rounded-lg">
                        <p className="text-sm text-black">현재 레벨</p>
                        <div className="flex items-center gap-2">
                          <Trophy className="w-5 h-5 text-indigo-500" />
                          <p className="text-2xl font-bold text-black">{timeAttackLevel}단</p>
                        </div>
                      </div>
                      <div className="bg-gray-50 p-3 rounded-lg">
                        <p className="text-sm text-black">최고 레벨</p>
                        <div className="flex items-center gap-2">
                          <Award className="w-5 h-5 text-amber-500" />
                          <p className="text-2xl font-bold text-black">
                            {Math.max(timeAttackLevel, parseInt(localStorage.getItem('highestTimeAttackLevel') || '2'))}단
                          </p>
                        </div>
                      </div>
                    </div>
                    <div className="grid grid-cols-2 gap-4">
                      <div className="bg-gray-50 p-3 rounded-lg">
                        <p className="text-sm text-black">총 시도</p>
                        <div className="flex items-center gap-2">
                          <Target className="w-5 h-5 text-blue-500" />
                          <p className="text-2xl font-bold text-black">{totalAttempts}회</p>
                        </div>
                      </div>
                      <div className="bg-gray-50 p-3 rounded-lg">
                        <p className="text-sm text-black">성공</p>
                        <div className="flex items-center gap-2">
                          <Check className="w-5 h-5 text-green-500" />
                          <p className="text-2xl font-bold text-black">{successfulAttempts}회</p>
                        </div>
                      </div>
                    </div>
                    <div className="bg-gray-50 p-3 rounded-lg">
                      <p className="text-sm text-black">성공률</p>
                      <div className="flex items-center gap-2">
                        <Star className="w-5 h-5 text-yellow-500" />
                        <p className="text-2xl font-bold text-black">
                          {totalAttempts > 0 ? Math.round((successfulAttempts / totalAttempts) * 100) : 0}%
                        </p>
                      </div>
                    </div>
                    <Button
                      variant="destructive"
                      className="w-full"
                      onClick={() => {
                        setIsPaused(true);
                        showConfirmDialog(
                          '정말 모든 기록을 초기화하시겠습니까?',
                          () => {
                            setTimeAttackLevel(2);
                            setHistory([]);
                            setTotalAttempts(0);
                            setSuccessfulAttempts(0);
                            localStorage.setItem('multiplicationGame', JSON.stringify({
                              practiceHighestTable,
                              timeAttackLevel: 2,
                              history: [],
                              achievements,
                              totalAttempts: 0,
                              successfulAttempts: 0
                            }));
                            showAlert('모든 기록이 초기화되었습니다.', 'info');
                            setShowSettings(false);
                            setIsPaused(false);
                            handleModeChange('timeAttack');
                          }
                        );
                      }}
                    >
                      기록 초기화
                    </Button>
                  </div>
                </CardContent>
              </Card>
            )}
          </motion.div>
        )}
      </AnimatePresence>


      {/* 숫자패드 부분만 수정 */}
      <Card className="mb-6">
        <CardContent className="p-6">
          <div className="text-4xl font-bold text-center mb-6 text-gray-900">
            {num1} × {num2} = {userAnswer || "_"}
          </div>
          <div className="grid grid-cols-3 gap-2">
            {/* 1-9까지 숫자 */}
            {[1, 2, 3, 4, 5, 6, 7, 8, 9].map((num) => (
              <Button
                key={num}
                variant="outline"
                className="h-12 text-xl font-bold text-gray-900"
                onClick={() => {
                  if (userAnswer.length < 3) {
                    setUserAnswer(userAnswer + num);
                  }
                }}
              >
                {num}
              </Button>
            ))}

            {/* 지우기 버튼 */}
            <Button
              variant="outline"
              className="h-12 bg-blue-200 text-xl font-bold text-gray-900"
              onClick={() => setUserAnswer(userAnswer.slice(0, -1))}
            >
              ←
            </Button>

            {/* 0 버튼 */}
            <Button
              variant="outline"
              className="h-12 text-xl font-bold text-gray-900"
              onClick={() => {
                if (userAnswer.length < 3) {
                  setUserAnswer(userAnswer + '0');
                }
              }}
            >
              0
            </Button>

            {/* 확인 버튼 */}
            <Button
              variant="default"
              className="h-12 bg-indigo-400 text-white hover:bg-violet-600 text-xl font-bold"
              onClick={checkAnswer}
            >
              확인
            </Button>
          </div>
        </CardContent>
      </Card>


      {/* 히스토리 표시 수정 */}
      {
        history.length > 0 && (
          <Card className="mb-6">
            <CardContent className="p-4">
              <h3 className="font-bold mb-4">최근 기록</h3>
              <div className="space-y-2">
                {history.slice(0, 10).map((item, index) => (
                  <div
                    key={index}
                    className={`p-3 rounded-lg border ${item.correct
                      ? 'bg-green-50 border-green-200 text-green-700'
                      : 'bg-red-50 border-red-200 text-red-700'
                      } flex justify-between items-center`}
                  >
                    <div className="flex items-center gap-2">
                      {item.correct ? (
                        <Check className="w-5 h-5 text-green-500" />
                      ) : (
                        <X className="w-5 h-5 text-red-500" />
                      )}
                      <span>
                        {item.problem} = {item.userAnswer}
                      </span>
                    </div>
                    <span className="text-sm text-black">
                      {new Date(item.timestamp).toLocaleTimeString()}
                    </span>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        )
      }
    </div >
  );
};

export default MultiplicationGame;