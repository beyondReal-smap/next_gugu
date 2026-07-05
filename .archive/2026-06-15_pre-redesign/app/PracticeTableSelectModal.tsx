import { AnimatePresence, motion, useMotionValue } from 'framer-motion';
import { useRef, useState } from 'react';
import { Star } from 'lucide-react';

interface PracticeStats {
    attempts: number;
    correct: number;
}

interface PracticeTableSelectModalProps {
    show: boolean;
    onClose: () => void;
    onTableSelect: (table: number) => void;
    currentTable: number;
    practiceStats: Record<number, PracticeStats>;
    showAlert: (message: string, type: 'success' | 'error') => void;
}

const backgroundVariants = {
    hidden: { opacity: 0 },
    visible: { opacity: 1 }
};

const getStatusColor = (stats: PracticeStats | undefined): string => {
    if (!stats || stats.attempts === 0) return 'bg-clay-bg text-clay-muted shadow-clay-sm';
    const accuracy = Math.round((stats.correct / stats.attempts) * 100);
    if (accuracy >= 90) return 'bg-clay-mint-light/40 text-clay-mint-dark shadow-clay-mint';
    if (accuracy >= 70) return 'bg-clay-yellow-light/40 text-clay-yellow-dark shadow-clay-yellow';
    return 'bg-clay-bg text-clay-muted shadow-clay-sm';
};

const getAccuracy = (stats: PracticeStats | undefined): number => {
    if (!stats || stats.attempts === 0) return 0;
    return Math.round((stats.correct / stats.attempts) * 100);
};

const PracticeTableSelectModal = ({ 
    show,
    onClose, 
    onTableSelect, 
    currentTable, 
    practiceStats, 
    showAlert 
}: PracticeTableSelectModalProps) => {
    const [isDragging, setIsDragging] = useState(false);
    const [exitVelocity, setExitVelocity] = useState(0);
    const modalRef = useRef<HTMLDivElement>(null);
    const dragY = useMotionValue(0);
    const windowHeight = typeof window !== 'undefined' ? window.innerHeight : 1000;

    const getExitAnimation = () => {
        // 화면 높이를 기준으로 모달이 완전히 사라질 때까지의 거리 계산
        const distance = windowHeight;
        
        return {
            y: distance,
            transition: {
                type: 'spring',
                // 드래그 속도에 따라 damping 조절 (더 부드러운 감속을 위해 값 조정)
                damping: Math.max(15, 30 - Math.abs(exitVelocity / 100)),
                // 드래그 속도에 따라 stiffness 조절 (더 자연스러운 움직임을 위해 값 조정)
                stiffness: Math.min(300, 200 + Math.abs(exitVelocity)),
                // 드래그 방향과 속도를 유지하기 위한 초기 속도 설정
                velocity: exitVelocity,
                // 최소 지속 시간 설정으로 너무 빠르게 사라지는 것 방지
                duration: Math.max(0.3, Math.min(0.8, Math.abs(exitVelocity) / 2000))
            }
        };
    };

    const modalVariants = {
        hidden: { y: '100%' },
        visible: { 
            y: 0,
            transition: {
                type: 'spring',
                damping: 30,
                stiffness: 300
            }
        },
        exit: getExitAnimation()
    };
    
    const handleDrag = (_: any, info: { velocity: { y: number }; offset: { y: number } }) => {
        const speed = Math.abs(info.velocity.y);
        const offset = info.offset.y;
        
        // 드래그 중에 opacity 조절
        if (modalRef.current) {
            const opacity = Math.max(0, 1 - (offset / (windowHeight * 0.5)));
            modalRef.current.style.opacity = opacity.toString();
        }

        if (speed > 500 && offset > 50) {
            setExitVelocity(info.velocity.y);
            onClose();
        }
    };

    const handleDragEnd = (_: any, info: { offset: { y: number }; velocity: { y: number } }) => {
        setIsDragging(false);
        
        const threshold = windowHeight * 0.2; // 화면 높이의 20%를 임계값으로 설정
        
        if (info.offset.y > threshold || (info.velocity.y > 50 && info.offset.y > 50)) {
            setExitVelocity(info.velocity.y);
            onClose();
        } else {
            // 원위치로 돌아갈 때 opacity 복구
            if (modalRef.current) {
                modalRef.current.style.opacity = '1';
            }
        }
    };

    return (
        <AnimatePresence mode="wait">
            {show && (
                <motion.div
                    key="modal-container"
                    initial="hidden"
                    animate="visible"
                    exit="hidden"
                    variants={backgroundVariants}
                    className="fixed inset-0 z-50 flex items-end justify-center"
                >
                    <motion.div
                        className="fixed inset-0 bg-clay-ink/40 backdrop-blur-sm"
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        exit={{ opacity: 0 }}
                        onClick={() => {
                            setExitVelocity(0);
                            onClose();
                        }}
                    />
                    <motion.div
                        ref={modalRef}
                        style={{ y: dragY }}
                        variants={modalVariants}
                        initial="hidden"
                        animate="visible"
                        exit="exit"
                        drag="y"
                        dragDirectionLock
                        dragConstraints={{ top: 0 }}
                        dragElastic={{ top: 0, bottom: 0.7 }}
                        onDragStart={() => setIsDragging(true)}
                        onDrag={handleDrag}
                        onDragEnd={handleDragEnd}
                        className={`
                            relative w-full bg-clay-bg rounded-t-clay border-[3px] border-white shadow-clay
                            overflow-hidden touch-none select-none max-w-2xl
                            ${isDragging ? 'cursor-grabbing' : 'cursor-grab'}
                        `}
                    >
                        <div className="flex justify-center pt-4 pb-2">
                            <div className="w-12 h-1.5 bg-clay-border rounded-full"/>
                        </div>

                        <div className="px-6 pb-2">
                            <h3 className="text-xl font-suite font-bold text-clay-blue">구구단 선택</h3>
                            <p className="text-clay-muted text-sm mt-1">연습할 구구단을 선택하세요</p>
                        </div>

                        <div className="p-6 pt-2">
                            <div className="grid grid-cols-4 gap-3">
                                {Array.from({ length: 18 }, (_, i) => i + 2).map((table) => {
                                    const stats = practiceStats[table];
                                    const accuracy = getAccuracy(stats);
                                    const isCurrent = table === currentTable;

                                    return (
                                        <motion.div
                                            key={table}
                                            whileHover={{ scale: 1.05 }}
                                            whileTap={{ scale: 0.95 }}
                                            className="relative"
                                        >
                                            <button
                                                onClick={() => {
                                                    onTableSelect(table);
                                                    showAlert(`${table}단 연습을 시작합니다! 💪`, 'success');
                                                    onClose();
                                                    const savedState = JSON.parse(localStorage.getItem('multiplicationGame') || '{}');
                                                    localStorage.setItem('multiplicationGame', JSON.stringify({
                                                        ...savedState,
                                                        selectedTable: table,
                                                        lastPracticeDate: new Date().toISOString()
                                                    }));
                                                }}
                                                className={`
                                                    clay-btn w-full aspect-square text-base font-suite font-medium
                                                    flex items-center justify-center relative
                                                    transition-all duration-300
                                                    ${isCurrent
                                                        ? 'bg-clay-blue text-white shadow-clay-blue'
                                                        : getStatusColor(stats)}
                                                `}
                                            >
                                                <div className="flex flex-col items-center gap-1">
                                                    <span className="num-display text-lg">{table}단</span>
                                                    {stats && stats.attempts > 0 && (
                                                        <div className="flex items-center gap-1 text-xs">
                                                            <Star className="w-3 h-3" />
                                                            <span>{accuracy}%</span>
                                                        </div>
                                                    )}
                                                </div>
                                            </button>
                                            {accuracy >= 90 && (
                                                <motion.div
                                                    initial={{ scale: 0 }}
                                                    animate={{ scale: 1 }}
                                                    className="absolute -top-1 -right-1 w-3 h-3 bg-clay-mint rounded-full
                                                    border-2 border-white shadow-clay-mint z-10"
                                                    title="마스터 완료!"
                                                />
                                            )}
                                        </motion.div>
                                    );
                                })}
                            </div>

                            <div className="mt-6 flex items-center justify-center gap-6 text-sm">
                                <div className="flex items-center gap-2">
                                    <div className="w-3 h-3 bg-clay-mint rounded-full shadow-clay-mint" />
                                    <span className="text-clay-muted">90% 이상</span>
                                </div>
                                <div className="flex items-center gap-2">
                                    <div className="w-3 h-3 bg-clay-yellow rounded-full shadow-clay-yellow" />
                                    <span className="text-clay-muted">70% 이상</span>
                                </div>
                                <div className="flex items-center gap-2">
                                    <div className="w-3 h-3 bg-clay-blue rounded-full shadow-clay-blue" />
                                    <span className="text-clay-muted">현재 선택</span>
                                </div>
                            </div>
                        </div>
                    </motion.div>
                </motion.div>
            )}
        </AnimatePresence>
    );
};

export default PracticeTableSelectModal;