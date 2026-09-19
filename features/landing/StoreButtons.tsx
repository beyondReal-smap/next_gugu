"use client";
import React from 'react';
import { APP_STORE_URL, PLAY_STORE_URL, StorePlatform } from './stores';
import { trackMetaCustom } from '@/src/utils/metaPixel';

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

function StoreButton({ store }: { store: 'ios' | 'android' }) {
  const ios = store === 'ios';
  return (
    <a
      href={ios ? APP_STORE_URL : PLAY_STORE_URL}
      target="_blank"
      rel="noopener noreferrer"
      onClick={() => {
        trackMetaCustom('app_store_click', {
          store: ios ? 'ios' : 'android',
          content_name: ios ? 'app_store' : 'play_store',
        });
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
export function StoreButtons({ platform, className = '' }: { platform: StorePlatform; className?: string }) {
  const order: Array<'ios' | 'android'> = platform === 'android' ? ['android', 'ios'] : ['ios', 'android'];
  return (
    <div className={`flex flex-wrap gap-3 ${className}`}>
      {order.map((s) => <StoreButton key={s} store={s} />)}
    </div>
  );
}
