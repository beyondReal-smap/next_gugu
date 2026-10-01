"use client";

import { useEffect } from 'react';
import { Capacitor } from '@capacitor/core';
import { enableMetaPixel } from '@/src/utils/metaPixel';
import { captureAttribution } from '@/src/utils/attribution';

/**
 * Meta 광고 측정용 Pixel (Base + PageView).
 * - 웹 브라우저에서만 로드. Capacitor 네이티브는 스킵.
 * - GA(AnalyticsLoader)와 달리 보호자 동의 게이트를 두지 않음:
 *   유료 광고 클릭 → 랜딩 PageView 측정이 목적이고, 랜딩 방문자는 대개 보호자임.
 * - 광고 유입 식별자(fbclid, utm_*)를 저장한다(src/utils/attribution.ts).
 */
export function MetaPixelLoader() {
  useEffect(() => {
    try {
      if (Capacitor.isNativePlatform()) return;
    } catch {
      // Capacitor 미초기화 환경은 웹으로 간주
    }
    // 광고 유입(fbclid/utm_*)은 첫 화면에서 저장해 둔다 → 이후 어느 페이지의 설치 CTA에서든 귀속 가능.
    captureAttribution();
    enableMetaPixel();
  }, []);

  return null;
}
