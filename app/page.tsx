/* eslint-disable react-hooks/exhaustive-deps */
"use client"
import React, { useState, useEffect } from "react";
import { Card, CardContent } from "./components/ui/card";
import { Button } from "./components/ui/button";
import {
  Activity,
  Settings2,
  Sparkles,
  X,
  Check,
  Timer,
  ChevronRight,
  ChevronLeft,
  Award,
  Star,
  TrophyIcon
} from "lucide-react";
import { Alert, AlertDescription } from "./components/ui/alert";
import { motion, AnimatePresence } from "framer-motion";

const CHARACTER_THEMES = [
  { name: "Robot", color: "text-blue-500" },
  { name: "Dinosaur", color: "text-green-500" },
  { name: "Space Explorer", color: "text-purple-500" },
  { name: "Pirate", color: "text-orange-500" },
  { name: "Superhero", color: "text-red-500" },
];

const MultiplicationGame = () => {
  // 기존 상태 변수들...
  const [level, setLevel] = useState(1);
  const [unlockedCharacters, setUnlockedCharacters] = useState<string[]>([CHARACTER_THEMES[0].name]);
  const [currentCharacter, setCurrentCharacter] = useState(CHARACTER_THEMES[0]);
  const [achievements, setAchievements] = useState<string[]>([]);
  const [timeAttackMode, setTimeAttackMode] = useState(false);
  const [remainingTime, setRemainingTime] = useState(30);

  const [num1, setNum1] = useState(2);
  const [num2, setNum2] = useState(1);
  const [userAnswer, setUserAnswer] = useState("");
  const [score, setScore] = useState(0);
  const [streak, setStreak] = useState(0);
  const [selectedTable, setSelectedTable] = useState(2);
  const [showSettings, setShowSettings] = useState(false);
  const [usedProblems, setUsedProblems] = useState(new Set());
  const [history, setHistory] = useState<HistoryItem[]>([]);
  const [currentPage, setCurrentPage] = useState(0);
  const [showErrorAlert, setShowErrorAlert] = useState(false);
  const [startTime, setStartTime] = useState<Date | null>(null); // 타입 명시

  const tablesPerPage = 10;
  const totalTables = 18; // 2단부터 19단까지
  const totalPages = Math.ceil(totalTables / tablesPerPage);

  // HistoryItem 타입 정의
  interface HistoryItem {
    problem: string;
    userAnswer: number;
    correct: boolean;
    timestamp: Date;
    timeTaken: number;
  }

  const generateNewProblem = () => {
    const availableNumbers = Array.from({ length: 12 }, (_, i) => i + 1)
      .filter(n => !usedProblems.has(`${selectedTable}-${n}`));

    if (availableNumbers.length === 0) {
      setUsedProblems(new Set());
      setNum2(Math.floor(Math.random() * 12) + 1);
      return;
    }

    const randomIndex = Math.floor(Math.random() * availableNumbers.length);
    const newNum2 = availableNumbers[randomIndex];

    setNum1(selectedTable);
    setNum2(newNum2);
    setUsedProblems(prev => new Set([...prev, `${selectedTable}-${newNum2}`]));
    setUserAnswer("");
    setStartTime(new Date());
  };

  useEffect(() => {
    setUsedProblems(new Set());
    generateNewProblem();
  }, [selectedTable]);

  useEffect(() => {
    window.addEventListener('keydown', handleKeyPress);
    return () => {
      window.removeEventListener('keydown', handleKeyPress);
    };
  }, [userAnswer]);

  // 타임 어택 모드 관리
  useEffect(() => {
    let timer: NodeJS.Timeout;
    if (timeAttackMode && remainingTime > 0) {
      timer = setInterval(() => {
        setRemainingTime(prev => {
          if (prev <= 1) {
            clearInterval(timer);
            handleGameOver();
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    }
    return () => clearInterval(timer);
  }, [timeAttackMode, remainingTime]);

  // 게임 오버 처리
  const handleGameOver = () => {
    setTimeAttackMode(false);
    // 게임 오버 로직 추가 (예: 최고 점수 저장, 업적 해제 등)
    if (score >= 100) {
      addAchievement("Time Attack Rookie");
    }
  };

  // 업적 추가 함수
  const addAchievement = (achievement: string) => {
    if (!achievements.includes(achievement)) {
      setAchievements(prev => [...prev, achievement]);

      // 캐릭터 잠금 해제 로직
      if (achievement === "Multiplication Master") {
        const newCharacter = CHARACTER_THEMES[achievements.length % CHARACTER_THEMES.length];
        setUnlockedCharacters(prev =>
          prev.includes(newCharacter.name) ? prev : [...prev, newCharacter.name]
        );
      }
    }
  };

  const handleKeyPress = (event: KeyboardEvent) => {
    if (event.key >= '0' && event.key <= '9') {
      handleNumberClick(event.key);
    } else if (event.key === 'Backspace') {
      handleDelete();
    } else if (event.key === 'Enter') {
      checkAnswer();
    }
  };

  const handleNumberClick = (num: string) => {
    if (userAnswer.length < 3) {
      setUserAnswer(userAnswer + num);
    }
  };

  const handleDelete = () => {
    setUserAnswer(userAnswer.slice(0, -1));
  };

  // 햅틱 피드백 함수들
  const hapticFeedback = {
    success: () => {
      if ('vibrate' in navigator) {
        // Android: 성공 패턴: 짧은 진동 두 번
        navigator.vibrate([50, 30, 50]);
      }

      // iOS 햅틱 피드백
      if ('window' in globalThis && 'ImpactFeedbackGenerator' in window) {
        const generator = new (window as any).ImpactFeedbackGenerator('medium');
        generator.prepare();
        generator.impactOccurred();
      }
    },

    error: () => {
      if ('vibrate' in navigator) {
        // Android: 실패 패턴: 긴 진동 한 번
        navigator.vibrate(300);
      }

      // iOS 햅틱 피드백
      if ('window' in globalThis && 'NotificationFeedbackGenerator' in window) {
        const generator = new (window as any).NotificationFeedbackGenerator('error');
        generator.prepare();
        generator.notificationOccurred();
      }
    },

    buttonPress: () => {
      if ('vibrate' in navigator) {
        navigator.vibrate(10);
      }

      // iOS 햅틱 피드백
      if ('window' in globalThis && 'ImpactFeedbackGenerator' in window) {
        const generator = new (window as any).ImpactFeedbackGenerator('light');
        generator.prepare();
        generator.impactOccurred();
      }
    }
  };

  const handleWrongAnswer = () => {
    hapticFeedback.error();
    setShowErrorAlert(true);

    setTimeout(() => {
      setShowErrorAlert(false);
    }, 2000);

    setScore(score - 15);
    setStreak(0);
    setUserAnswer("");
  };

  // 기존의 checkAnswer 함수 수정
  const checkAnswer = () => {
    if (!userAnswer) return;

    const correct = num1 * num2 === parseInt(userAnswer);

    if (correct) {
      // 연속 정답 시 추가 보상
      if (streak >= 4) {
        addAchievement("Streak Champion");
      }

      // 레벨 시스템 추가
      if (score % 50 === 0) {
        setLevel(prev => prev + 1);
        addAchievement("Level Up!");
      }
    }

    const endTime = new Date();
    const timeTaken = startTime ? endTime.getTime() - startTime.getTime() : 0;

    const newHistory: HistoryItem = {
      problem: `${num1} × ${num2}`,
      userAnswer: parseInt(userAnswer),
      correct: correct,
      timestamp: new Date(),
      timeTaken: timeTaken
    };

    setHistory([newHistory, ...history].slice(0, 5));

    if (correct) {
      hapticFeedback.success();
      setScore(score + 10);
      setStreak(streak + 1);
      setTimeout(generateNewProblem, 1000);
    } else {
      handleWrongAnswer();
    }
  };

  const NumberButton = ({ number }: { number: number }) => (
    <motion.button
      whileHover={{ scale: 1.02 }}
      whileTap={{ scale: 0.95 }}
      onClick={() => {
        hapticFeedback.buttonPress();
        handleNumberClick(number.toString());
      }}
      className="w-full h-16 text-2xl font-medium rounded-lg
                 bg-white hover:bg-gray-50 border border-gray-200
                 shadow-sm transition-all duration-200
                 text-gray-700 relative overflow-hidden"
    >
      <motion.div
        initial={false}
        animate={{
          backgroundColor: ["rgba(79, 70, 229, 0)", "rgba(79, 70, 229, 0.1)", "rgba(79, 70, 229, 0)"]
        }}
        transition={{ duration: 0.3 }}
      >
        {number}
      </motion.div>
    </motion.button>
  );

  const renderAchievementsSection = () => (
    <Card className="bg-white/90 backdrop-blur border-none shadow-lg mt-4">
      <CardContent className="p-6">
        <h3 className="text-lg font-medium text-gray-700 mb-4 flex items-center">
          <TrophyIcon className="w-6 h-6 mr-2 text-amber-500" />
          Achievements
        </h3>
        <div className="grid grid-cols-3 gap-3">
          {[
            "Multiplication Starter",
            "Streak Champion",
            "Time Attack Rookie",
            "Multiplication Master"
          ].map((achievement) => (
            <div
              key={achievement}
              className={`p-2 rounded-lg text-center text-sm 
                ${achievements.includes(achievement)
                  ? 'bg-green-100 text-green-700'
                  : 'bg-gray-100 text-gray-400'}`}
            >
              {achievement}
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );

  const renderCharacterSelection = () => (
    <Card className="bg-white/90 backdrop-blur border-none shadow-lg mt-4">
      <CardContent className="p-6">
        <h3 className="text-lg font-medium text-gray-700 mb-4 flex items-center">
          <Star className="w-6 h-6 mr-2 text-purple-500" />
          Characters
        </h3>
        <div className="grid grid-cols-3 gap-3">
          {CHARACTER_THEMES.map((character) => (
            <motion.button
              key={character.name}
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              onClick={() => setCurrentCharacter(character)}
              disabled={!unlockedCharacters.includes(character.name)}
              className={`p-3 rounded-lg text-center 
                ${unlockedCharacters.includes(character.name)
                  ? `${character.color} bg-opacity-10 border`
                  : 'bg-gray-200 text-gray-400'} 
                ${currentCharacter.name === character.name ? 'ring-2 ring-indigo-500' : ''}`}
            >
              {character.name}
            </motion.button>
          ))}
        </div>
      </CardContent>
    </Card>
  );

  const renderTimeAttackMode = () => (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      className="mb-4"
    >
      <Card className="bg-white/90 backdrop-blur border-none shadow-lg">
        <CardContent className="p-6">
          <div className="flex justify-between items-center">
            <div className="flex items-center">
              <Timer className="w-6 h-6 mr-2 text-red-500" />
              <span className="text-lg font-medium">Time Remaining: {remainingTime}s</span>
            </div>
            <Button
              variant="destructive"
              size="sm"
              onClick={() => {
                setTimeAttackMode(false);
                setRemainingTime(30);
              }}
            >
              End Mode
            </Button>
          </div>
        </CardContent>
      </Card>
    </motion.div>
  );

  return (
    <div className="max-w-md mx-auto p-4 min-h-screen bg-gradient-to-b from-gray-50 to-gray-100">
      <header className="flex justify-between items-center mb-8">
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2 bg-white px-4 py-2 rounded-lg shadow-sm">
            <Activity className="w-5 h-5 text-indigo-500" />
            <span className="text-xl font-medium text-gray-700">{score}</span>
          </div>
          {streak > 0 && (
            <motion.div
              initial={{ scale: 0 }}
              animate={{ scale: 1 }}
              className="flex items-center bg-white px-4 py-2 rounded-lg shadow-sm"
            >
              <Sparkles className="w-5 h-5 text-amber-500 mr-2" />
              <span className="text-xl font-medium text-gray-700">{streak}</span>
            </motion.div>
          )}
        </div>
        <Button
          variant="outline"
          className="w-12 h-12 rounded-lg bg-white"
          onClick={() => setShowSettings(!showSettings)}
        >
          <Settings2 className="w-5 h-5 text-gray-600" />
        </Button>
      </header>

      <AnimatePresence>
        {showSettings && (
          <motion.div
            initial={{ opacity: 0, y: -20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -20 }}
            className="mb-8"
          >
            <Card className="bg-white/90 backdrop-blur border-none shadow-lg">
              <CardContent className="p-6">
                <div className="flex justify-between items-center mb-4">
                  <h3 className="text-lg font-medium text-gray-700">Training Set</h3>
                  <div className="flex gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={currentPage === 0}
                      onClick={() => setCurrentPage(prev => prev - 1)}
                      className="w-8 h-8 flex items-center justify-center"
                    >
                      <ChevronLeft className="w-4 h-4" />
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={currentPage === totalPages - 1}
                      onClick={() => setCurrentPage(prev => prev + 1)}
                      className="w-8 h-8 flex items-center justify-center"
                    >
                      <ChevronRight className="w-5 h-5" />
                    </Button>
                  </div>
                </div>
                <div className="grid grid-cols-5 gap-3">
                  {Array.from({ length: tablesPerPage }, (_, i) => {
                    const table = 2 + i + (currentPage * tablesPerPage);
                    if (table > 19) return null;
                    return (
                      <motion.button
                        key={table}
                        whileHover={{ scale: 1.02 }}
                        whileTap={{ scale: 0.98 }}
                        onClick={() => {
                          setSelectedTable(table);
                          setScore(0);
                          setStreak(0);
                          setShowSettings(false);
                        }}
                        className={`h-12 rounded-lg font-medium transition-all
                                  ${selectedTable === table
                            ? 'bg-indigo-500 text-white'
                            : 'bg-white text-gray-600 hover:bg-gray-50 border border-gray-200'
                          }`}
                      >
                        {table}
                      </motion.button>
                    );
                  })}
                </div>
              </CardContent>
            </Card>
          </motion.div>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {showErrorAlert && (
          <motion.div
            initial={{ opacity: 0, y: -20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -20 }}
            className="fixed top-4 left-4 right-4 z-50"
          >
            <Alert className="bg-red-50 border-red-200">
              <X className="w-4 h-4 text-red-500" />
              <AlertDescription className="text-red-600">
                Incorrect!
              </AlertDescription>
            </Alert>
          </motion.div>
        )}
      </AnimatePresence>

      <Card className="bg-white/90 backdrop-blur border-none shadow-lg mb-6">
        <CardContent className="p-6">
          <motion.div
            key={`${num1}-${num2}`}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="text-center mb-6"
          >
            <div className="text-4xl font-medium mb-6 text-gray-800">
              {num1} × {num2}
            </div>
            <div className="text-4xl h-16 mb-6 bg-gray-50 rounded-lg 
                          flex items-center justify-center font-medium 
                          text-gray-800 border border-gray-200">
              {userAnswer || ""}
            </div>
          </motion.div>

          <div className="grid grid-cols-3 gap-3">
            {[1, 2, 3, 4, 5, 6, 7, 8, 9].map((num) => (
              <NumberButton key={num} number={num} />
            ))}
            <motion.button
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
              onClick={handleDelete}
              className="w-full h-16 rounded-lg bg-gray-100 
                       hover:bg-gray-200 text-gray-600 
                       border border-gray-200 shadow-sm"
            >
              <ChevronLeft className="w-6 h-6 mx-auto" />
            </motion.button>
            <NumberButton number={0} />
            <motion.button
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
              onClick={checkAnswer}
              className="w-full h-16 rounded-lg bg-indigo-500 
                       hover:bg-indigo-600 text-white shadow-sm
                       transition-all duration-200"
            >
              <Check className="w-6 h-6 mx-auto" />
            </motion.button>
          </div>
        </CardContent>
      </Card>

      {history.length > 0 && (
        <Card className="bg-white/90 backdrop-blur border-none shadow-lg">
          <CardContent className="p-6">
            <h3 className="text-lg font-medium text-gray-700 mb-4">History</h3>
            <div className="space-y-3">
              {history.map((item, index) => (
                <div key={index} className="flex items-center justify-between p-3 
                                        bg-gray-50 rounded-lg border border-gray-200">
                  <div className="flex items-center gap-3">
                    {item.correct ? (
                      <Check className="w-5 h-5 text-green-500" />
                    ) : (
                      <X className="w-5 h-5 text-red-500" />
                    )}
                    <span className="text-gray-600">{item.problem} = {item.userAnswer}</span>
                  </div>
                  <div className="flex items-center gap-3 text-sm text-gray-400">
                    <div className="flex items-center gap-1">
                      <Timer className="w-4 h-4" />
                      {formatTime(item.timeTaken)}
                    </div>
                    <span>{formatDateTime(item.timestamp)}</span>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {/* 타임 어택 모드 렌더링 */}
      {timeAttackMode && renderTimeAttackMode()}

      {/* 기존 게임 섹션... */}

      {/* 새로운 섹션들 추가 */}
      {renderAchievementsSection()}
      {renderCharacterSelection()}

      {/* 타임 어택 모드 시작 버튼 */}
      <Button
        className="w-full mt-4"
        onClick={() => {
          setTimeAttackMode(true);
          setRemainingTime(30);
          setScore(0);
          setStreak(0);
        }}
      >
        Start Time Attack Mode
      </Button>
    </div>
  );
};

export default MultiplicationGame;

const formatTime = (ms: number) => {
  if (ms < 1000) return `${ms}ms`;
  const seconds = Math.floor(ms / 1000);
  const remainingMs = Math.floor((ms % 1000) / 100);
  return `${seconds}.${remainingMs}s`;
};

const formatDateTime = (date: Date) => {
  return new Date(date).toLocaleTimeString('en-US', {
    hour: 'numeric',
    minute: 'numeric',
    hour12: true
  });
};
