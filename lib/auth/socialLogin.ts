import { Capacitor } from '@capacitor/core';
import { getSupabase } from '../supabase/client';

export type SocialProvider = 'apple' | 'google';

let nativeInitialized = false;

// 네이티브(iOS/Android): OS 로그인 다이얼로그 → idToken → Supabase 세션.
// 웹뷰 브라우저 리다이렉트를 쓰지 않아 WKWebView 쿠키/딥링크 문제를 회피한다.
async function nativeSignIn(provider: SocialProvider): Promise<void> {
  const { SocialLogin } = await import('@capgo/capacitor-social-login');

  if (!nativeInitialized) {
    await SocialLogin.initialize({
      google: {
        webClientId: process.env.NEXT_PUBLIC_GOOGLE_WEB_CLIENT_ID,
        iOSClientId: process.env.NEXT_PUBLIC_GOOGLE_IOS_CLIENT_ID,
      },
      apple: {},
    });
    nativeInitialized = true;
  }

  const res =
    provider === 'apple'
      ? await SocialLogin.login({ provider: 'apple', options: { scopes: ['email', 'name'] } })
      : await SocialLogin.login({ provider: 'google', options: { scopes: ['email', 'profile'] } });

  const idToken = (res.result as { idToken?: string | null }).idToken;
  if (!idToken) {
    throw new Error(`${provider} 로그인에서 idToken을 받지 못했습니다`);
  }

  const { error } = await getSupabase().auth.signInWithIdToken({ provider, token: idToken });
  if (error) throw error;
}

// 웹 브라우저: Supabase OAuth(PKCE) 리다이렉트 → /auth/callback 에서 세션 교환
async function webSignIn(provider: SocialProvider): Promise<void> {
  const { error } = await getSupabase().auth.signInWithOAuth({
    provider,
    options: { redirectTo: `${window.location.origin}/auth/callback/` },
  });
  if (error) throw error;
}

export async function signInWithProvider(provider: SocialProvider): Promise<void> {
  if (Capacitor.isNativePlatform()) {
    await nativeSignIn(provider);
  } else {
    await webSignIn(provider);
  }
}
