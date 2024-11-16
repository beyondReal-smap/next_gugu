import React, { useEffect, useState, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
    Sparkles, Star, Zap, Crown, Heart, Award, Trophy,
    Shield, Flame, CircleDot
} from 'lucide-react';
import { triggerHapticFeedback, HAPTIC_TYPES } from '../src//utils/hapticFeedback';

interface ComboAnimationProps {
    combo: number;
}

const ComboAnimation: React.FC<ComboAnimationProps> = ({ combo }) => {
    const [showAnimation, setShowAnimation] = useState(false);
    const [animationStyle, setAnimationStyle] = useState(0);

    const getComboStyle = useCallback(() => {
        if (combo % 5 === 0) {
            return 'text-transparent bg-clip-text bg-gradient-to-r from-yellow-300 via-purple-500 to-pink-500 drop-shadow-[0_0_10px_rgba(255,255,255,0.7)]';
        }

        switch (combo % 4) {
            case 0:
                return 'text-yellow-300 drop-shadow-[0_0_15px_rgba(252,211,77,0.7)]';
            case 1:
                return 'text-emerald-300 drop-shadow-[0_0_15px_rgba(110,231,183,0.7)]';
            case 2:
                return 'text-cyan-300 drop-shadow-[0_0_15px_rgba(103,232,249,0.7)]';
            case 3:
                return 'text-pink-300 drop-shadow-[0_0_15px_rgba(249,168,212,0.7)]';
            default:
                return 'text-white';
        }
    }, [combo]);

    useEffect(() => {
        if (combo >= 2) {
            setShowAnimation(true);
            if (combo % 5 === 0) {
                triggerHapticFeedback(HAPTIC_TYPES.IMPACT_HEAVY);
            }
            setAnimationStyle(combo % 5);

            const timer = setTimeout(() => {
                setShowAnimation(false);
            }, 1500);

            return () => {
                clearTimeout(timer);
            };
        } else {
            setShowAnimation(false);
        }
    }, [combo]);

    const renderBackgroundEffect = () => {
        switch (animationStyle) {
            case 0: // 회전하는 그라데이션 별
                return (
                    <motion.div
                        initial={{ scale: 0, opacity: 0 }}
                        animate={{
                            scale: [1, 1.2, 1],
                            opacity: [0.3, 0.5, 0.3],
                            rotate: [0, 180, 360]
                        }}
                        transition={{ duration: 2, repeat: Infinity }}
                        className="absolute w-48 h-48 rounded-full blur-2xl bg-gradient-to-r from-yellow-300 via-purple-500 to-pink-500"
                    />
                );

            case 1: // 다중 원형 파동
                return (
                    <>
                        {[0, 1, 2].map((i) => (
                            <motion.div
                                key={i}
                                initial={{ scale: 0.5, opacity: 0.8 }}
                                animate={{
                                    scale: [0.5, 2],
                                    opacity: [0.8, 0],
                                }}
                                transition={{
                                    duration: 1.5,
                                    repeat: Infinity,
                                    delay: i * 0.3
                                }}
                                className="absolute w-32 h-32 rounded-full border-4 border-cyan-400"
                            />
                        ))}
                    </>
                );

            case 2: // 광선 효과 대신 새로운 효과 (회오리 파티클)
                return (
                    <motion.div className="absolute">
                        {Array.from({ length: 12 }).map((_, i) => {
                            const radius = 30 + (i % 3) * 15;
                            const angle = (i * 30) * (Math.PI / 180);
                            return (
                                <motion.div
                                    key={i}
                                    initial={{
                                        x: 0,
                                        y: 0,
                                        opacity: 0,
                                        scale: 0
                                    }}
                                    animate={{
                                        x: Math.cos(angle) * radius,
                                        y: Math.sin(angle) * radius,
                                        opacity: [0, 1, 0],
                                        scale: [0, 1, 0.5]
                                    }}
                                    transition={{
                                        duration: 1.5,
                                        repeat: Infinity,
                                        delay: i * 0.1,
                                        ease: "easeInOut"
                                    }}
                                    className={`absolute w-2 h-2 rounded-full ${i % 3 === 0 ? 'bg-purple-400' :
                                            i % 3 === 1 ? 'bg-pink-400' :
                                                'bg-indigo-400'
                                        }`}
                                />
                            );
                        })}
                        <motion.div
                            initial={{ scale: 0.8, opacity: 0 }}
                            animate={{
                                scale: [0.8, 1.2, 0.8],
                                opacity: [0.3, 0.6, 0.3],
                                rotate: [0, 360]
                            }}
                            transition={{
                                duration: 3,
                                repeat: Infinity,
                                ease: "linear"
                            }}
                            className="absolute w-32 h-32 rounded-full border-2 border-purple-400/30 border-dashed"
                        />
                    </motion.div>
                );

            case 3: // 분산되는 파티클
                return (
                    <>
                        {Array.from({ length: 8 }).map((_, i) => {
                            const angle = (i * Math.PI) / 4;
                            return (
                                <motion.div
                                    key={i}
                                    initial={{ x: 0, y: 0, opacity: 1, scale: 1 }}
                                    animate={{
                                        x: Math.cos(angle) * 60,
                                        y: Math.sin(angle) * 60,
                                        opacity: [1, 0],
                                        scale: [1, 0]
                                    }}
                                    transition={{
                                        duration: 1,
                                        repeat: Infinity,
                                        delay: i * 0.1
                                    }}
                                    className="absolute w-3 h-3 rounded-full bg-yellow-400"
                                />
                            );
                        })}
                    </>
                );

            case 4: // 무지개 펄스
                return (
                    <>
                        {[
                            'bg-pink-400',
                            'bg-purple-400',
                            'bg-blue-400',
                            'bg-green-400',
                            'bg-yellow-400'
                        ].map((color, i) => (
                            <motion.div
                                key={i}
                                initial={{ scale: 0, opacity: 0 }}
                                animate={{
                                    scale: [0, 1.5],
                                    opacity: [0.7, 0],
                                }}
                                transition={{
                                    duration: 1.5,
                                    repeat: Infinity,
                                    delay: i * 0.2
                                }}
                                className={`absolute w-32 h-32 rounded-full ${color} blur-xl opacity-30`}
                            />
                        ))}
                    </>
                );

            default:
                return null;
        }
    };

    return (
        <AnimatePresence mode="wait">
            {showAnimation && (
                <div className="fixed inset-x-0 top-0 h-[26vh] pointer-events-none z-[60] 
                               sm:h-[45vh] md:h-[50vh] lg:h-[55vh]">
                    <motion.div 
                        className="absolute inset-0 bg-black/50 backdrop-blur-sm"
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        exit={{ opacity: 0 }}
                    />
                    
                    <div className="relative h-full flex items-center justify-center">
                        {renderBackgroundEffect()}
                        
                        <motion.div
                            initial={{ scale: 0.5, opacity: 0, y: 20 }}
                            animate={{ scale: 1, opacity: 1, y: 0 }}
                            exit={{ scale: 0.5, opacity: 0, y: -20 }}
                            transition={{ duration: 0.3 }}
                            className="relative flex flex-col items-center"
                        >
                            {/* 반응형 아이콘 배치 */}
                            <motion.div
                                className="absolute -left-12 -top-12 
                                           sm:-left-16 sm:-top-16 
                                           md:-left-20 md:-top-20"
                                animate={{
                                    scale: [1, 1.2, 1],
                                    rotate: [-10, 10, -10],
                                }}
                                transition={{ duration: 0.5, repeat: Infinity }}
                            >
                                <Sparkles className="w-8 h-8 
                                                   sm:w-10 sm:h-10 
                                                   md:w-12 md:h-12 
                                                   text-yellow-300" />
                            </motion.div>
                            
                            {/* 추가 아이콘들도 비슷한 방식으로 반응형 적용 */}
                            
                            {/* 콤보 숫자 */}
                            <motion.div 
                                className={`text-[3.5rem] sm:text-[4.5rem] md:text-[5.5rem] lg:text-[6.5rem]
                                          leading-none font-black font-suite tracking-tight ${getComboStyle()}`}
                                animate={{ 
                                    scale: [1, 1.15, 1],
                                    rotate: [0, -3, 3, 0],
                                }}
                                transition={{ 
                                    duration: 0.5,
                                    repeat: Infinity,
                                    repeatType: "reverse"
                                }}
                            >
                                {combo}
                            </motion.div>
                            
                            {/* Combo 텍스트 */}
                            <motion.div 
                                className={`text-xl sm:text-2xl md:text-3xl lg:text-4xl
                                          font-black tracking-wider font-suite mt-2
                                          ${combo % 5 === 0 
                                              ? 'text-transparent bg-clip-text bg-gradient-to-r from-yellow-300 via-purple-500 to-pink-500'
                                              : getComboStyle()
                                          }`}
                                initial={{ opacity: 0, scale: 0.5 }}
                                animate={{ 
                                    opacity: 1, 
                                    scale: [1, 1.1, 1],
                                    y: [0, -2, 0]
                                }}
                                transition={{
                                    duration: 0.5,
                                    repeat: Infinity,
                                    repeatType: "reverse"
                                }}
                            >
                                COMBO!
                            </motion.div>
                        </motion.div>
                    </div>
                </div>
            )}
        </AnimatePresence>
    );
};

export default ComboAnimation;