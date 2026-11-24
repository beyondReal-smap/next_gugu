"use client";
import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import type { User } from '@supabase/supabase-js';
import { getSupabase, isSupabaseConfigured } from '../supabase/client';
import { signInWithProvider, SocialProvider } from '../auth/socialLogin';

interface AuthContextValue {
  user: User | null;
  loading: boolean;
  available: boolean; // Supabase env 구성 여부 — 미구성 빌드에서는 로그인 UI 숨김
  signInEmail: (email: string, password: string) => Promise<void>;
  signUpEmail: (email: string, password: string) => Promise<{ needsConfirm: boolean }>;
  signInSocial: (provider: SocialProvider) => Promise<void>;
  signOut: () => Promise<void>;
  getAccessToken: () => Promise<string | null>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const available = isSupabaseConfigured();
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(available);

  useEffect(() => {
    if (!available) return;
    const supabase = getSupabase();

    supabase.auth.getSession().then(({ data }) => {
      setUser(data.session?.user ?? null);
      setLoading(false);
    });

    const { data: sub } = supabase.auth.onAuthStateChange((_event, session) => {
      setUser(session?.user ?? null);
      // 네이티브 셸이 IAP 검증 API 호출 시 Authorization을 붙일 수 있도록 토큰 노출
      // (window.__GUGU_AUTH_TOKEN__ — docs/auth-setup.md의 네이티브 연동 참고)
      (window as unknown as Record<string, unknown>).__GUGU_AUTH_TOKEN__ =
        session?.access_token ?? null;
    });
    return () => sub.subscription.unsubscribe();
  }, [available]);

  const signInEmail = useCallback(async (email: string, password: string) => {
    const { error } = await getSupabase().auth.signInWithPassword({ email, password });
    if (error) throw error;
  }, []);

  const signUpEmail = useCallback(async (email: string, password: string) => {
    const { data, error } = await getSupabase().auth.signUp({ email, password });
    if (error) throw error;
    // 이메일 확인이 켜진 프로젝트에서는 session이 null → 확인 메일 안내 필요
    return { needsConfirm: !data.session };
  }, []);

  const signInSocial = useCallback(async (provider: SocialProvider) => {
    await signInWithProvider(provider);
  }, []);

  const signOut = useCallback(async () => {
    const { error } = await getSupabase().auth.signOut();
    if (error) throw error;
  }, []);

  const getAccessToken = useCallback(async () => {
    if (!available) return null;
    const { data } = await getSupabase().auth.getSession();
    return data.session?.access_token ?? null;
  }, [available]);

  return (
    <AuthContext.Provider
      value={{ user, loading, available, signInEmail, signUpEmail, signInSocial, signOut, getAccessToken }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
