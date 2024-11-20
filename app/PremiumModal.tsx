import { useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Check, AlertCircle, Crown, Sparkles, Zap, Ban } from 'lucide-react';
import { Button } from "./components/ui/button";
import { usePremium } from '@/contexts/PremiumContext';

interface CenterModalProps {
    show: boolean;
    onClose: () => void;
    children: React.ReactNode;
}

const CenterModal = ({ show, onClose, children }: CenterModalProps) => {
    if (!show) return null;

    return (
        <>
            <div
                className="fixed inset-0 bg-black/50 backdrop-blur-sm z-[100]"
                onClick={onClose}
            />
            <div className="fixed inset-0 flex items-center justify-center z-[101]">
                <motion.div
                    initial={{ opacity: 0, y: -20 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -20 }}
                    className="w-full max-w-sm bg-white rounded-xl shadow-xl mx-4"
                    onClick={(e) => e.stopPropagation()}
                >
                    {children}
                </motion.div>
            </div>
        </>
    );
};

export interface PremiumModalProps {
    show: boolean;
    onClose: () => void;
}

export const PremiumModal = ({ show, onClose }: PremiumModalProps) => {
    const { 
        isPremium, 
        purchaseDate, 
        handlePurchase,
        handleRestore, // 추가
        isProcessing 
    } = usePremium();

    useEffect(() => {
        if (show) {
            window.closePaymentModal = () => {
                onClose();
            };

            return () => {
                if (window.closePaymentModal) window.closePaymentModal = undefined;
            };
        }
    }, [show, onClose]);

    const benefits = [
        {
            icon: <Ban className="w-5 h-5 text-indigo-500" />,
            text: "광고 없는 깔끔한 학습 환경",
            description: "10문제마다 나오는 광고가 표시되지 않아요"
        },
        {
            icon: <Sparkles className="w-5 h-5 text-indigo-500" />,
            text: "끊김 없는 학습 경험",
            description: "광고 대기 시간 없이 집중할 수 있어요"
        },
        {
            icon: <Zap className="w-5 h-5 text-indigo-500" />,
            text: "빠른 학습 진행",
            description: "더 효율적인 학습이 가능해요"
        }
    ];

    const warnings = [
        { icon: "📱", text: "프리미엄은 현재 기기에서만 사용 가능" },
        { icon: "🔄", text: "앱 재설치 시 재구매 필요" },
        { icon: "⚡", text: "구매 후 즉시 적용" }
    ];

    return (
        <CenterModal show={show} onClose={onClose}>
            <div className="p-4">
                <div className="flex justify-between items-center mb-4">
                    <div>
                        <h3 className="text-2xl font-suite font-bold text-indigo-600">
                            {isPremium ? '프리미엄 멤버' : '프리미엄으로 업그레이드'}
                        </h3>
                        <p className="text-gray-600 mt-1">
                            {isPremium ? '모든 프리미엄 혜택을 이용 중입니다' : '더 나은 학습 경험을 시작하세요'}
                        </p>
                    </div>
                    <button
                        onClick={onClose}
                        className="text-gray-400 hover:text-gray-600 transition-colors"
                    >
                        <X className="h-6 w-6" />
                    </button>
                </div>

                {isPremium ? (
                    <div className="space-y-3">
                        <div className="bg-indigo-50 p-3 rounded-xl border-2 border-indigo-100">
                            <div className="flex items-center gap-2 mb-1">
                                <Crown className="w-6 h-6 text-indigo-500" />
                                <span className="font-suite font-bold text-indigo-700">프리미엄 사용 중</span>
                            </div>
                            {purchaseDate && (
                                <p className="text-sm text-indigo-600">
                                    구매일: {new Date(purchaseDate).toLocaleDateString()}
                                </p>
                            )}
                        </div>
                        
                        <Button 
                            variant="outline"
                            onClick={onClose}
                            className="w-full text-indigo-600 hover:bg-indigo-50"
                        >
                            닫기
                        </Button>
                    </div>
                ) : (
                    <div className="space-y-4">
                        <div className="grid gap-3">
                            {benefits.map((benefit, index) => (
                                <div 
                                    key={index} 
                                    className="flex items-start gap-4 p-3 rounded-xl bg-white border-2 border-indigo-50 hover:border-indigo-100 transition-colors"
                                >
                                    <div className="flex-shrink-0 p-2 bg-indigo-50 rounded-lg">
                                        {benefit.icon}
                                    </div>
                                    <div>
                                        <h4 className="font-suite font-bold text-gray-900">
                                            {benefit.text}
                                        </h4>
                                        <p className="text-sm text-gray-600 mt-0">
                                            {benefit.description}
                                        </p>
                                    </div>
                                </div>
                            ))}
                        </div>

                        <div className="space-y-2">
                            <div className="flex items-center gap-2 text-sm text-amber-700">
                                <AlertCircle className="w-4 h-4" />
                                <span className="font-medium">구매 전 확인사항</span>
                            </div>
                            <div className="bg-amber-50 p-3 rounded-xl border border-amber-100 space-y-2">
                                {warnings.map((warning, index) => (
                                    <div key={index} className="flex items-center gap-2 text-sm">
                                        <span className="text-lg">{warning.icon}</span>
                                        <span className="text-amber-700">{warning.text}</span>
                                    </div>
                                ))}
                            </div>
                        </div>

                        <div className="space-y-2">
                            <Button
                                variant="default"
                                onClick={handlePurchase}
                                disabled={isProcessing}
                                className="w-full bg-gradient-to-r from-indigo-500 to-indigo-600 hover:from-indigo-600 hover:to-indigo-700 text-white py-3 rounded-xl font-suite font-bold text-lg flex items-center justify-center"
                            >
                                <Crown className="w-5 h-5 mr-2 align-middle" />
                                <span className="align-middle">
                                    {isProcessing ? '처리 중...' : '프리미엄 시작하기'}
                                </span>
                            </Button>
                            <Button
                                variant="outline"
                                onClick={handleRestore}
                                disabled={isProcessing}
                                className="w-full flex items-center justify-center"
                            >
                                <Sparkles className="w-4 h-4 mr-2" />
                                {isProcessing ? '복원 중...' : '구매 복원'}
                            </Button>
                            <Button
                                variant="ghost"
                                onClick={onClose}
                                className="w-full"
                            >
                                나중에 하기
                            </Button>
                            <p className="text-xs text-center text-gray-500">
                                구매 시 위 내용에 동의하는 것으로 간주됩니다
                            </p>
                        </div>
                    </div>
                )}
            </div>
        </CenterModal>
    );
};

PremiumModal.displayName = 'PremiumModal';
CenterModal.displayName = 'CenterModal';

export default PremiumModal;
