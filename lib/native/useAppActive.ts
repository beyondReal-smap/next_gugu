"use client";
import { useEffect, useRef, useState } from 'react';

// 웹: visibilitychange. 네이티브: Capacitor App.appStateChange.
export function useAppActive(): boolean {
  const [active, setActive] = useState(true);

  useEffect(() => {
    const onVis = () => {
      setActive(document.visibilityState !== 'hidden');
    };
    document.addEventListener('visibilitychange', onVis);

    let removeCap: (() => void) | undefined;
    void (async () => {
      try {
        const { Capacitor } = await import('@capacitor/core');
        if (!Capacitor.isNativePlatform()) return;
        const { App } = await import('@capacitor/app');
        const handle = await App.addListener('appStateChange', (s) => {
          setActive(s.isActive);
        });
        removeCap = () => { void handle.remove(); };
      } catch (e) {
        console.error('앱 생명주기 구독 실패:', e);
      }
    })();

    return () => {
      document.removeEventListener('visibilitychange', onVis);
      removeCap?.();
    };
  }, []);

  return active;
}

// 백그라운드 동안 흐른 시간을 startRef 들에 더해 타이머를 멈춘 것처럼 보이게 함
export function useShiftClocksOnResume(
  refs: Array<React.MutableRefObject<number>>,
  active: boolean
): void {
  const pauseAt = useRef<number | null>(null);
  useEffect(() => {
    if (!active) {
      pauseAt.current = typeof performance !== 'undefined' ? performance.now() : Date.now();
      return;
    }
    if (pauseAt.current == null) return;
    const now = typeof performance !== 'undefined' ? performance.now() : Date.now();
    const dt = now - pauseAt.current;
    pauseAt.current = null;
    for (const r of refs) r.current += dt;
  }, [active, refs]);
}
