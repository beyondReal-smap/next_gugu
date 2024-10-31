"use client"
import React, { useState, useEffect } from "react";
import { Card, CardContent } from "./components/ui/card";
import { Button } from "./components/ui/button";
import {
  Activity, Settings2, Sparkles, X, Check, Timer,
  ChevronRight, ChevronLeft, Trophy, Star, Gift
} from "lucide-react";
import { Alert, AlertDescription } from "./components/ui/alert";
import { motion, AnimatePresence } from "framer-motion";

const MultiplicationGame = () => {
  // 기존 상태 관리
  const [num1, setNum1] = useState(2);
  const [num2, setNum2] = useState(1);
  const [userAnswer, setUserAnswer] = useState("");
  const [score, setScore] = useState(0);
  const [streak, setStreak] = useState(0);
  const [selectedTable, setSelectedTable] = useState(2);
  const [showSettings, setShowSettings] = useState(false);
  const [usedProblems, setUsedProblems] = useState(new Set());
  const [history, setHistory] = useState([]);
  const [currentPage, setCurrentPage] = useState(0);
  const [showErrorAlert, setShowErrorAlert] = useState(false);
  const [startTime, setStartTime] = useState(null);

  // 새로운 게임 요소를 위한 상태 추가
  const [coins, setCoins] = useState(0);
  const [level, setLevel] = useState(1);
  const [showReward, setShowReward] = useState(false);
  const [character, setCharacter] = useState("robot");
  const [unlockedCharacters, setUnlockedCharacters] = useState(["robot"]);
  const [showTutorial, setShowTutorial] = useState(true);
  const [combo, setCombo] = useState(0);

  const characters = {
    robot: { name: "로봇", color: "text-blue-500", price: 0 },
    cat: { name: "고양이", color: "text-orange-500", price: 100 },
    dog: { name: "강아지", color: "text-yellow-500", price: 200 },
    dragon: { name: "드래곤", color: "text-red-500", price: 300 },
  };

  // 레벨업에 필요한 점수 계산
  const scoreForNextLevel = level * 100;

  useEffect(() => {
    if (score >= scoreForNextLevel) {
      handleLevelUp();
    }
  }, [score]);

  const handleLevelUp = () => {
    setLevel(prev => prev + 1);
    setShowReward(true);
    setCoins(prev => prev + 50); // 레벨업 보상
    
    // 레벨업 축하 애니메이션
    const confetti = {
      particleCount: 100,
      spread: 70,
      origin: { y: 0.6 }
    };
  };

  const generateNewProblem = () => {
    // 기존 로직 유지
    const availableNumbers = Array.from({ length: 12 }, (_, i) => i + 1)
      .filter(n => !usedProblems.has(`${selectedTable}-${n}`));

    if (availableNumbers.length === 0) {
      setUsedProblems(new Set());
      const newNum2 = Math.floor(Math.random() * 12) + 1;
      setNum2(newNum2);
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

  const checkAnswer = () => {
    if (!userAnswer) return;

    const endTime = new Date();
    const timeTaken = startTime ? endTime.getTime() - startTime.getTime() : 0;
    const correct = num1 * num2 === parseInt(userAnswer);

    if (correct) {
      // 정답 처리 및 보상
      const basePoints = 10;
      const comboBonus = Math.floor(combo / 5) * 2;
      const timeBonus = timeTaken < 3000 ? 5 : 0;
      const totalPoints = basePoints + comboBonus + timeBonus;

      setScore(prev => prev + totalPoints);
      setStreak(prev => prev + 1);
      setCombo(prev => prev + 1);
      setCoins(prev => prev + Math.ceil(totalPoints / 2));

      // 연속 정답 보상
      if ((streak + 1) % 10 === 0) {
        setShowReward(true);
        setCoins(prev => prev + 20);
      }

      playSuccessAnimation();
      setTimeout(generateNewProblem, 1000);
    } else {
      handleWrongAnswer();
    }

    // 히스토리 업데이트
    const newHistory = {
      problem: `${num1} × ${num2}`,
      userAnswer: parseInt(userAnswer),
      correct,
      timestamp: new Date(),
      timeTaken,
      pointsEarned: correct ? totalPoints : 0
    };

    setHistory([newHistory, ...history].slice(0, 5));
  };

  const CharacterShop = () => (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: 20 }}
      className="fixed inset-0 bg-black/50 flex items-center justify-center p-4"
    >
      <Card className="w-full max-w-md bg-white">
        <CardContent className="p-6">
          <div className="flex justify-between items-center mb-6">
            <h3 className="text-xl font-bold">캐릭터 상점</h3>
            <div className="flex items-center gap-2">
              <Coins className="w-5 h-5 text-yellow-500" />
              <span className="font-medium">{coins}</span>
            </div>
          </div>
          
          <div className="grid grid-cols-2 gap-4">
            {Object.entries(characters).map(([id, char]) => (
              <motion.div
                key={id}
                whileHover={{ scale: 1.02 }}
                className="relative p-4 border rounded-lg"
              >
                <div className={`text-4xl mb-2 ${char.color}`}>
                  {getCharacterEmoji(id)}
                </div>
                <div className="font-medium">{char.name}</div>
                {unlockedCharacters.includes(id) ? (
                  <Button
                    variant={character === id ? "default" : "outline"}
                    className="w-full mt-2"
                    onClick={() => setCharacter(id)}
                  >
                    {character === id ? "사용 중" : "사용하기"}
                  </Button>
                ) : (
                  <Button
                    variant="outline"
                    className="w-full mt-2"
                    onClick={() => purchaseCharacter(id, char.price)}
                    disabled={coins < char.price}
                  >
                    {char.price} 코인
                  </Button>
                )}
              </motion.div>
            ))}
          </div>
        </CardContent>
      </Card>
    </motion.div>
  );

  // 캐릭터 이모지 반환 함수
  const getCharacterEmoji = (charId) => {
    switch (charId) {
      case 'robot': return '🤖';
      case 'cat': return '🐱';
      case 'dog': return '🐶';
      case 'dragon': return '🐲';
      default: return '🤖';
    }
  };

  return (
    <div className="max-w-md mx-auto p-4 min-h-screen bg-gradient-to-b from-gray-50 to-gray-100">
      {/* 상단 메뉴바 */}
      <header className="flex justify-between items-center mb-8">
        <div className="flex items-center gap-4">
          {/* 점수 */}
          <motion.div
            whileHover={{ scale: 1.05 }}
            className="flex items-center gap-2 bg-white px-4 py-2 rounded-lg shadow-sm"
          >
            <Activity className="w-5 h-5 text-indigo-500" />
            <span className="text-xl font-medium text-gray-700">{score}</span>
          </motion.div>
          
          {/* 레벨 */}
          <motion.div
            whileHover={{ scale: 1.05 }}
            className="flex items-center gap-2 bg-white px-4 py-2 rounded-lg shadow-sm"
          >
            <Trophy className="w-5 h-5 text-yellow-500" />
            <span className="text-xl font-medium text-gray-700">Lv.{level}</span>
          </motion.div>

          {/* 코인 */}
          <motion.div
            whileHover={{ scale: 1.05 }}
            className="flex items-center gap-2 bg-white px-4 py-2 rounded-lg shadow-sm"
          >
            <Coins className="w-5 h-5 text-yellow-500" />
            <span className="text-xl font-medium text-gray-700">{coins}</span>
          </motion.div>
        </div>

        {/* 설정 버튼 */}
        <Button
          variant="outline"
          className="w-12 h-12 rounded-lg bg-white"
          onClick={() => setShowSettings(!showSettings)}
        >
          <Settings2 className="w-5 h-5 text-gray-600" />
        </Button>
      </header>

      {/* 메인 게임 카드 */}
      <Card className="bg-white/90 backdrop-blur border-none shadow-lg mb-6">
        <CardContent className="p-6">
          {/* 캐릭터 표시 */}
          <motion.div
            animate={{ scale: [1, 1.1, 1], rotate: [0, 10, -10, 0] }}
            transition={{ duration: 0.5 }}
            className="text-center mb-4"
          >
            <div className="text-6xl mb-2">
              {getCharacterEmoji(character)}
            </div>
            <div className="text-sm text-gray-500">
              {characters[character].name}와 함께 공부해요!
            </div>
          </motion.div>

          {/* 문제 표시 */}
          <motion.div
            key={`${num1}-${num2}`}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="text-center mb-6"
          >
            <div className="text-4xl font-medium mb-6 text-gray-800">
              {num1} × {num2} = ?
            </div>
            <div className="text-4xl h-16 mb-6 bg-gray-50 rounded-lg 
                          flex items-center justify-center font-medium 
                          text-gray-800 border border-gray-200">
              {userAnswer || ""}
            </div>
          </motion.div>

          {/* 숫자 패드 */}
          <div className="grid grid-cols-3 gap-3">
            {[1, 2, 3, 4, 5, 6, 7, 8, 9].map((num) => (
              <NumberButton key={num} number={num} />
            ))}
            <Button variant="outline" onClick={handleDelete}>
              <ChevronLeft className="w-6 h-6" />
            </Button>
            <NumberButton number={0} />
            <Button 
              variant="default"
              className="bg-indigo-500 hover:bg-indigo-600"
              onClick={checkAnswer}
            >
              <Check className="w-6 h-6" />
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* 진행 상황 표시 */}
      <div className="mb-6">
        <div className="flex justify-between text-sm text-gray-600 mb-2">
          <span>Level {level}</span>
          <span>{score}/{scoreForNextLevel}</span>
        </div>
        <div className="h-2 bg-gray-200 rounded-full">
          <motion.div
            className="h-full bg-indigo-500 rounded-full"
            initial={{ width: "0%" }}
            animate={{ width: `${(score / scoreForNextLevel) * 100}%` }}
            transition={{ duration: 0.5 }}
          />
        </div>
      </div>

      {/* 히스토리 섹션 */}
      {history.length > 0 && (
        <Card className="bg-white/90 backdrop-blur border-none shadow-lg">
          <CardContent className="p-6">
            <h3 className="text-lg font-medium text-gray-700 mb-4">기록</h3>
            <div className="space-y-3">
              {history.map((item, index) => (
                <motion.div
                  key={index}
                  initial={{ opacity: 0, x: -20 }}
                  animate={{ opacity: 1, x: 0 }}
                  className="flex items-center justify-between p-3 
                          bg-gray-50 rounded-lg border border-gray-200"
                >
                  <div className="flex items-center gap-3">
                    {item.correct ? (
                      <Check className="w-5 h-5 text-green-500" />
                    ) : (
                      <X className="w-5 h-5 text-red-500" />
                    )}
                    <span className="text-gray-600">
                      {item.problem} = {item.userAnswer}
                      {item.correct && item.pointsEarned > 10 && (
                        <span className="ml-2 text-sm text-indigo-500">
                          +{item.pointsEarned}
                        </span>
                      )}
                    </span>
                  </div>
                  <div className="flex items-center gap-2 text-sm text-gray-400">
                    <Timer className="w-4 h-4" />
                    {formatTime(item.timeTaken)}
                  </div>
                </motion.div>
              ))}
            </div>
          </CardContent>
        </Card>
        {/* 보상 애니메이션 */}
      <AnimatePresence>
      {showReward && (
        <motion.div
          initial={{ opacity: 0, scale: 0.5 }}
          animate={{ opacity: 1, scale: 1 }}
          exit={{ opacity: 0, scale: 0.5 }}
          className="fixed inset-0 bg-black/50 flex items-center justify-center p-4"
          onClick={() => setShowReward(false)}
        >
          <Card className="w-full max-w-sm bg-white">
            <CardContent className="p-6 text-center">
              <motion.div
                animate={{ 
                  rotate: [0, 10, -10, 10, 0],
                  scale: [1, 1.2, 1]
                }}
                transition={{ duration: 0.5 }}
                className="text-6xl mb-4"
              >
                {level > 1 ? '🎉' : '🌟'}
              </motion.div>
              <h3 className="text-2xl font-bold mb-2">
                {level > 1 ? `Level ${level} 달성!` : '연속 정답!'}
              </h3>
              <p className="text-gray-600 mb-4">
                {level > 1 
                  ? '더 어려운 문제에 도전해보세요!'
                  : '잘 하고 있어요! 계속 도전하세요!'}
              </p>
              <div className="flex items-center justify-center gap-2 mb-4">
                <Coins className="w-6 h-6 text-yellow-500" />
                <span className="text-xl font-bold text-yellow-500">
                  +{level > 1 ? 50 : 20}
                </span>
              </div>
              <Button
                className="w-full"
                onClick={() => setShowReward(false)}
              >
                계속하기
              </Button>
            </CardContent>
          </Card>
        </motion.div>
      )}
    </AnimatePresence>

    {/* 튜토리얼 */}
    <AnimatePresence>
      {showTutorial && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="fixed inset-0 bg-black/50 flex items-center justify-center p-4"
        >
          <Card className="w-full max-w-md bg-white">
            <CardContent className="p-6">
              <h3 className="text-xl font-bold mb-4">구구단 게임 방법</h3>
              <div className="space-y-4">
                <div className="flex items-start gap-3">
                  <div className="w-8 h-8 rounded-full bg-indigo-100 flex items-center justify-center flex-shrink-0">
                    1
                  </div>
                  <div>
                    <p className="text-gray-700">문제를 보고 답을 입력하세요</p>
                    <p className="text-sm text-gray-500">숫자 버튼을 터치하거나 키보드를 사용할 수 있어요</p>
                  </div>
                </div>
                <div className="flex items-start gap-3">
                  <div className="w-8 h-8 rounded-full bg-indigo-100 flex items-center justify-center flex-shrink-0">
                    2
                  </div>
                  <div>
                    <p className="text-gray-700">정답을 맞추면 점수와 코인을 획득해요</p>
                    <p className="text-sm text-gray-500">빠르게 맞추면 보너스 점수를 받을 수 있어요!</p>
                  </div>
                </div>
                <div className="flex items-start gap-3">
                  <div className="w-8 h-8 rounded-full bg-indigo-100 flex items-center justify-center flex-shrink-0">
                    3
                  </div>
                  <div>
                    <p className="text-gray-700">코인으로 새로운 캐릭터를 구매할 수 있어요</p>
                    <p className="text-sm text-gray-500">각 캐릭터는 특별한 효과를 가지고 있어요</p>
                  </div>
                </div>
              </div>
              <Button
                className="w-full mt-6"
                onClick={() => setShowTutorial(false)}
              >
                시작하기
              </Button>
            </CardContent>
          </Card>
        </motion.div>
      )}
    </AnimatePresence>

    {/* 연속 정답 콤보 표시 */}
    <AnimatePresence>
      {combo > 0 && (
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: 20 }}
          className="fixed bottom-4 right-4"
        >
          <div className="bg-indigo-500 text-white px-4 py-2 rounded-full shadow-lg flex items-center gap-2">
            <Sparkles className="w-5 h-5" />
            <span className="font-bold">{combo} Combo!</span>
          </div>
        </motion.div>
      )}
    </AnimatePresence>

    {/* 효과음 처리를 위한 audio 요소들 */}
    <audio id="correctSound" src="/sounds/correct.mp3" />
    <audio id="wrongSound" src="/sounds/wrong.mp3" />
    <audio id="buttonSound" src="/sounds/button.mp3" />
    <audio id="rewardSound" src="/sounds/reward.mp3" />
  </div>
);
};

// 숫자 버튼 컴포넌트
const NumberButton = ({ number, onClick }) => (
<motion.button
  whileHover={{ scale: 1.02 }}
  whileTap={{ scale: 0.95 }}
  onClick={onClick}
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

// 시간 포맷팅 유틸리티 함수
const formatTime = (ms) => {
if (ms < 1000) return `${ms}ms`;
const seconds = Math.floor(ms / 1000);
const remainingMs = Math.floor((ms % 1000) / 100);
return `${seconds}.${remainingMs}s`;
};

// 날짜 포맷팅 유틸리티 함수
const formatDateTime = (date) => {
return new Date(date).toLocaleTimeString('ko-KR', {
  hour: 'numeric',
  minute: 'numeric',
  hour12: true
});
};

export default MultiplicationGame;