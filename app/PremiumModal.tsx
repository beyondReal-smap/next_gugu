import { useEffect, useState } from 'react';
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
            <div className="fixed inset-0 bg-black/50 backdrop-blur-sm z-[100]" onClick={onClose} />
            <div className="fixed inset-0 flex items-center justify-center z-[101] p-4">
                <motion.div
                    initial={{ opacity: 0, y: -20 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -20 }}
                    className="w-full max-w-sm bg-white rounded-xl shadow-xl overflow-hidden"
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

type OperationType = 'restore' | 'purchase' | null;

export const PremiumModal = ({ show, onClose }: PremiumModalProps) => {
    const {
        isPremium,
        purchaseDate,
        handlePurchase,
        handleRestore,
        isProcessing: contextProcessing
    } = usePremium();

    const [currentOperation, setCurrentOperation] = useState<OperationType>(null);
    const [isProcessing, setIsProcessing] = useState(false);
    const [error, setError] = useState<string | null>(null);

    // 타임아웃 Promise 생성 함수
    const createTimeoutPromise = (operation: string) => {
        return new Promise((_, reject) => {
            setTimeout(() => {
                reject(new Error(`${operation} 시간이 초과되었습니다`));
            }, 3000);
        });
    };

    // 작업 초기화 함수
    const resetState = () => {
        setIsProcessing(false);
        setCurrentOperation(null);
        setError(null);
    };

    const handleOperationWithTimeout = async (operation: OperationType, handler: () => Promise<any>) => {
        try {
            setIsProcessing(true);
            setCurrentOperation(operation);
            setError(null);

            await Promise.race([
                handler(),
                createTimeoutPromise(operation === 'restore' ? '복원' : '구매')
            ]);

        } catch (error) {
            setError(error instanceof Error ? error.message : `${operation === 'restore' ? '복원' : '구매'} 중 오류가 발생했습니다`);
            console.error(`${operation} error:`, error);
        } finally {
            setIsProcessing(false);
            // 3초 후에 에러 메시지와 상태를 초기화
            setTimeout(resetState, 3000);
        }
    };

    const handleRestoreClick = () => {
        handleOperationWithTimeout('restore', handleRestore);
    };

    const handlePurchaseClick = () => {
        handleOperationWithTimeout('purchase', handlePurchase);
    };

    useEffect(() => {
        if (show) {
            window.closePaymentModal = () => { onClose(); };
            return () => { window.closePaymentModal = undefined; };
        }
    }, [show, onClose]);

    // contextProcessing이 false로 변경되면 상태 초기화
    useEffect(() => {
        if (!contextProcessing && isProcessing) {
            resetState();
        }
    }, [contextProcessing]);

    const benefits = [
        {
            icon: <Ban className="w-5 h-5 text-indigo-500" />,
            text: "광고 없는 학습 환경",
            description: "10문제 후 광고가 표시되지 않아요"
        },
        {
            icon: <Sparkles className="w-5 h-5 text-indigo-500" />,
            text: "끊김 없는 학습",
            description: "쉬지 않고 계속 풀 수 있어요"
        },
        {
            icon: <Zap className="w-5 h-5 text-indigo-500" />,
            text: "빠른 학습 진행",
            description: "더 효율적인 학습이 가능해요"
        }
    ];

    return (
        <CenterModal show={show} onClose={onClose}>
            <div className="p-4 max-h-[85vh] overflow-y-auto">
                <div className="flex justify-between items-start mb-4">
                    <div>
                        <h3 className="text-xl font-suite font-bold text-indigo-600">
                            {isPremium ? '프리미엄 멤버' : '프리미엄으로 업그레이드'}
                        </h3>
                        <p className="text-sm text-gray-600">
                            {isPremium ? '모든 프리미엄 혜택을 이용 중입니다' : '더 나은 학습 경험을 시작하세요'}
                        </p>
                    </div>
                    <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
                        <X className="h-5 w-5" />
                    </button>
                </div>

                {isPremium ? (
                    <div className="space-y-3">
                        <div className="bg-indigo-50 p-3 rounded-xl border border-indigo-100">
                            <div className="flex items-center gap-2">
                                <Crown className="w-5 h-5 text-indigo-500" />
                                <span className="font-suite font-bold text-indigo-700">프리미엄 사용 중</span>
                            </div>
                            {purchaseDate && (
                                <p className="text-sm text-indigo-600 mt-1">
                                    구매일: {new Date(purchaseDate).toLocaleDateString()}
                                </p>
                            )}
                        </div>
                        <Button
                            variant="outline"
                            onClick={onClose}
                            className="w-full"
                        >
                            닫기
                        </Button>
                    </div>
                ) : (
                    <div className="space-y-4">
                        <div className="bg-amber-50 border-2 border-amber-200 rounded-xl p-4">
                            <div className="flex flex-col gap-2">
                                <div className="flex items-center gap-2 mb-1">
                                    <Sparkles className="w-5 h-5 text-amber-500" />
                                    <p className="font-bold text-amber-900">이전에 구매하신 적이 있나요?</p>
                                </div>
                                <p className="text-sm text-amber-700 mb-2">
                                    기기 변경 또는 재설치 후에는 구매를 복원해주세요
                                </p>
                                <Button
                                    variant="default"
                                    onClick={handleRestoreClick}
                                    disabled={isProcessing || contextProcessing}
                                    className="w-full !bg-amber-500 !hover:bg-amber-600 text-white py-2.5 rounded-lg font-medium shadow-sm transition-colors border-0 disabled:bg-amber-400 [&:not(:disabled)]:hover:bg-amber-600"
                                >
                                    {currentOperation === 'restore' && isProcessing ? '복원 중...' : '구매 복원하기'}
                                </Button>
                                {error && currentOperation === 'restore' && (
                                    <p className="text-sm text-red-600 mt-2 flex items-center gap-1">
                                        <AlertCircle className="w-4 h-4" />
                                        {error}
                                    </p>
                                )}
                            </div>
                        </div>

                        <div className="flex flex-col divide-y divide-gray-100">
                            {benefits.map((benefit, index) => (
                                <div key={index} className="flex items-center gap-3 py-3">
                                    <div className="flex-shrink-0 p-2 bg-indigo-50 rounded-lg">
                                        {benefit.icon}
                                    </div>
                                    <div>
                                        <h4 className="font-bold text-gray-900">{benefit.text}</h4>
                                        <p className="text-sm text-gray-600">{benefit.description}</p>
                                    </div>
                                </div>
                            ))}
                        </div>

                        <div className="space-y-3 pt-2">
                            <Button
                                variant="default"
                                onClick={handlePurchaseClick}
                                disabled={isProcessing || contextProcessing}
                                className="w-full bg-gradient-to-r from-indigo-500 to-indigo-600 hover:from-indigo-600 hover:to-indigo-700 text-white py-2.5 rounded-xl font-medium text-base flex items-center justify-center shadow-sm"
                            >
                                <Crown className="w-5 h-5 mr-2" />
                                {currentOperation === 'purchase' && isProcessing ? '처리 중...' : '프리미엄 시작하기'}
                            </Button>
                            {error && currentOperation === 'purchase' && (
                                <p className="text-sm text-red-600 mt-2 flex items-center gap-1">
                                    <AlertCircle className="w-4 h-4" />
                                    {error}
                                </p>
                            )}
                            <Button
                                variant="default"
                                onClick={onClose}
                                className="w-full py-2.5 text-base font-medium !text-gray-700 hover:!text-white rounded-xl border-2 border-slate-200 bg-white hover:bg-indigo-300 hover:border-indigo-300 transition-all duration-200"
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