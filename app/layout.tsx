import type { Metadata, Viewport } from "next";
import localFont from "next/font/local";
import "./globals.css";
import ClientLayout from './ClientLayout';
import Script from 'next/script'
import { GA_TRACKING_ID } from '../src/utils/gtag'

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

export const metadata: Metadata = {
  title: "구구 어드벤처 — 매일 1분, 게임으로 배우는 구구단",
  description: "3D 월드를 탐험하며 구구단을 정복! 레벨, 스트릭, 마스터리로 매일 성장하는 구구단 학습 게임.",
  icons: {
    icon: '/icons/icon-192x192.png',
    apple: '/icons/apple-touch-icon.png', // iOS 홈 화면 추가 시 아이콘
  },
};

// 모바일 앱형 UX: 핀치/더블탭 확대 차단 (게임 조작 중 오확대 방지)
export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  maximumScale: 1,
  userScalable: false,
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
        {/* 다크 기본 테마 — 하이드레이션 전에 적용해 라이트 화면 번쩍임 방지 */}
        <Script
          id="theme-init"
          strategy="beforeInteractive"
          dangerouslySetInnerHTML={{
            __html: `try{var t=localStorage.getItem('gugu.theme');if(t!=='light')document.documentElement.classList.add('dark')}catch(e){}`,
          }}
        />
        {/* Google Analytics */}
        <Script
          strategy="afterInteractive"
          src={`https://www.googletagmanager.com/gtag/js?id=${GA_TRACKING_ID}`}
        />
        <Script
          id="google-analytics"
          strategy="afterInteractive"
          dangerouslySetInnerHTML={{
            __html: `
              window.dataLayer = window.dataLayer || [];
              function gtag(){dataLayer.push(arguments);}
              gtag('js', new Date());
              gtag('config', '${GA_TRACKING_ID}');
            `,
          }}
        />
      </head>
      <body className={`${geistSans.variable} ${geistMono.variable} ${suite.variable} font-suite antialiased`}>
        <ClientLayout>{children}</ClientLayout>
      </body>
    </html>
  );
}