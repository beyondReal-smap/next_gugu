import { motion, AnimatePresence } from 'framer-motion';
import { X, AlertCircle, Crown } from 'lucide-react';
import { Button } from "../app/components/ui/button";

interface CenterModalProps {
    show: boolean;
    onClose: () => void;
    children: React.ReactNode;
}

export const CenterModal: React.FC<CenterModalProps> = ({ show, onClose, children }) => {
    if (!show) return null;

    return (
        <>
            <div
                className="fixed inset-0 bg-black/50 backdrop-blur-sm z-[100]"
                onClick={onClose}
            />
            <div className="fixed inset-0 flex items-center justify-center z-[101] p-4">
                <motion.div
                    initial={{ opacity: 0, y: -20 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -20 }}
                    className="w-full max-w-md bg-white rounded-2xl shadow-xl"
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
    onPurchase?: () => Promise<void>;
    purchaseDate?: string;
}

export const PremiumModal: React.FC<PremiumModalProps> = ({
    show,
    onClose,
    onPurchase,
    purchaseDate
}) => {
    const benefits = [
        { icon: "🎯", text: "10문제마다 나오는 광고가 표시되지 않아요" },
        { icon: "🔄", text: "광고 없이 끊김없는 학습이 가능해요" },
        { icon: "⚡", text: "더 빠르고 효율적인 학습이 가능해요" }
    ];

    const warnings = [
        { icon: "📱", text: "프리미엄은 구매한 기기에서만 적용됩니다" },
        { icon: "🗑️", text: "앱을 삭제하면 구매 이력이 초기화됩니다" },
        { icon: "💡", text: "재설치 시 다시 구매가 필요합니다" }
    ];

    return (
        <CenterModal show={show} onClose={onClose}>
            <div className="p-6 relative">
                {/* 닫기 버튼 */}
                <button
                    onClick={onClose}
                    className="absolute top-4 right-4 text-gray-400 hover:text-gray-600 transition-colors"
                >
                    <X className="h-5 w-5" />
                </button>

                <h3 className="text-xl font-bold mb-6 text-gray-900">
                    {purchaseDate ? '프리미엄 상태' : '프리미엄으로 업그레이드'}
                </h3>

                {purchaseDate ? (
                    <div className="space-y-4">
                        <div className="bg-amber-50 p-4 rounded-lg">
                            <div className="flex items-center gap-2 mb-2">
                                <Crown className="w-5 h-5 text-amber-500" />
                                <span className="font-medium text-amber-700">프리미엄 사용 중</span>
                            </div>
                            <p className="text-sm text-amber-600">
                                구매일: {new Date(purchaseDate).toLocaleDateString()}
                            </p>
                        </div>
                        
                        <div className="space-y-2">
                            <p className="text-sm text-gray-600">현재 모든 프리미엄 기능을 사용할 수 있습니다</p>
                        </div>
                    </div>
                ) : (
                    <div className="space-y-6">
                        {/* 프리미엄 혜택 */}
                        <div className="space-y-3">
                            <h4 className="font-semibold text-gray-900">프리미엄 혜택</h4>
                            <div className="space-y-2">
                                {benefits.map((benefit, index) => (
                                    <div key={index} className="flex items-center gap-2 text-sm">
                                        <span className="text-xl">{benefit.icon}</span>
                                        <span className="text-gray-700">{benefit.text}</span>
                                    </div>
                                ))}
                            </div>
                        </div>

                        {/* 경고 사항 */}
                        <div className="space-y-3">
                            <h4 className="font-semibold text-gray-900 flex items-center gap-2">
                                <AlertCircle className="w-4 h-4 text-amber-500" />
                                <span>주의사항</span>
                            </h4>
                            <div className="space-y-2 bg-amber-50 p-4 rounded-lg">
                                {warnings.map((warning, index) => (
                                    <div key={index} className="flex items-start gap-2 text-sm">
                                        <span className="text-xl flex-shrink-0">{warning.icon}</span>
                                        <span className="text-amber-700">{warning.text}</span>
                                    </div>
                                ))}
                            </div>
                        </div>

                        {/* 구매 버튼 */}
                        <div className="space-y-3">
                            <Button
                                variant="default"
                                onClick={onPurchase}
                                className="w-full bg-gradient-to-r from-amber-500 to-amber-600 hover:from-amber-600 hover:to-amber-700 text-white font-semibold h-12"
                            >
                                프리미엄 구매하기
                            </Button>
                            <p className="text-xs text-center text-gray-500">
                                구매하시면 위 내용에 동의하는 것으로 간주됩니다
                            </p>
                        </div>
                    </div>
                )}
            </div>
        </CenterModal>
    );
};