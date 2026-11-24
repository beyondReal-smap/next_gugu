import type { Metadata, Viewport } from "next";
import localFont from "next/font/local";
import "./globals.css";
import ClientLayout from './ClientLayout';

const geistSans = localFont({
  src: "./fonts/GeistVF.woff",
  variable: "--font-geist-sans",
  weight: "100 900",
});

const geistMono = localFont({
  src: "./fonts/GeistMonoVF.woff",
  variable: "--font-geist-mono",
  weight: "100 900",
});

const suite = localFont({
  src: [
    {
      path: './fonts/SUITE-Light.woff2',
      weight: '300',
    },
    {
      path: './fonts/SUITE-Regular.woff2',
      weight: '400',
    },
    {
      path: './fonts/SUITE-Medium.woff2',
      weight: '500',
    },
    {
      path: './fonts/SUITE-SemiBold.woff2',
      weight: '600',
    },
    {
      path: './fonts/SUITE-Bold.woff2',
      weight: '700',
    },
    {
      path: './fonts/SUITE-ExtraBold.woff2',
      weight: '800',
    },
    {
      path: './fonts/SUITE-Heavy.woff2',
      weight: '900',
    }
  ],
  variable: '--font-suite'
});

// 정적 export라 앱 라우트는 클라이언트에서 그려진다 → 크롤러가 읽을 본문이 사실상 없다.
// 메타데이터·JSON-LD·noscript 링크로 최소한의 기계 판독 가능성을 확보하고,
// 본문 콘텐츠는 public/guide/*.html (scripts/build-ai-pages.mjs 생성물)이 담당한다.
const SITE_URL = 'https://gugu.smap.site';

export const metadata: Metadata = {
  metadataBase: new URL(SITE_URL),
  title: {
    default: "구구 어드벤처 — 매일 레벨업하는 구구단 학습",
    template: "%s — 구구 어드벤처",
  },
  description:
    "2단부터 9단까지 구구단 72개 곱셈식을 게임으로 익히는 학습 앱. 6가지 게임 모드, 레벨·스트릭, 오답 가중 출제로 약한 구구단만 집중 반복합니다.",
  applicationName: "구구 어드벤처",
  keywords: ["구구단", "구구단 게임", "구구단 앱", "곱셈구구", "구구단 외우는 법", "초등 2학년 수학", "구구단 연습"],
  authors: [{ name: "SMAP", url: "https://smap.site" }],
  creator: "SMAP",
  alternates: {
    canonical: '/',
    types: { 'application/rss+xml': '/rss.xml' },
  },
  openGraph: {
    type: 'website',
    siteName: '구구 어드벤처',
    locale: 'ko_KR',
    url: SITE_URL,
    title: '구구 어드벤처 — 매일 레벨업하는 구구단 학습',
    description: '2~9단 구구단 72개 곱셈식을 게임으로. 6가지 모드와 오답 가중 출제로 약한 단만 집중 반복.',
    images: [{ url: '/icons/icon-512x512.png', width: 512, height: 512, alt: '구구 어드벤처' }],
  },
  twitter: {
    card: 'summary',
    title: '구구 어드벤처 — 매일 레벨업하는 구구단 학습',
    description: '2~9단 구구단 72개 곱셈식을 게임으로 익히는 학습 앱.',
    images: ['/icons/icon-512x512.png'],
  },
  robots: {
    index: true,
    follow: true,
    googleBot: { index: true, follow: true, 'max-snippet': -1, 'max-image-preview': 'large' },
  },
  // 검색엔진 사이트 소유확인 — Search Console / 네이버 서치어드바이저에서 발급받은 코드를
  // .env.local에 넣으면 빌드 시 메타태그로 들어간다. 값이 없으면 태그 자체가 생략된다.
  verification: {
    google: process.env.GOOGLE_SITE_VERIFICATION,
    other: process.env.NAVER_SITE_VERIFICATION
      ? { 'naver-site-verification': process.env.NAVER_SITE_VERIFICATION }
      : {},
  },
  icons: {
    icon: '/icon-gugu.png',
    apple: '/icons/apple-touch-icon.png', // iOS 홈 화면 추가 시 아이콘
  },
};

// 검색·AI 크롤러용 구조화 데이터. 앱 화면은 JS로 그려지므로 여기서 실체를 설명한다.
const JSON_LD = {
  '@context': 'https://schema.org',
  '@graph': [
    {
      '@type': 'WebSite',
      '@id': `${SITE_URL}/#website`,
      url: SITE_URL,
      name: '구구 어드벤처',
      inLanguage: 'ko',
      description: '2단부터 9단까지 구구단을 게임으로 익히는 한국어 학습 앱',
    },
    {
      '@type': 'SoftwareApplication',
      '@id': `${SITE_URL}/#app`,
      name: '구구 어드벤처',
      alternateName: ['구구 어드벤처 앱', 'Gugu Adventure', '구구단 마스터'],
      url: SITE_URL,
      applicationCategory: 'EducationalApplication',
      operatingSystem: 'iOS, Android, Web',
      installUrl: ['https://apps.apple.com/kr/app/id6737556680', 'https://play.google.com/store/apps/details?id=site.smap.gugudan'],
      inLanguage: 'ko',
      description:
        '2단~9단 총 72개 곱셈식을 6가지 게임 모드로 반복 학습하는 앱. 틀린 식의 출제 가중치를 높여 약한 구구단만 집중적으로 보완합니다.',
      featureList: ['6가지 게임 모드', '오답 가중 출제', '레벨과 경험치', '연속 학습 스트릭', '단별 마스터리 별', '3D 어드벤처 모드'],
      offers: { '@type': 'Offer', price: '0', priceCurrency: 'KRW' },
      publisher: { '@type': 'Organization', name: 'SMAP', url: 'https://smap.site' },
    },
  ],
};

// 저시력·저학년 확대 허용. 게임 입력부는 touch-action: manipulation으로 더블탭 줌만 억제.
export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  maximumScale: 2,
  userScalable: true,
  viewportFit: 'cover',
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko">
      <head>
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: JSON.stringify(JSON_LD) }}
        />
      </head>
      <body className={`${geistSans.variable} ${geistMono.variable} ${suite.variable} font-suite antialiased`}>
        {/* JS를 실행하지 않는 크롤러(네이버 Yeti, GPTBot·ClaudeBot 등)가 보는 유일한 본문.
            AppShell은 loaded=false인 프리렌더 시점에 빈 div만 반환하므로 앱 화면은 정적 HTML에
            남지 않는다. 브라우저 사용자에게는 보이지 않아 앱 UX에는 영향이 없다.
            /learn·/profile은 noindex라 이 블록이 세 라우트에 공통으로 나가도 중복 문제가 없다. */}
        <noscript>
          <h1>구구 어드벤처 — 매일 레벨업하는 구구단 학습</h1>
          <p>
            구구 어드벤처는 2단부터 9단까지 총 72개 곱셈식을 게임으로 익히는 한국어 학습 앱입니다.
            iOS·Android 앱으로 제공되며, 설치 전에 웹 브라우저에서도 바로 해볼 수 있습니다.
            2~9단 전체 학습과 6가지 게임 모드를 무료로 이용할 수 있습니다.
          </p>
          <p>
            틀린 곱셈식은 출제 가중치가 올라가 더 자주 나오고, 직전에 나온 문제는 덜 나옵니다.
            그래서 이미 아는 단을 반복하는 대신 약한 구구단만 집중적으로 보완하게 됩니다.
            정답마다 경험치(XP)를 얻어 레벨이 오르고, 매일 학습하면 스트릭이 쌓이며,
            단마다 마스터리 별 3개를 모아 2~9단 전체 진도를 한눈에 확인합니다.
          </p>

          <h2>구구단 학습에서 알아 두면 좋은 것</h2>
          <ul>
            <li>곱셈은 순서를 바꿔도 답이 같으므로(교환법칙) 실제로 외울 식은 72개가 아니라 36개입니다.</li>
            <li>가장 어려운 단은 7단이고, 가장 많이 틀리는 식은 7 × 8 = 56입니다.</li>
            <li>권장 학습 순서는 단 번호 순서가 아니라 2단 → 5단 → 3단 → 4단 → 6단 → 9단 → 7단 → 8단입니다.</li>
            <li>검산 규칙: 2·4·6·8단은 답이 짝수, 3단은 자릿수 합이 3의 배수, 5단은 5나 0으로 끝나고, 9단은 자릿수 합이 항상 9입니다.</li>
          </ul>

          <h2>구구단 가이드</h2>
          <ul>
            <li><a href="/guide">구구단 완전 정복 가이드 — 2~9단 전체 표와 학습 순서</a></li>
            <li><a href="/guide/2-dan">2단</a> · <a href="/guide/3-dan">3단</a> · <a href="/guide/4-dan">4단</a> · <a href="/guide/5-dan">5단</a> · <a href="/guide/6-dan">6단</a> · <a href="/guide/7-dan">7단</a> · <a href="/guide/8-dan">8단</a> · <a href="/guide/9-dan">9단</a></li>
            <li><a href="/guide/modes">게임 모드 6종의 규칙</a></li>
            <li><a href="/guide/faq">구구단 학습 자주 묻는 질문</a></li>
          </ul>

          <h2>AI·개발자를 위한 데이터</h2>
          <ul>
            <li><a href="/llms.txt">llms.txt — AI 에이전트용 사이트 요약</a></li>
            <li><a href="/ai/dataset.json">AI-ready 데이터셋 (JSON)</a></li>
          </ul>

          <p><a href="/privacy">개인정보처리방침</a> · <a href="/terms">이용약관</a></p>
        </noscript>
        <ClientLayout>{children}</ClientLayout>
      </body>
    </html>
  );
}