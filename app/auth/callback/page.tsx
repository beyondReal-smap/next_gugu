"use client";
import React, { useEffect, useState } from 'react';
import { getSupabase } from '@/lib/supabase/client';

// 웹 소셜 로그인(PKCE) 콜백 — ?code= 를 세션으로 교환 후 홈으로 복귀.
// 네이티브 앱은 signInWithIdToken을 쓰므로 이 페이지를 거치지 않는다.
export default function AuthCallbackPage() {
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      try {
        const code = new URLSearchParams(window.location.search).get('code');
        if (!code) throw new Error('인증 코드가 없습니다');
        const { error } = await getSupabase().auth.exchangeCodeForSession(code);
        if (error) throw error;
        window.location.replace('/play/');
      } catch (e) {
        setError(e instanceof Error ? e.message : '로그인 처리에 실패했습니다');
      }
    })();
  }, []);

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-3 px-6 text-center">
      {error ? (
        <>
          <div className="font-bold text-danger">로그인에 실패했습니다</div>
          <div className="text-sm text-text-muted">{error}</div>
          <a href="/play/" className="mt-2 text-sm font-bold text-accent">홈으로 돌아가기</a>
        </>
      ) : (
        <div className="font-bold text-text-muted">로그인 처리 중…</div>
      )}
    </div>
  );
}
