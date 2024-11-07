// RollingBanner.tsx
"use client"

import { useState, useEffect, TouchEvent } from 'react';
import { ChevronLeft, ChevronRight, ExternalLink } from 'lucide-react';
import { BannerItem, RollingBannerProps, SubscriptionProduct } from './types/banner';

declare global {
  interface Window {
    webkit?: {
      messageHandlers: {
        openLink: {
          postMessage: (url: string) => void;
        };
        subscribeProduct: {
          postMessage: (productId: string) => void;
        };
        loadAd: {
          postMessage: (adUnitId: string) => void;
        };
      };
    };
    Android?: {
      openLink: (url: string) => void;
      subscribeProduct: (productId: string) => void;
      loadAd: (adUnitId: string) => void;
    };
  }
}

const RollingBanner = ({ 
  items = [], 
  autoPlayInterval = 5000,
  onSubscribe 
}: RollingBannerProps) => {
  const [currentIndex, setCurrentIndex] = useState(0);
  const [isPaused, setIsPaused] = useState(false);
  const [touchStart, setTouchStart] = useState<number | null>(null);
  const [touchEnd, setTouchEnd] = useState<number | null>(null);
  const [adLoaded, setAdLoaded] = useState<{[key: string]: boolean}>({});

  const minSwipeDistance = 50;

  // AdMob 광고 로드 함수
  const loadAd = (adUnitId: string) => {
    if (window.webkit?.messageHandlers?.loadAd) {
      window.webkit.messageHandlers.loadAd.postMessage(adUnitId);
    } else if (window.Android?.loadAd) {
      window.Android.loadAd(adUnitId);
    } else {
      console.log('AdMob not available');
    }
  };

  // 구독 처리 함수
  const handleSubscription = (productId: string) => {
    if (window.webkit?.messageHandlers?.subscribeProduct) {
      window.webkit.messageHandlers.subscribeProduct.postMessage(productId);
    } else if (window.Android?.subscribeProduct) {
      window.Android.subscribeProduct(productId);
    } else {
      console.log('Subscription not available');
    }
  };

  const handleItemClick = (item: BannerItem, e: React.MouseEvent) => {
    e.preventDefault();
    
    switch (item.type) {
      case 'content':
      case 'image':
        if (item.link) {
          if (window.webkit?.messageHandlers?.openLink) {
            window.webkit.messageHandlers.openLink.postMessage(item.link);
          } else if (window.Android?.openLink) {
            window.Android.openLink(item.link);
          } else {
            try {
              window.open(item.link, '_system', 'location=yes');
            } catch (error) {
              window.open(item.link, '_blank', 'noopener,noreferrer');
            }
          }
        }
        break;
      
      case 'subscription':
        if (item.link) {
          handleSubscription(item.link);
        }
        break;
    }
  };

  // 광고 로드 효과
  useEffect(() => {
    items.forEach(item => {
      if (item.type === 'ad' && item.adUnitId && !adLoaded[item.adUnitId]) {
        loadAd(item.adUnitId);
        setAdLoaded(prev => ({ ...prev, [item.adUnitId as string]: true })); 
      }
    });
  }, [items]);

  // 자동 슬라이드 효과
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

  // 터치 이벤트 핸들러
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

  if (!items || items.length === 0) {
    return null;
  }

  const currentItem = items[currentIndex];

  const renderBannerContent = (item: BannerItem) => {
    switch (item.type) {
      case 'image':
        return (
          <div 
            className="w-full h-full bg-cover bg-center cursor-pointer"
            style={{ backgroundImage: `url(${item.imageUrl})` }}
          />
        );
      
      case 'content':
        return (
          <div className={`flex items-center gap-3 ${item.textColor || 'text-indigo-700'}`}>
            {item.icon && (
              <span className="text-2xl flex items-center" role="img" aria-hidden="true">
                {item.icon}
              </span>
            )}
            <span className="text-base font-suite font-medium">
              {item.text}
            </span>
            {item.link && (
              <ExternalLink className="w-4 h-4 opacity-70" />
            )}
          </div>
        );
      
      case 'ad':
        return (
          <div id={`admob-banner-${item.adUnitId}`} className="w-full h-full flex items-center justify-center">
            <span className="text-sm text-gray-500">Advertisement</span>
          </div>
        );
      
      case 'subscription':
        return (
          <div className="flex flex-col items-center justify-center p-4">
            <h3 className="text-lg font-suite font-bold mb-2">{item.text}</h3>
            {item.description && (
              <p className="text-sm text-gray-600 mb-4">{item.description}</p>
            )}
            <button
              onClick={() => item.link && handleSubscription(item.link)}
              className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700"
            >
              구독하기
            </button>
          </div>
        );
    }
  };

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
                       shadow-sm hover:shadow-md z-10"
              aria-label="Previous banner"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>

            <button 
              onClick={handleNext}
              className="absolute right-2 p-1.5 rounded-full bg-white/80 text-indigo-600 
                       hover:bg-indigo-50 transition-all opacity-0 group-hover:opacity-100
                       hidden md:flex items-center justify-center
                       shadow-sm hover:shadow-md z-10"
              aria-label="Next banner"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </>
        )}
        
        <div 
          className="flex-1 h-full flex items-center justify-center px-8"
          onClick={(e) => handleItemClick(currentItem, e)}
          role={currentItem.link ? 'link' : 'presentation'}
        >
          {renderBannerContent(currentItem)}
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