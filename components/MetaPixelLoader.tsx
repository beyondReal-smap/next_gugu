"use client";

import { useEffect } from 'react';
import { Capacitor } from '@capacitor/core';
import { enableMetaPixel } from '@/src/utils/metaPixel';

/**
 * Meta 광고 측정용 Pixel (Base + PageView).
 * - 웹 브라우저에서만 로드. Capacitor 네이티브는 스킵.
 * - GA(AnalyticsLoader)와 달리 보호자 동의 게이트를 두지 않음:
 *   유료 광고 클릭 → 랜딩 PageView 측정이 목적이고, 랜딩 방문자는 대개 보호자임.
 * - play_start / Purchase 등 커스텀 이벤트는 이 PR 범위 밖.
 */
export function MetaPixelLoader() {
  useEffect(() => {
    try {
      if (Capacitor.isNativePlatform()) return;
    } catch {
      // Capacitor 미초기화 환경은 웹으로 간주
    }
    enableMetaPixel();
  }, []);

  return null;
}
