"use client"

import { useState, useEffect } from 'react';
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
  items, 
  autoPlayInterval = 3000 
}: RollingBannerProps) => {
  const [currentIndex, setCurrentIndex] = useState(0);
  const [isPaused, setIsPaused] = useState(false);

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

  const handleItemClick = (item: BannerItem) => {
    if (item.type === 'content' && item.link) {
      window.open(item.link, '_blank', 'noopener,noreferrer');
    }
  };

  if (items.length === 0) return null;

  const currentItem = items[currentIndex];

  return (
    <div 
      className={`relative mt-4 rounded-xl shadow-sm border border-indigo-100 overflow-hidden transition-colors duration-300 
        ${currentItem.type === 'content' ? currentItem.backgroundColor || 'bg-white/80' : 'bg-white'}`}
      onMouseEnter={() => setIsPaused(true)}
      onMouseLeave={() => setIsPaused(false)}
    >
      {/* 메인 컨테이너에 h-16 추가하여 고정 높이 설정 */}
      <div className="relative h-16 flex items-center">
        {items.length > 1 && (
          <button 
            onClick={handlePrevious}
            className="absolute left-2 p-1 rounded-full bg-white/90 text-indigo-600 hover:bg-indigo-50 transition-colors z-10"
            aria-label="Previous banner"
          >
            <ChevronLeft className="w-4 h-4" />
          </button>
        )}
        
        {/* 중앙 정렬을 위한 컨테이너 */}
        <div 
          className={`flex-1 h-full flex items-center justify-center px-8
            ${currentItem.type === 'content' && currentItem.link ? 'cursor-pointer hover:opacity-80' : ''}`}
          onClick={() => handleItemClick(currentItem)}
          role={currentItem.type === 'content' && currentItem.link ? 'link' : 'presentation'}
        >
          {currentItem.type === 'content' ? (
            <div className={`flex items-center gap-2 ${currentItem.textColor || 'text-indigo-700'}`}>
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

        {items.length > 1 && (
          <button 
            onClick={handleNext}
            className="absolute right-2 p-1 rounded-full bg-white/90 text-indigo-600 hover:bg-indigo-50 transition-colors z-10"
            aria-label="Next banner"
          >
            <ChevronRight className="w-4 h-4" />
          </button>
        )}
      </div>
      
      {/* 인디케이터를 배너 하단에 배치 */}
      {items.length > 1 && (
        <div className="absolute bottom-0 left-0 right-0 flex justify-center gap-1 pb-1">
          {items.map((_, index) => (
            <div
              key={index}
              className={`h-1 rounded-full transition-all duration-300 ${
                index === currentIndex 
                  ? 'w-4 bg-indigo-500' 
                  : 'w-1 bg-indigo-200'
              }`}
              role="presentation"
            />
          ))}
        </div>
      )}
    </div>
  );
};

export default RollingBanner;