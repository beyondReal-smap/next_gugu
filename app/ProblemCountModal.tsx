import React, { useRef, useState } from 'react';
import { Hash, Check } from "lucide-react";
import { Button } from "./components/ui/button";
import { motion, AnimatePresence, useMotionValue } from 'framer-motion';

interface ProblemCountModalProps {
    show: boolean;
    onClose: () => void;
    onCountSelect: (count: number) => void;
    currentCount: number;
}

const CenterModal = ({ show, onClose, children }: {
    show: boolean;
    onClose: () => void;
    children: React.ReactNode;
}) => {
    const modalRef = useRef<HTMLDivElement>(null);
    const [isDragging, setIsDragging] = useState(false);
    const [exitVelocity, setExitVelocity] = useState(0);
    const dragY = useMotionValue(0);
    const windowHeight = typeof window !== 'undefined' ? window.innerHeight : 1000;

    const getExitAnimation = () => {
        return {
            y: windowHeight,
            transition: {
                type: 'spring',
                damping: Math.max(15, 30 - Math.abs(exitVelocity / 100)),
                stiffness: Math.min(300, 200 + Math.abs(exitVelocity)),
                velocity: exitVelocity,
                duration: Math.max(0.3, Math.min(0.8, Math.abs(exitVelocity) / 2000))
            }
        };
    };

    const modalVariants = {
        hidden: { y: windowHeight },
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
        
        const threshold = windowHeight * 0.2;
        
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
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    className="fixed inset-0 z-50 flex items-end justify-center"
                >
                    <motion.div 
                        className="fixed inset-0 bg-black/50 backdrop-blur-sm"
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
                            relative w-full max-w-lg bg-white rounded-t-2xl shadow-xl 
                            overflow-hidden touch-none select-none
                            ${isDragging ? 'cursor-grabbing' : 'cursor-grab'}
                        `}
                    >
                        <div className="flex justify-center pt-4 pb-2">
                            <div className="w-12 h-1.5 bg-gray-300 rounded-full"/>
                        </div>
                        {children}
                    </motion.div>
                </motion.div>
            )}
        </AnimatePresence>
    );
};

const ProblemCountModal = ({
    show,
    onClose,
    onCountSelect,
    currentCount
}: ProblemCountModalProps) => {
    const countOptions = [10, 15, 20, 25, 30];

    return (
        <CenterModal show={show} onClose={onClose}>
            <div className="p-6">
                <div className="mb-6">
                    <h3 className="text-2xl font-suite font-bold text-indigo-600">
                        문제 수 설정
                    </h3>
                    <p className="text-gray-600 mt-1">
                        한 번에 풀 문제 수를 선택하세요
                    </p>
                </div>

                <div className="grid gap-2">
                    {countOptions.map((count) => (
                        <Button
                            key={count}
                            variant={currentCount === count ? "default" : "outline"}
                            onClick={() => {
                                onCountSelect(count);
                                onClose();
                            }}
                            className={`
                                group relative w-full flex items-center gap-4 p-4 h-auto
                                border-2 rounded-xl transition-all duration-200
                                ${currentCount === count 
                                    ? 'bg-indigo-500 hover:bg-indigo-600 text-white border-transparent' 
                                    : 'hover:bg-indigo-50 border-indigo-100 hover:border-indigo-200'}
                            `}
                        >
                            <div className={`
                                flex-shrink-0 p-2 rounded-lg transition-colors
                                ${currentCount === count 
                                    ? 'bg-indigo-400 text-white' 
                                    : 'bg-indigo-50 text-indigo-500 group-hover:bg-indigo-100'}
                            `}>
                                <Hash className="w-5 h-5" />
                            </div>
                            <div className="flex items-center gap-2">
                                <span className="text-lg font-semibold">{count}문제</span>
                                {currentCount === count && (
                                    <Check className="w-5 h-5 ml-1" />
                                )}
                            </div>
                        </Button>
                    ))}
                </div>
            </div>
        </CenterModal>
    );
};

CenterModal.displayName = 'CenterModal';
ProblemCountModal.displayName = 'ProblemCountModal';

export default ProblemCountModal;