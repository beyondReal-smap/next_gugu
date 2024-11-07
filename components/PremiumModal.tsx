import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Crown, Check, ArrowRight } from 'lucide-react';
import { Button } from '../app/components/ui/button';

interface PremiumModalProps {
    show: boolean;
    onClose: () => void;
    onPurchase: () => void;
}

const PremiumModal: React.FC<PremiumModalProps> = ({ show, onClose, onPurchase }) => {
    const benefits = [
        '📱 광고 없는 깔끔한 학습',
        '🎯 모든 구구단 학습 가능',
        '📊 상세한 학습 통계',
        '🎮 추가 게임 모드',
        '🌟 프리미엄 테마'
    ];

    return (
        <AnimatePresence>
            {show && (
                <>
                    <div
                        className="fixed inset-0 bg-black/50 backdrop-blur-sm z-[100]"
                        onClick={onClose}
                    />
                    <div className="fixed inset-0 flex items-center justify-center z-[101] p-4">
                        <motion.div
                            initial={{ opacity: 0, scale: 0.95 }}
                            animate={{ opacity: 1, scale: 1 }}
                            exit={{ opacity: 0, scale: 0.95 }}
                            className="w-full max-w-md bg-white rounded-2xl shadow-xl overflow-hidden"
                            onClick={(e) => e.stopPropagation()}
                        >
                            <div className="bg-gradient-to-r from-amber-400 to-orange-400 p-6 text-white">
                                <div className="flex items-center justify-between mb-4">
                                    <Crown className="w-8 h-8" />
                                    <button
                                        onClick={onClose}
                                        className="text-white/80 hover:text-white transition-colors"
                                    >
                                        ✕
                                    </button>
                                </div>
                                <h2 className="text-2xl font-suite font-bold mb-2">프리미엄으로 업그레이드</h2>
                                <p className="font-suite text-white/90">더 나은 학습 경험을 시작하세요</p>
                            </div>

                            <div className="p-6">
                                <div className="space-y-4 mb-6">
                                    {benefits.map((benefit, index) => (
                                        <div
                                            key={index}
                                            className="flex items-center gap-3 bg-amber-50 p-3 rounded-lg"
                                        >
                                            <Check className="w-5 h-5 text-amber-500 flex-shrink-0" />
                                            <span className="text-gray-700 font-suite">{benefit}</span>
                                        </div>
                                    ))}
                                </div>

                                <Button
                                    variant="primary"
                                    onClick={onPurchase}
                                    className="w-full h-12 bg-gradient-to-r from-amber-400 to-orange-400 
                                    hover:from-amber-500 hover:to-orange-500 text-white font-suite font-medium
                                    flex items-center justify-center gap-2"
                                >
                                    프리미엄 시작하기
                                    <ArrowRight className="w-4 h-4" />
                                </Button>
                            </div>
                        </motion.div>
                    </div>
                </>
            )}
        </AnimatePresence>
    );
};

export default PremiumModal;