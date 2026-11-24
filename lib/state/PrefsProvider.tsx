"use client";
import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import { FontScale, GraphicsQuality, UserRole } from '../types';

const STORAGE_KEY = 'gugu.prefs.v1';

export const FONT_SCALES: FontScale[] = [1, 1.15, 1.3];

interface Prefs {
  version: 1;
  role: UserRole;
  analyticsConsent: boolean;
  ttsEnabled: boolean;
  fontScale: FontScale;
  guardianPin: string | null;
  reminderEnabled: boolean;
  reminderHour: number;
  graphicsQuality: GraphicsQuality;
}

const DEFAULT_PREFS: Prefs = {
  version: 1,
  role: 'child',
  analyticsConsent: false,
  ttsEnabled: false,
  fontScale: 1,
  guardianPin: null,
  reminderEnabled: false,
  reminderHour: 19,
  graphicsQuality: 'auto',
};

function isUserRole(v: unknown): v is UserRole {
  return v === 'child' || v === 'guardian';
}
function isFontScale(v: unknown): v is FontScale {
  return v === 1 || v === 1.15 || v === 1.3;
}
function isPin(v: unknown): v is string {
  return typeof v === 'string' && /^\d{4}$/.test(v);
}
function isHour(v: unknown): v is number {
  return typeof v === 'number' && Number.isInteger(v) && v >= 0 && v <= 23;
}
function isGraphics(v: unknown): v is GraphicsQuality {
  return v === 'auto' || v === 'smooth' || v === 'battery';
}

function parsePrefs(raw: string): Prefs {
  const parsed = JSON.parse(raw) as Partial<Prefs>;
  if (parsed == null || typeof parsed !== 'object') {
    throw new Error('prefs 형식이 객체가 아닙니다');
  }
  return {
    version: 1,
    role: isUserRole(parsed.role) ? parsed.role : DEFAULT_PREFS.role,
    analyticsConsent: typeof parsed.analyticsConsent === 'boolean'
      ? parsed.analyticsConsent
      : DEFAULT_PREFS.analyticsConsent,
    ttsEnabled: typeof parsed.ttsEnabled === 'boolean'
      ? parsed.ttsEnabled
      : DEFAULT_PREFS.ttsEnabled,
    fontScale: isFontScale(parsed.fontScale) ? parsed.fontScale : DEFAULT_PREFS.fontScale,
    guardianPin: isPin(parsed.guardianPin) ? parsed.guardianPin : null,
    reminderEnabled: typeof parsed.reminderEnabled === 'boolean'
      ? parsed.reminderEnabled
      : DEFAULT_PREFS.reminderEnabled,
    reminderHour: isHour(parsed.reminderHour) ? parsed.reminderHour : DEFAULT_PREFS.reminderHour,
    graphicsQuality: isGraphics(parsed.graphicsQuality) ? parsed.graphicsQuality : DEFAULT_PREFS.graphicsQuality,
  };
}

function applyFontScale(scale: FontScale) {
  document.documentElement.style.setProperty('--font-scale', String(scale));
}

interface PrefsContextValue {
  loaded: boolean;
  role: UserRole;
  analyticsConsent: boolean;
  ttsEnabled: boolean;
  fontScale: FontScale;
  setRole: (role: UserRole) => void;
  setAnalyticsConsent: (v: boolean) => void;
  setTtsEnabled: (v: boolean) => void;
  setFontScale: (v: FontScale) => void;
  guardianPin: string | null;
  setGuardianPin: (pin: string) => void;
  reminderEnabled: boolean;
  reminderHour: number;
  setReminderEnabled: (v: boolean) => void;
  setReminderHour: (h: number) => void;
  graphicsQuality: GraphicsQuality;
  setGraphicsQuality: (q: GraphicsQuality) => void;
}

const PrefsContext = createContext<PrefsContextValue | null>(null);

export function PrefsProvider({ children }: { children: React.ReactNode }) {
  const [prefs, setPrefs] = useState<Prefs>(DEFAULT_PREFS);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    let next = DEFAULT_PREFS;
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) next = parsePrefs(raw);
    } catch (e) {
      console.error('설정 로드 실패:', e);
    }
    applyFontScale(next.fontScale);
    setPrefs(next);
    setLoaded(true);
  }, []);

  useEffect(() => {
    if (!loaded) return;
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(prefs));
    } catch (e) {
      console.error('설정 저장 실패:', e);
    }
  }, [prefs, loaded]);

  const setRole = useCallback((role: UserRole) => {
    setPrefs((s) => {
      // 아이로 바꾸면 통계 동의를 해제 — 아동 경로에서 GA가 남지 않게
      if (role === 'child') return { ...s, role, analyticsConsent: false };
      return { ...s, role };
    });
  }, []);
  const setAnalyticsConsent = useCallback((v: boolean) => {
    setPrefs((s) => ({ ...s, analyticsConsent: v }));
  }, []);
  const setTtsEnabled = useCallback((v: boolean) => {
    setPrefs((s) => ({ ...s, ttsEnabled: v }));
  }, []);
  const setFontScale = useCallback((v: FontScale) => {
    if (!isFontScale(v)) {
      throw new Error(`지원하지 않는 글자 크기: ${v}`);
    }
    applyFontScale(v);
    setPrefs((s) => ({ ...s, fontScale: v }));
  }, []);
  const setGuardianPin = useCallback((pin: string) => {
    if (!isPin(pin)) throw new Error('PIN은 숫자 4자리여야 해요');
    setPrefs((s) => ({ ...s, guardianPin: pin }));
  }, []);
  const setReminderEnabled = useCallback((v: boolean) => {
    setPrefs((s) => ({ ...s, reminderEnabled: v }));
  }, []);
  const setReminderHour = useCallback((h: number) => {
    if (!isHour(h)) throw new Error(`알림 시각이 올바르지 않아요: ${h}`);
    setPrefs((s) => ({ ...s, reminderHour: h }));
  }, []);
  const setGraphicsQuality = useCallback((q: GraphicsQuality) => {
    if (!isGraphics(q)) throw new Error(`지원하지 않는 그래픽 설정: ${q}`);
    setPrefs((s) => ({ ...s, graphicsQuality: q }));
  }, []);

  return (
    <PrefsContext.Provider
      value={{
        loaded,
        role: prefs.role,
        analyticsConsent: prefs.analyticsConsent,
        ttsEnabled: prefs.ttsEnabled,
        fontScale: prefs.fontScale,
        setRole,
        setAnalyticsConsent,
        setTtsEnabled,
        setFontScale,
        guardianPin: prefs.guardianPin,
        setGuardianPin,
        reminderEnabled: prefs.reminderEnabled,
        reminderHour: prefs.reminderHour,
        setReminderEnabled,
        setReminderHour,
        graphicsQuality: prefs.graphicsQuality,
        setGraphicsQuality,
      }}
    >
      {children}
    </PrefsContext.Provider>
  );
}

export function usePrefs(): PrefsContextValue {
  const ctx = useContext(PrefsContext);
  if (!ctx) throw new Error('usePrefs must be used within PrefsProvider');
  return ctx;
}
