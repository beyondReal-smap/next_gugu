import React, { useEffect } from 'react';
import { motion } from 'framer-motion';
import { Crown, Check, ArrowRight } from 'lucide-react';
import { Button } from '../app/components/ui/button';
import PurchaseManager from '../app/lib/purchaseManager';

interface PremiumModalProps {
    show: boolean;
    onClose: () => void;
    purchaseDate?: Date;
}

const PremiumModal = React.memo(({ show, onClose, purchaseDate }: PremiumModalProps) => {
    if (!show) return null;

    const benefits = [
        '📱 광고 없는 깔끔한 학습',
        '🎯 모든 구구단 학습 가능',
        '📊 상세한 학습 통계',
        '🎮 추가 게임 모드',
        '🌟 프리미엄 테마'
    ];

    return (
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
                    <div className="bg-gradient-to-r from-indigo-500 to-blue-500 p-6 text-white">
                        <div className="flex items-center justify-between mb-4">
                            <Crown className="w-8 h-8" />
                            <button
                                onClick={onClose}
                                className="text-white/80 hover:text-white transition-colors"
                            >
                                ✕
                            </button>
                        </div>
                        <h2 className="text-2xl font-suite font-bold mb-2">프리미엄 회원</h2>
                        {purchaseDate && (
                            <p className="font-suite text-white/90">
                                {`${purchaseDate.getFullYear()}년 ${purchaseDate.getMonth() + 1}월 ${purchaseDate.getDate()}일부터 이용 중`}
                            </p>
                        )}
                    </div>

                    <div className="p-6">
                        <div className="mb-6">
                            <h3 className="text-lg font-suite font-bold text-gray-900 mb-4">
                                이용 중인 프리미엄 혜택
                            </h3>
                            <div className="space-y-3">
                                {benefits.map((benefit, index) => (
                                    <div
                                        key={index}
                                        className="flex items-center gap-3 bg-indigo-50 p-3 rounded-lg"
                                    >
                                        <Check className="w-5 h-5 text-indigo-500 flex-shrink-0" />
                                        <span className="text-gray-700 font-suite">{benefit}</span>
                                    </div>
                                ))}
                            </div>
                        </div>

                        <Button
                            variant="default"
                            onClick={onClose}
                            className="w-full h-12 bg-gradient-to-r from-indigo-500 to-blue-500 
                            hover:from-indigo-600 hover:to-blue-600 text-white font-suite font-medium"
                        >
                            확인
                        </Button>
                    </div>
                </motion.div>
            </div>
        </>
    );
});

PremiumModal.displayName = 'PremiumModal';

export default PremiumModal;