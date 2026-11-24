"use client";
import { useEffect } from 'react';
import { usePrefs } from '@/lib/state/PrefsProvider';
import { disableAnalytics, enableAnalytics } from '@/src/utils/gtag';

// 보호자 + 동의일 때만 GA를 로드. 기본(아이/미동의)은 스크립트 자체를 넣지 않는다.
export function AnalyticsLoader() {
  const { loaded, role, analyticsConsent } = usePrefs();

  useEffect(() => {
    if (!loaded) return;
    if (role === 'guardian' && analyticsConsent) {
      enableAnalytics();
    } else {
      disableAnalytics();
    }
  }, [loaded, role, analyticsConsent]);

  return null;
}
