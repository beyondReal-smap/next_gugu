"use client"

import { useEffect, useRef } from 'react';

interface AdBannerProps {
  className?: string;
}

const AdBanner = ({ className = '' }: AdBannerProps) => {
  const adContainerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    // AdSense 스크립트가 로드되었는지 확인
    const isAdSenseLoaded = typeof window !== 'undefined' && (window as any).adsbygoogle;

    if (adContainerRef.current && isAdSenseLoaded) {
      try {
        ((window as any).adsbygoogle = (window as any).adsbygoogle || []).push({});
      } catch (error) {
        console.error('AdSense error:', error);
      }
    }
  }, []);

  return (
    <div ref={adContainerRef} className={`ad-container ${className}`}>
      <ins
        className="adsbygoogle"
        style={{ display: 'block' }}
        data-ad-client="ca-pub-XXXXXXXXXXXXXXXX" // 여기에 AdSense 퍼블리셔 ID를 넣으세요
        data-ad-slot="YYYYYYYY" // 여기에 광고 슬롯 ID를 넣으세요
        data-ad-format="auto"
        data-full-width-responsive="true"
      />
    </div>
  );
};

export default AdBanner;