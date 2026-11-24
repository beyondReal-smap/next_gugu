"use client";
import React from 'react';
import { APP_STORE_URL, PLAY_STORE_URL, StorePlatform } from './stores';
import {
  buildStoreUrl,
  captureAttribution,
  newClickId,
  trackInstallClick,
} from '@/src/utils/attribution';

function AppleLogo() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" className="h-7 w-7 fill-current">
      <path d="M16.37 12.73c-.02-2.2 1.8-3.26 1.88-3.31-1.02-1.5-2.62-1.7-3.19-1.72-1.36-.14-2.65.8-3.34.8-.69 0-1.75-.78-2.88-.76-1.48.02-2.85.86-3.61 2.19-1.54 2.67-.39 6.63 1.11 8.8.73 1.06 1.6 2.25 2.75 2.21 1.1-.04 1.52-.71 2.85-.71 1.33 0 1.71.71 2.88.69 1.19-.02 1.94-1.08 2.67-2.14.84-1.23 1.19-2.42 1.21-2.48-.03-.01-2.31-.89-2.33-3.57ZM14.18 6.27c.61-.74 1.02-1.76.91-2.78-.88.04-1.94.59-2.57 1.32-.56.65-1.06 1.69-.93 2.69.98.08 1.98-.5 2.59-1.23Z" />
    </svg>
  );
}

function PlayLogo() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" className="h-6 w-6">
      <path fill="#00D7FE" d="M3.6 2.3c-.2.2-.3.6-.3 1v17.4c0 .4.1.8.3 1l9.6-9.7-9.6-9.7Z" />
      <path fill="#FFCE00" d="m16.4 15.2-3.2-3.2 3.2-3.2 3.7 2.1c1 .6 1 1.6 0 2.2l-3.7 2.1Z" />
      <path fill="#FF3A44" d="M16.4 15.2 13.2 12l-9.6 9.7c.4.4 1 .4 1.7 0l11.1-6.5Z" />
      <path fill="#00F076" d="M16.4 8.8 5.3 2.3c-.7-.4-1.3-.4-1.7 0l9.6 9.7 3.2-3.2Z" />
    </svg>
  );
}

function StoreButton({ store, placement }: { store: 'ios' | 'android'; placement: string }) {
  const ios = store === 'ios';
  const baseUrl = ios ? APP_STORE_URL : PLAY_STORE_URL;
  // 프리렌더/첫 렌더는 기본 URL. 마운트 후 저장된 유입 정보를 붙인 URL로 교체(우클릭·길게 누르기 대비).
  const [href, setHref] = React.useState(baseUrl);
  React.useEffect(() => {
    setHref(buildStoreUrl(store, baseUrl, captureAttribution(), { placement }));
  }, [store, baseUrl, placement]);

  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      data-store={store}
      data-placement={placement}
      onClick={(e) => {
        // 기본 이동(새 탭)을 막지 않는다. 클릭 ID를 포함한 최종 URL로 href만 갈아끼우고,
        // 이벤트 전송은 fbq(비차단) + sendBeacon 이라 이동과 경쟁하지 않는다.
        const clickId = newClickId();
        const finalUrl = buildStoreUrl(store, baseUrl, captureAttribution(), { placement, clickId });
        e.currentTarget.href = finalUrl;
        trackInstallClick({ store, placement, clickId, href: finalUrl });
      }}
      className="inline-flex min-h-14 min-w-[11.5rem] items-center gap-3 rounded-2xl bg-black px-5 py-2.5 text-white shadow-lg shadow-black/20 ring-1 ring-white/15 transition-transform hover:-translate-y-0.5 active:scale-[0.98]"
    >
      {ios ? <AppleLogo /> : <PlayLogo />}
      <span className="flex flex-col text-left leading-tight">
        <span className="text-[0.7rem] font-medium text-white/75">{ios ? 'App Store에서' : 'Google Play에서'}</span>
        <span className="text-lg font-bold">다운로드</span>
      </span>
    </a>
  );
}

// 방문자 기기에 맞는 스토어를 앞에 둔다. 판별 전(프리렌더)과 데스크톱은 iOS → Android 순.
export function StoreButtons({
  platform,
  placement = 'unknown',
  className = '',
}: {
  platform: StorePlatform;
  /** 버튼 위치 식별자(hero, bottom_cta, install_prompt_limit 등) — 이벤트/로그 파라미터로 전송 */
  placement?: string;
  className?: string;
}) {
  const order: Array<'ios' | 'android'> = platform === 'android' ? ['android', 'ios'] : ['ios', 'android'];
  return (
    <div className={`flex flex-wrap gap-3 ${className}`}>
      {order.map((s) => <StoreButton key={s} store={s} placement={placement} />)}
    </div>
  );
}
