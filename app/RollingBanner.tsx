"use client"

import { useState, useEffect, TouchEvent } from 'react';
import { ChevronLeft, ChevronRight, ExternalLink } from 'lucide-react';

interface BannerItem {
  type: 'content' | 'ad';
  icon?: string;
  text?: string;
  image?: string;
  link?: string;
  backgroundColor?: string;
  textColor?: string;
}

interface RollingBannerProps {
  items: BannerItem[];
  autoPlayInterval?: number;
}

const RollingBanner = ({ 
  items = [], // 기본값 제공
  autoPlayInterval = 5000 
}: RollingBannerProps) => {
  const [currentIndex, setCurrentIndex] = useState(0);
  const [isPaused, setIsPaused] = useState(false);
  const [touchStart, setTouchStart] = useState<number | null>(null);
  const [touchEnd, setTouchEnd] = useState<number | null>(null);

  const minSwipeDistance = 50;

  const handlePrevious = () => {
    setCurrentIndex((prevIndex) => 
      prevIndex === 0 ? items.length - 1 : prevIndex - 1
    );
  };

  const handleNext = () => {
    setCurrentIndex((prevIndex) => 
      prevIndex === items.length - 1 ? 0 : prevIndex + 1
    );
  };

  const onTouchStart = (e: TouchEvent) => {
    setTouchEnd(null);
    setTouchStart(e.targetTouches[0].clientX);
  };

  const onTouchMove = (e: TouchEvent) => {
    setTouchEnd(e.targetTouches[0].clientX);
  };

  const onTouchEnd = () => {
    if (!touchStart || !touchEnd) return;
    
    const distance = touchStart - touchEnd;
    const isLeftSwipe = distance > minSwipeDistance;
    const isRightSwipe = distance < -minSwipeDistance;
    
    if (isLeftSwipe) {
      handleNext();
    }
    if (isRightSwipe) {
      handlePrevious();
    }
  };

  const handleItemClick = (item: BannerItem) => {
    if (item.type === 'content' && item.link) {
      window.open(item.link, '_blank', 'noopener,noreferrer');
    }
  };

  useEffect(() => {
    let interval: NodeJS.Timeout;
    
    if (!isPaused && items.length > 1) {
      interval = setInterval(() => {
        setCurrentIndex((prevIndex) => 
          prevIndex === items.length - 1 ? 0 : prevIndex + 1
        );
      }, autoPlayInterval);
    }

    return () => clearInterval(interval);
  }, [isPaused, items.length, autoPlayInterval]);

  // 빈 배열 체크
  if (!items || items.length === 0) {
    return null;
  }

  const currentItem = items[currentIndex];

  return (
    <div 
      className={`relative mt-4 rounded-xl shadow-sm border border-indigo-100 overflow-hidden transition-colors duration-300 group
        ${currentItem.type === 'content' ? currentItem.backgroundColor || 'bg-white/80' : 'bg-white'}`}
      onMouseEnter={() => setIsPaused(true)}
      onMouseLeave={() => setIsPaused(false)}
      onTouchStart={onTouchStart}
      onTouchMove={onTouchMove}
      onTouchEnd={onTouchEnd}
    >
      <div className="relative h-32 flex items-center">
        {items.length > 1 && (
          <>
            <button 
              onClick={handlePrevious}
              className="absolute left-2 p-1.5 rounded-full bg-white/80 text-indigo-600 
                       hover:bg-indigo-50 transition-all opacity-0 group-hover:opacity-100 
                       hidden md:flex items-center justify-center
                       shadow-sm hover:shadow-md"
              aria-label="Previous banner"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>

            <button 
              onClick={handleNext}
              className="absolute right-2 p-1.5 rounded-full bg-white/80 text-indigo-600 
                       hover:bg-indigo-50 transition-all opacity-0 group-hover:opacity-100
                       hidden md:flex items-center justify-center
                       shadow-sm hover:shadow-md"
              aria-label="Next banner"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </>
        )}
        
        <div 
          className={`flex-1 h-full flex items-center justify-center px-8
            ${currentItem.type === 'content' && currentItem.link ? 'cursor-pointer hover:opacity-80' : ''}`}
          onClick={() => handleItemClick(currentItem)}
          role={currentItem.type === 'content' && currentItem.link ? 'link' : 'presentation'}
        >
          {currentItem.type === 'content' ? (
            <div className={`flex items-center gap-3 ${currentItem.textColor || 'text-indigo-700'}`}>
              {currentItem.icon && (
                <span className="text-2xl flex items-center" role="img" aria-hidden="true">
                  {currentItem.icon}
                </span>
              )}
              <span className="text-base font-medium">
                {currentItem.text}
              </span>
              {currentItem.link && (
                <ExternalLink className="w-4 h-4 opacity-70" />
              )}
            </div>
          ) : (
            <span className="text-sm text-gray-500">Advertisement</span>
          )}
        </div>
      </div>
      
      {items.length > 1 && (
        <div className="absolute bottom-2 left-0 right-0 flex justify-center gap-1.5">
          {items.map((_, index) => (
            <div
              key={index}
              className={`h-1 rounded-full transition-all duration-300 ${
                index === currentIndex 
                  ? 'w-4 bg-indigo-500' 
                  : 'w-1 bg-indigo-200 cursor-pointer hover:bg-indigo-300'
              }`}
              role="button"
              onClick={() => setCurrentIndex(index)}
            />
          ))}
        </div>
      )}
    </div>
  );
};

export default RollingBanner;