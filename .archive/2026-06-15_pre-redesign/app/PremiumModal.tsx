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
            <div className="fixed inset-0 bg-clay-ink/40 backdrop-blur-sm z-[100]" onClick={onClose} />
            <div className="fixed inset-0 flex items-center justify-center z-[101] p-4">
                <motion.div
                    initial={{ opacity: 0, y: -20 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -20 }}
                    className="w-full max-w-sm clay-card shadow-clay overflow-hidden"
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
            icon: <Ban className="w-5 h-5 text-clay-blue" />,
            text: "광고 없는 학습 환경",
            description: "10문제 후 광고가 표시되지 않아요"
        },
        {
            icon: <Sparkles className="w-5 h-5 text-clay-purple" />,
            text: "끊김 없는 학습",
            description: "쉬지 않고 계속 풀 수 있어요"
        },
        {
            icon: <Zap className="w-5 h-5 text-clay-pink" />,
            text: "빠른 학습 진행",
            description: "더 효율적인 학습이 가능해요"
        }
    ];

    return (
        <CenterModal show={show} onClose={onClose}>
            <div className="p-4 max-h-[85vh] overflow-y-auto">
                <div className="flex justify-between items-start mb-4">
                    <div>
                        <h3 className="text-xl font-suite font-bold text-clay-yellow">
                            {isPremium ? '프리미엄 멤버' : '프리미엄으로 업그레이드'}
                        </h3>
                        <p className="text-sm text-clay-muted">
                            {isPremium ? '모든 프리미엄 혜택을 이용 중입니다' : '더 나은 학습 경험을 시작하세요'}
                        </p>
                    </div>
                    <button onClick={onClose} className="text-clay-muted hover:text-clay-ink">
                        <X className="h-5 w-5" />
                    </button>
                </div>

                {isPremium ? (
                    <div className="space-y-3">
                        <div className="bg-clay-yellow/10 p-3 rounded-clay-sm border-2 border-clay-yellow-light">
                            <div className="flex items-center gap-2">
                                <Crown className="w-5 h-5 text-clay-yellow" />
                                <span className="font-suite font-bold text-clay-yellow-dark">프리미엄 사용 중</span>
                            </div>
                            {purchaseDate && (
                                <p className="text-sm text-clay-yellow-dark mt-1">
                                    구매일: {new Date(purchaseDate).toLocaleDateString()}
                                </p>
                            )}
                        </div>
                        <Button
                            variant="outline"
                            onClick={onClose}
                            className="w-full clay-btn bg-white text-clay-ink shadow-clay-sm"
                        >
                            닫기
                        </Button>
                    </div>
                ) : (
                    <div className="space-y-4">
                        <div className="bg-clay-yellow/10 border-2 border-clay-yellow-light rounded-clay-sm p-4">
                            <div className="flex flex-col gap-2">
                                <div className="flex items-center gap-2 mb-1">
                                    <Sparkles className="w-5 h-5 text-clay-yellow" />
                                    <p className="font-bold text-clay-yellow-dark">이전에 구매하신 적이 있나요?</p>
                                </div>
                                <p className="text-sm text-clay-muted mb-2">
                                    기기 변경 또는 재설치 후에는 구매를 복원해주세요
                                </p>
                                <Button
                                    variant="default"
                                    onClick={handleRestoreClick}
                                    disabled={isProcessing || contextProcessing}
                                    className="w-full clay-btn !bg-clay-yellow text-white py-2.5 shadow-clay-yellow font-medium transition-all disabled:!bg-clay-yellow-light [&:not(:disabled)]:hover:!bg-clay-yellow-dark"
                                >
                                    {currentOperation === 'restore' && isProcessing ? '복원 중...' : '구매 복원하기'}
                                </Button>
                                {error && currentOperation === 'restore' && (
                                    <p className="text-sm text-clay-pink mt-2 flex items-center gap-1">
                                        <AlertCircle className="w-4 h-4" />
                                        {error}
                                    </p>
                                )}
                            </div>
                        </div>

                        <div className="flex flex-col divide-y divide-clay-border">
                            {benefits.map((benefit, index) => (
                                <div key={index} className="flex items-center gap-3 py-3">
                                    <div className="flex-shrink-0 p-2 bg-clay-bg rounded-clay-sm">
                                        {benefit.icon}
                                    </div>
                                    <div>
                                        <h4 className="font-bold text-clay-ink">{benefit.text}</h4>
                                        <p className="text-sm text-clay-muted">{benefit.description}</p>
                                    </div>
                                </div>
                            ))}
                        </div>

                        <div className="space-y-3 pt-2">
                            <Button
                                variant="default"
                                onClick={handlePurchaseClick}
                                disabled={isProcessing || contextProcessing}
                                className="w-full clay-btn bg-clay-yellow hover:bg-clay-yellow-dark text-white py-2.5 font-medium text-base flex items-center justify-center shadow-clay-yellow"
                            >
                                <Crown className="w-5 h-5 mr-2" />
                                {currentOperation === 'purchase' && isProcessing ? '처리 중...' : '프리미엄 시작하기'}
                            </Button>
                            {error && currentOperation === 'purchase' && (
                                <p className="text-sm text-clay-pink mt-2 flex items-center gap-1">
                                    <AlertCircle className="w-4 h-4" />
                                    {error}
                                </p>
                            )}
                            <Button
                                variant="default"
                                onClick={onClose}
                                className="w-full clay-btn py-2.5 text-base font-medium !text-clay-ink hover:!text-white bg-white shadow-clay-sm hover:bg-clay-purple transition-all duration-200"
                            >
                                나중에 하기
                            </Button>
                            <p className="text-xs text-center text-clay-muted">
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