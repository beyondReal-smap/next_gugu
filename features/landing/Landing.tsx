"use client";
import React from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { isNativeShell } from '@/lib/native/platform';
import { Globe2, Map as MapIcon, Gamepad2, Target, TrendingUp, ShieldCheck } from 'lucide-react';
import { APP_NAME, StorePlatform, detectStorePlatform } from './stores';
import { StoreButtons } from './StoreButtons';
import { WEB_TRIAL_DAILY_LIMIT } from '@/lib/webTrial';

// 네이티브 앱(Capacitor 번들 또는 StoreKit 브릿지를 가진 iOS 셸)도 dist/index.html(=/)로 시작한다.
// 하이드레이션 전에 랜딩이 번쩍이지 않도록 파싱 시점에 html.native-app 을 붙여 숨기고(globals.css),
// 하이드레이션 후 게임(/play/)으로 클라이언트 라우팅한다. 전체 새로고침(location)은 Android 로컬
// 서버가 확장자 없는 경로에 루트 index.html을 돌려주므로 쓰지 않는다.
const NATIVE_FLAG_SCRIPT =
  "(function(w){try{var c=w.Capacitor,k=w.webkit&&w.webkit.messageHandlers;" +
  "if((c&&c.isNativePlatform&&c.isNativePlatform())||(k&&k.storeKit))" +
  "document.documentElement.classList.add('native-app')}catch(e){}})(window);";

const FEATURES = [
  { icon: Globe2, title: '3D 월드 모험', desc: '마을을 돌아다니며 주민과 구구단 대결을 펼쳐요.' },
  { icon: MapIcon, title: '8개 지역 정복', desc: '2단부터 9단까지, 보스를 이기면 다음 지역이 열려요.' },
  { icon: Gamepad2, title: '6가지 게임 모드', desc: '60초 챌린지, 서바이벌, 빈칸 추리, OX 퀴즈까지.' },
  { icon: Target, title: '틀린 문제 집중 반복', desc: '약한 곱셈식은 더 자주, 아는 식은 덜 나와요.' },
  { icon: TrendingUp, title: '성장이 보이는 기록', desc: '레벨·별점·업적 17종과 연속 학습 스트릭.' },
  { icon: ShieldCheck, title: '전 연령 안심 이용', desc: '4세 이상 등급, 가입 없이 바로 시작할 수 있어요.' },
];

const SHOTS = [
  { src: '/landing/01-home.webp', alt: '매일 1분, 게임 한 판 — 홈 화면' },
  { src: '/landing/02-world3d.webp', alt: '구구단이 모험이 된다 — 3D 월드' },
  { src: '/landing/03-regions.webp', alt: '8개 지역을 정복해요 — 지역 목록' },
  { src: '/landing/04-session.webp', alt: '풀고, 바로 피드백 — 문제 풀이 화면' },
  { src: '/landing/05-modes.webp', alt: '6가지 게임 모드 — 모드 선택 화면' },
  { src: '/landing/06-profile.webp', alt: '성장이 눈에 보여요 — 프로필 화면' },
];

function PhoneFrame({ src, alt, className = '' }: { src: string; alt: string; className?: string }) {
  return (
    <div className={`overflow-hidden rounded-[2rem] bg-white shadow-2xl shadow-indigo-950/40 ring-4 ring-white/20 ${className}`}>
      {/* eslint-disable-next-line @next/next/no-img-element -- 정적 export(images.unoptimized) */}
      <img src={src} alt={alt} width={600} height={1304} className="block h-auto w-full" />
    </div>
  );
}

export function Landing() {
  const router = useRouter();
  const [platform, setPlatform] = React.useState<StorePlatform>('other');

  React.useEffect(() => {
    if (isNativeShell()) {
      router.replace('/play/');
      return;
    }
    setPlatform(detectStorePlatform());
  }, [router]);

  return (
    <div className="landing-root app-scroll break-keep bg-white text-slate-900">
      <script dangerouslySetInnerHTML={{ __html: NATIVE_FLAG_SCRIPT }} />

      {/* ── 히어로 ─────────────────────────────────────────── */}
      <section className="relative isolate overflow-hidden bg-gradient-to-br from-indigo-600 via-indigo-600 to-violet-700 text-white">
        <div aria-hidden="true" className="pointer-events-none absolute inset-0 -z-10 select-none font-extrabold text-white/[0.07]">
          <span className="absolute left-[6%] top-24 text-8xl">×</span>
          <span className="absolute right-[8%] top-10 text-9xl">9</span>
          <span className="absolute bottom-16 left-[42%] text-8xl">=</span>
          <span className="absolute bottom-8 right-[30%] text-7xl">7</span>
        </div>

        <header className="mx-auto flex max-w-6xl items-center justify-between px-5 pt-[max(1.25rem,env(safe-area-inset-top))] sm:px-8">
          <div className="flex items-center gap-2.5">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src="/landing/app-icon.webp" alt="" width={40} height={40} className="h-10 w-10 rounded-xl ring-1 ring-white/30" />
            <span className="text-lg font-extrabold tracking-tight">{APP_NAME}</span>
          </div>
          <Link href="/play/" className="rounded-full border border-white/30 px-4 py-2 text-sm font-bold text-white/90 transition-colors hover:bg-white/10">
            웹에서 해보기
          </Link>
        </header>

        <div className="mx-auto grid max-w-6xl items-center gap-12 px-5 pb-16 pt-12 sm:px-8 lg:grid-cols-[1.1fr_1fr] lg:pb-24 lg:pt-16">
          <div>
            <span className="inline-flex items-center gap-2 rounded-full bg-amber-300 px-3.5 py-1.5 text-xs font-extrabold text-indigo-950">
              무료 · 4세 이상 · iOS / Android
            </span>
            <h1 className="mt-5 text-4xl font-extrabold leading-[1.15] tracking-tight sm:text-5xl lg:text-6xl">
              구구단이<br />
              <span className="text-amber-300">모험</span>이 된다
            </h1>
            <p className="mt-5 max-w-xl text-base leading-relaxed text-indigo-100 sm:text-lg">
              3D 월드를 탐험하며 주민과 구구단 대결! 2단부터 9단까지 8개 지역을 정복하다 보면
              72개 곱셈식이 저절로 손에 익어요. 매일 1분, 게임 한 판이면 충분해요.
            </p>
            <div className="mt-8">
              <StoreButtons platform={platform} />
            </div>
            <p className="mt-4 text-sm text-indigo-200">
              설치 전에 먼저 보고 싶다면{' '}
              <Link href="/play/" className="font-bold text-white underline underline-offset-4">웹에서 하루 {WEB_TRIAL_DAILY_LIMIT}판 무료 체험</Link>
            </p>
          </div>

          <div className="relative mx-auto flex w-full max-w-md justify-center">
            <PhoneFrame src={SHOTS[1].src} alt={SHOTS[1].alt} className="relative z-10 w-[58%]" />
            <PhoneFrame src={SHOTS[0].src} alt={SHOTS[0].alt} className="absolute left-0 top-10 w-[46%] -rotate-6 opacity-95" />
            <PhoneFrame src={SHOTS[2].src} alt={SHOTS[2].alt} className="absolute right-0 top-10 w-[46%] rotate-6 opacity-95" />
          </div>
        </div>
      </section>

      {/* ── 특징 ───────────────────────────────────────────── */}
      <section aria-labelledby="features-heading" className="mx-auto max-w-6xl px-5 py-16 sm:px-8 lg:py-24">
        <p className="text-sm font-extrabold text-indigo-600">왜 구구 어드벤처인가요?</p>
        <h2 id="features-heading" className="mt-2 text-3xl font-extrabold tracking-tight sm:text-4xl">
          외우지 말고, 놀면서 익혀요
        </h2>
        <div className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {FEATURES.map(({ icon: Icon, title, desc }) => (
            <div key={title} className="rounded-3xl border border-slate-200 bg-slate-50 p-6">
              <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-indigo-600 text-white">
                <Icon aria-hidden="true" className="h-6 w-6" />
              </span>
              <h3 className="mt-4 text-lg font-extrabold">{title}</h3>
              <p className="mt-1.5 text-sm leading-relaxed text-slate-600">{desc}</p>
            </div>
          ))}
        </div>
      </section>

      {/* ── 스크린샷 갤러리 ───────────────────────────────────── */}
      <section aria-labelledby="shots-heading" className="bg-slate-100 py-16 lg:py-24">
        <div className="mx-auto max-w-6xl px-5 sm:px-8">
          <h2 id="shots-heading" className="text-3xl font-extrabold tracking-tight sm:text-4xl">앱 미리보기</h2>
          <p className="mt-2 text-slate-600">실제 앱 화면이에요.</p>
        </div>
        <ul className="scrollbar-hide mx-auto mt-8 flex max-w-6xl snap-x snap-mandatory gap-4 overflow-x-auto px-5 pb-4 sm:px-8">
          {SHOTS.map((s) => (
            <li key={s.src} className="w-[62%] shrink-0 snap-start sm:w-[32%] lg:w-[23%]">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={s.src} alt={s.alt} width={600} height={1304} loading="lazy" className="block h-auto w-full rounded-3xl shadow-lg shadow-slate-900/10" />
            </li>
          ))}
        </ul>
      </section>

      {/* ── 마지막 CTA ────────────────────────────────────────── */}
      <section className="bg-gradient-to-br from-indigo-600 to-violet-700 text-white">
        <div className="mx-auto flex max-w-6xl flex-col items-start gap-6 px-5 py-16 sm:px-8 lg:flex-row lg:items-center lg:justify-between lg:py-20">
          <div>
            <h2 className="text-3xl font-extrabold tracking-tight sm:text-4xl">오늘부터 모험을 시작해요</h2>
            <p className="mt-2 text-indigo-100">무료로 설치하고 2단부터 9단까지 정복해 보세요.</p>
          </div>
          <StoreButtons platform={platform} />
        </div>
      </section>

      {/* ── 푸터 ─────────────────────────────────────────────── */}
      <footer className="bg-slate-950 text-slate-400">
        <div className="mx-auto flex max-w-6xl flex-col gap-4 px-5 pb-[max(2.5rem,env(safe-area-inset-bottom))] pt-10 text-sm sm:px-8 md:flex-row md:items-center md:justify-between">
          <nav aria-label="사이트 링크" className="flex flex-wrap gap-x-5 gap-y-2">
            <a href="/guide" className="hover:text-white">구구단 가이드</a>
            <Link href="/play/" className="hover:text-white">웹 버전</Link>
            <a href="/privacy" className="hover:text-white">개인정보처리방침</a>
            <a href="/terms" className="hover:text-white">이용약관</a>
            <a href="mailto:admin@smap.site" className="hover:text-white">문의 admin@smap.site</a>
          </nav>
          <p>© SMAP · {APP_NAME}</p>
        </div>
      </footer>
    </div>
  );
}
