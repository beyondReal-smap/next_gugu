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
  title: "구구단 마스터 — 매일 레벨업하는 구구단 학습",
  description: "레벨, 스트릭, 마스터리로 매일 성장하는 모던한 구구단 학습 게임.",
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