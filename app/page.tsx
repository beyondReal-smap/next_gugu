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
  ChevronLeft
} from "lucide-react";
import { Alert, AlertDescription } from "./components/ui/alert";
import { motion, AnimatePresence } from "framer-motion";

const MultiplicationGame = () => {
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
    setUsedProblems(new Set());
    generateNewProblem();

    // 정답 확인 시 햅틱 피드백 추가
    const feedback = () => {
      // iOS 햅틱 피드백
      if (
        'window' in globalThis &&
        'ImpactFeedbackGenerator' in window &&
        window.navigator.platform === 'iPhone'
      ) {
        // 타입 단언을 사용하여 ImpactFeedbackGenerator 타입 명시
        const generator = new (window as any).ImpactFeedbackGenerator('medium');
        generator.prepare();
        generator.impactOccurred();
      }
    };

    feedback();
  }, [selectedTable]);

  // 햅틱 피드백 함수들
  const hapticFeedback = {
    success: () => {
      if ('vibrate' in navigator) {
        // 성공 패턴: 짧은 진동 두 번
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
        // 실패 패턴: 긴 진동 한 번
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
    // setShake(true);
    setShowErrorAlert(true);

    setTimeout(() => {
      // setShake(false);
      setShowErrorAlert(false);
    }, 2000);

    setScore(score - 15);
    setStreak(0);
    setUserAnswer("");
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
          backgroundColor: ["rgba(79, 70, 229, 0)", "rgba(79, 70, 229, 0.1)", "rgba(79, 70, 229, 0)"],
        }}
        transition={{ duration: 0.3 }}
      >
        {number}
      </motion.div>
    </motion.button>
  );

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

  const checkAnswer = () => {
    if (!userAnswer) return;

    const endTime = new Date();
    const timeTaken = startTime ? endTime.getTime() - startTime.getTime() : 0;
    const correct = num1 * num2 === parseInt(userAnswer);

    const newHistory: HistoryItem = {
      problem: `${num1} × ${num2}`,
      userAnswer: parseInt(userAnswer),
      correct: correct,
      timestamp: new Date(),
      timeTaken: timeTaken
    };

    setHistory([newHistory, ...history].slice(0, 5));

    if (correct) {
      setScore(score + 10);
      setStreak(streak + 1);
      setTimeout(generateNewProblem, 1000);
    } else {
      handleWrongAnswer();
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

  const handleKeyPress = (event: KeyboardEvent) => {
    if (event.key >= '0' && event.key <= '9') {
      handleNumberClick(event.key);
    } else if (event.key === 'Backspace') {
      handleDelete();
    } else if (event.key === 'Enter') {
      checkAnswer();
    }
  };

  const [setIsLoading] = useState(true);

  useEffect(() => {
    window.addEventListener('keydown', handleKeyPress);
    return () => {
      window.removeEventListener('keydown', handleKeyPress);
    };
  }, [userAnswer]);


  useEffect(() => {
    // 2초 후 스플래시 화면 숨기기
    const timer = setTimeout(() => {
      // setIsLoading(false);
    }, 2000);

    return () => clearTimeout(timer);
  }, []);

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
                      className="w-8 h-8 flex items-center justify-center" // `className` 속성 추가
                    >
                      <ChevronLeft className="w-4 h-4" />
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={currentPage === totalPages - 1}
                      onClick={() => setCurrentPage(prev => prev + 1)}
                      className="w-8 h-8 flex items-center justify-center" // `className` 속성 추가
                    >
                      <ChevronRight className="w-4 h-4" />
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
                {/* The correct answer is {num1 * num2}. */}
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
    </div>
  );
};

export default MultiplicationGame;