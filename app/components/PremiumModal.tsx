// components/PremiumModal.tsx
import { motion, AnimatePresence } from "framer-motion";
import { Button } from "./ui/button";
import {
  Ban,
  Crown,
  Infinity,
  Zap,
  Check,
  X,
  Sparkles
} from "lucide-react";

interface PremiumModalProps {
  show: boolean;
  onClose: () => void;
  onPurchase: () => Promise<void>;
  isPremium: boolean;
}

export default function PremiumModal({
  show,
  onClose,
  onPurchase,
  isPremium
}: PremiumModalProps) {
  if (!show) return null;

  const benefits = [
    {
      icon: <Ban className="w-5 h-5 text-rose-500" />,
      title: "광고 제거",
      description: "학습에 방해되는 모든 광고가 제거됩니다"
    },
    {
      icon: <Infinity className="w-5 h-5 text-violet-500" />,
      title: "무제한 학습",
      description: "광고 없이 무제한으로 학습할 수 있습니다"
    },
    {
      icon: <Zap className="w-5 h-5 text-amber-500" />,
      title: "빠른 진행",
      description: "중간 광고 없이 빠르게 학습을 진행하세요"
    },
    {
      icon: <Sparkles className="w-5 h-5 text-blue-500" />,
      title: "1회 결제",
      description: "한 번의 결제로 평생 이용이 가능합니다"
    }
  ];

  return (
    <AnimatePresence>
      {show && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="fixed inset-0 z-50 flex items-center justify-center p-4"
        >
          <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onClose} />
          
          <motion.div
            initial={{ scale: 0.95, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            exit={{ scale: 0.95, opacity: 0 }}
            className="relative bg-white w-full max-w-md rounded-2xl shadow-xl overflow-hidden"
          >
            {/* 헤더 */}
            <div className="relative bg-gradient-to-br from-violet-500 to-indigo-600 p-6">
              <button 
                onClick={onClose}
                className="absolute top-4 right-4 text-white/80 hover:text-white"
              >
                <X className="w-6 h-6" />
              </button>
              
              <div className="flex flex-col items-center text-white">
                <Crown className="w-12 h-12 mb-3" />
                <h2 className="text-2xl font-suite font-bold mb-2">프리미엄으로 업그레이드</h2>
                <p className="text-white/90 text-center">
                  광고 없이 더 효과적으로 학습하세요
                </p>
              </div>
            </div>

            {/* 혜택 목록 */}
            <div className="p-6">
              <div className="space-y-4 mb-6">
                {benefits.map((benefit, index) => (
                  <div key={index} className="flex items-start gap-4">
                    <div className="p-2 rounded-full bg-gray-50">
                      {benefit.icon}
                    </div>
                    <div>
                      <h3 className="font-suite font-semibold text-gray-900">
                        {benefit.title}
                      </h3>
                      <p className="text-sm text-gray-500">
                        {benefit.description}
                      </p>
                    </div>
                  </div>
                ))}
              </div>

              {/* 가격 정보 */}
              <div className="bg-gray-50 rounded-xl p-4 mb-6">
                <div className="flex justify-between items-center mb-1">
                  <span className="font-suite font-medium text-gray-900">평생 이용권</span>
                  <div className="flex items-center gap-2">
                    <span className="text-sm line-through text-gray-400">
                      ₩6,900
                    </span>
                    <span className="text-lg font-suite font-bold text-violet-600">
                      ₩3,900
                    </span>
                  </div>
                </div>
                <p className="text-sm text-gray-500">
                  1회 결제로 평생 이용 가능합니다
                </p>
              </div>

              {/* 구매 버튼 */}
              <Button
                variant="primary"
                onClick={onPurchase}
                disabled={isPremium}
                className="w-full h-12 bg-violet-600 hover:bg-violet-700 text-white
                  font-suite font-medium rounded-xl flex items-center justify-center gap-2"
              >
                {isPremium ? (
                  <>
                    <Check className="w-5 h-5" />
                    이미 구매한 상품입니다
                  </>
                ) : (
                  <>
                    <Crown className="w-5 h-5" />
                    프리미엄 구매하기
                  </>
                )}
              </Button>
              
              <p className="mt-4 text-xs text-center text-gray-500">
                앱 삭제 전까지 계속 유지됩니다
              </p>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}