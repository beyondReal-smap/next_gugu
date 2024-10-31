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
  Award
} from "lucide-react";
import { Alert, AlertDescription } from "./components/ui/alert";
import { motion, AnimatePresence } from "framer-motion";

// 캐릭터 이미지 import (예시)
import character1 from "./assets/character1.png"; // 이미지 경로는 실제 경로로 변경해야 합니다.
import character2 from "./assets/character2.png";
import character3 from "./assets/character3.png";

const MultiplicationGame = () => {
  const [num1, setNum1] = useState(2);
  const [num2, setNum2] = useState(1);
  const [userAnswer, setUserAnswer] = useState("");
  const [score, setScore] = useState(0);
  const [streak, setStreak] = useState(0);
  const [level, setLevel] = useState(1);
  const [selectedTable, setSelectedTable] = useState(2);
  const [showSettings, setShowSettings] = useState(false);
  const [usedProblems, setUsedProblems] = useState(new Set());
  const [history, setHistory] = useState<HistoryItem[]>([]);
  const [currentPage, setCurrentPage] = useState(0);
  const [showErrorAlert, setShowErrorAlert] = useState(false);
  const [startTime, setStartTime] = useState<Date | null>(null);
  const [achievements, setAchievements] = useState<Achievement[]>([]);
  const [showAchievements, setShowAchievements] = useState(false);


  const tablesPerPage = 10;
  const totalTables = 18;
  const totalPages = Math.ceil(totalTables / tablesPerPage);

  const [selectedCharacter, setSelectedCharacter] = useState(0);
  const [gameMode, setGameMode] = useState("practice"); // "practice" 또는 "timeAttack"
  const [timeLeft, setTimeLeft] = useState(30); // 타임어택 시간 (초)
  const [timeAttackScore, setTimeAttackScore] = useState(0);

  const characters = [
    { id: 0, src: character1, name: "Character 1" },
    { id: 1, src: character2, name: "Character 2" },
    { id: 2, src: character3, name: "Character 3" },
  ];

  useEffect(() => {
    let timerInterval: NodeJS.Timeout | null = null;

    if (gameMode === "timeAttack" && timeLeft > 0) {
      timerInterval = setInterval(() => {
        setTimeLeft(prevTime => prevTime - 1);
      }, 1000);
    } else if (gameMode === "timeAttack" && timeLeft === 0) {
      clearInterval(timerInterval!);
       // 타임 어택 종료 시 로직 (예: 결과 화면 표시)
       alert("Time's up! Your score: " + timeAttackScore);
       setGameMode("practice"); // 게임 모드를 다시 "practice"로 변경
       setTimeAttackScore(0); // 점수 초기화
    }

    return () => {
      if (timerInterval) {
        clearInterval(timerInterval);
      }
    };
  }, [gameMode, timeLeft]);

  interface HistoryItem {
    problem: string;
    userAnswer: number;
    correct: boolean;
    timestamp: Date;
    timeTaken: number;
  }

  interface Achievement {
    name: string;
    description: string;
    unlocked: boolean;
  }

  const initialAchievements: Achievement[] = [
    { name: "First Step", description: "Solve your first problem!", unlocked: false },
    { name: "Streak Master", description: "Reach a streak of 5!", unlocked: false },
    { name: "Level Up!", description: "Reach level 5!", unlocked: false },
    // ... 더 많은 업적 추가
  ];


  useEffect(() => {
    setAchievements(initialAchievements);
  }, []);

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

    setScore(Math.max(0, score - 15)); // 점수가 0 밑으로 내려가지 않도록 수정
    setStreak(0);
    setUserAnswer("");
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
      hapticFeedback.success();
      setScore(score + 10);
      setStreak(streak + 1);

      // 업적 확인 및 갱신
      setAchievements(prevAchievements => {
        const updatedAchievements = prevAchievements.map(achievement => {
          if (achievement.name === "First Step" && !achievement.unlocked) {
            return { ...achievement, unlocked: true };
          }
          if (achievement.name === "Streak Master" && streak + 1 >= 5 && !achievement.unlocked) {
            return { ...achievement, unlocked: true };
          }
          return achievement;
        });
        return updatedAchievements;
      });

      // 레벨 업 로직
      const newLevel = Math.floor(score / 100) + 1;
      if (newLevel > level) {
        setLevel(newLevel);

          // 업적 확인 및 갱신
          setAchievements(prevAchievements => {
            const updatedAchievements = prevAchievements.map(achievement => {
              if (achievement.name === "Level Up!" && newLevel >= 5 && !achievement.unlocked) {
                return { ...achievement, unlocked: true };
              }
              return achievement;
            });
            return updatedAchievements;
          });
      }

      setTimeout(generateNewProblem, 1000);
    } else {
      handleWrongAnswer();
    }
    if (gameMode === "timeAttack" && correct) {
      setTimeAttackScore(prevScore => prevScore + 10);
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


  return (
    <div className="max-w-md mx-auto p-4 min-h-screen bg-gradient-to-b from-gray-50 to-gray-100">
      <div className="mb-8 flex justify-center">
         {gameMode === "timeAttack" && (
          <div className="flex items-center gap-2 bg-white px-4 py-2 rounded-lg shadow-sm">
            <Clock className="w-5 h-5 text-red-500 mr-2" />
           <span className="text-xl font-medium text-gray-700">{timeLeft}s</span>
          </div>
         )}
      </div>

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
             <div className="flex items-center gap-2 bg-white px-4 py-2 rounded-lg shadow-sm">
            <span className="text-xl font-medium text-gray-700">Lv. {level}</span>
          </div>
        </div>
        <div className="flex gap-2">
        <Button
          variant="outline"
          className="w-12 h-12 rounded-lg bg-white"
          onClick={() => setShowAchievements(!showAchievements)}
        >
          <Award className="w-5 h-5 text-gray-600" />
        </Button>
        <Button
          variant="outline"
          className="w-12 h-12 rounded-lg bg-white"
          onClick={() => setShowSettings(!showSettings)}
        >
          <Settings2 className="w-5 h-5 text-gray-600" />
        </Button>
        </div>
      </header>


      <AnimatePresence>
        {showAchievements && (
          <motion.div
            initial={{ opacity: 0, y: -20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -20 }}
            className="mb-8"
          >
            <Card className="bg-white/90 backdrop-blur border-none shadow-lg">
              <CardContent className="p-6">
                <h3 className="text-lg font-medium text-gray-700 mb-4">Achievements</h3>
                <ul className="space-y-3">
                  {achievements.map((achievement, index) => (
                    <li key={index} className={`flex items-center p-3 rounded-lg border ${achievement.unlocked ? 'border-green-200 bg-green-50' : 'border-gray-200 bg-gray-50'}`}>
                      <span className={`mr-3 font-medium ${achievement.unlocked ? 'text-green-600' : 'text-gray-600'}`}>
                       {achievement.name}
                      </span>
                      <span className="text-sm text-gray-500">{achievement.description}</span>
                    </li>
                  ))}
                </ul>
              </CardContent>
            </Card>
          </motion.div>
        )}
      </AnimatePresence>


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
                           setScore(0); // 선택한 테이블이 바뀌면 점수 초기화
                          setStreak(0); // 선택한 테이블이 바뀌면 streak 초기화
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

      <div className="mb-6 flex justify-center">
       {characters.map((char) => (
        <button key={char.id} onClick={() => setSelectedCharacter(char.id)} className={`p-2 rounded-full ${selectedCharacter === char.id ? 'bg-indigo-200' : ''}`}>
          <img src={char.src} alt={char.name} className="w-16 h-16 rounded-full" />
        </button>
      ))}
    </div>

    <div className="mb-6 flex justify-center gap-4">
        <Button onClick={() => setGameMode("practice")} variant={gameMode === "practice" ? "default" : "outline"}>연습 모드</Button>
        <Button onClick={() => {
           setGameMode("timeAttack");
           setTimeLeft(30); // 타임어택 시작 시 시간 초기화
           setTimeAttackScore(0); // 타임어택 시작 시 점수 초기화
           generateNewProblem(); // 새 문제 생성

        }} variant={gameMode === "timeAttack" ? "default" : "outline"}>타임 어택</Button>
      </div>


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