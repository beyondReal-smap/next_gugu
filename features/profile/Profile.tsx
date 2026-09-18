"use client";
import React, { useState, useEffect } from 'react';
import * as Icons from 'lucide-react';
import { Sun, Moon, Volume2, VolumeX, RotateCcw, Type, Volume1, Shield, Vibrate, Sparkles } from 'lucide-react';
import { FontScale, GameMode, GraphicsQuality, UserRole } from '@/lib/types';
import { MODES } from '@/lib/modes';
import { MODE_ICONS, MODE_TINT } from '@/components/modeIcons';
import { useGame } from '@/lib/state/GameProvider';
import { useAdventure } from '@/lib/state/AdventureProvider';
import { useTheme } from '@/lib/state/ThemeProvider';
import { usePrefs } from '@/lib/state/PrefsProvider';
import { Sparkline } from '@/components/ui/Sparkline';
import { Button } from '@/components/ui/Button';
import { Segmented } from '@/components/ui/Segmented';
import { levelTitle } from '@/lib/level';
import { ACHIEVEMENTS } from '@/lib/achievements';
import { totalStats } from '@/lib/adventure/progress';
import { REGIONS, bossIdFor } from '@/lib/adventure/world';

import { isSoundEnabled, setSoundEnabled } from '@/lib/sound';
import { isHapticEnabled, setHapticEnabled } from '@/lib/native/haptics';
import { AccountSection } from '@/features/auth/AccountSection';
import { ParentReport } from '@/features/profile/ParentReport';
import { Paywall } from '@/features/premium/Paywall';

const SCORED_MODES: GameMode[] = ['challenge', 'survival'];

export function Profile() {
  const { state, levelInfo, resetProgress, setDailyGoal } = useGame();
  const { resetAdventure, progress } = useAdventure();
  const { theme, toggleTheme } = useTheme();
  const {
    role, setRole,
    analyticsConsent, setAnalyticsConsent,
    ttsEnabled, setTtsEnabled,
    fontScale, setFontScale,
    graphicsQuality, setGraphicsQuality,
  } = usePrefs();
  const [sound, setSound] = useState(true);
  const [haptic, setHaptic] = useState(true);
  const [confirmReset, setConfirmReset] = useState(false);
  const [paywallOpen, setPaywallOpen] = useState(false);

  useEffect(() => { setSound(isSoundEnabled()); setHaptic(isHapticEnabled()); }, []);
  const toggleSound = () => { const n = !sound; setSoundEnabled(n); setSound(n); };
  const toggleHaptic = () => { const n = !haptic; setHapticEnabled(n); setHaptic(n); };

  const unlocked = new Set(state.unlockedAchievements);
  const accuracyTotal = state.totalCorrect + state.totalWrong;
  const accuracy = accuracyTotal ? Math.round((state.totalCorrect / accuracyTotal) * 100) : 0;
  // 오답 가중치가 높은 순으로 집중 공략 문제 추출
  const weakProblems = Object.entries(state.wrongPool)
    .sort((x, y) => y[1] - x[1])
    .slice(0, 6);

  return (
    <div className="mx-auto max-w-md px-5 pt-6 animate-fade-in">
      {/* 레벨 헤더 */}
      <div className="mb-4 flex items-center gap-4 rounded-3xl border border-border bg-surface p-5">
        <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-accent text-2xl font-extrabold text-accent-fg num">
          {levelInfo.level}
        </div>
        <div className="flex-1">
          <div className="text-lg font-extrabold text-text">Lv.{levelInfo.level} · {levelTitle(levelInfo.level)}</div>
          <div className="mt-1.5 h-2 overflow-hidden rounded-full bg-surface-2">
            <div className="h-full rounded-full bg-accent" style={{ width: `${levelInfo.progress * 100}%` }} />
          </div>
          <div className="num mt-1 text-xs font-bold text-text-muted">{levelInfo.totalXp} XP</div>
        </div>
      </div>

      {/* 통계 */}
      <div className="mb-4 grid grid-cols-3 gap-3">
        <MiniStat label="정답" value={`${state.totalCorrect}`} />
        <MiniStat label="정확도" value={`${accuracy}%`} />
        <MiniStat label="최고 콤보" value={`${state.maxCombo}`} />
      </div>

      {/* 모드 최고 기록 */}
      <div className="mb-2 text-sm font-bold text-text-muted">최고 기록</div>
      <div className="mb-4 grid grid-cols-2 gap-3">
        {SCORED_MODES.map((id) => {
          const m = MODES[id];
          const Icon = MODE_ICONS[id];
          const tint = MODE_TINT[id];
          const best = state.bestScores[id] ?? 0;
          return (
            <div key={id} className="flex items-center gap-3 rounded-2xl border border-border bg-surface px-4 py-3">
              <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${tint.bg} ${tint.text}`}>
                <Icon className="h-5 w-5" strokeWidth={2.4} />
              </span>
              <div>
                <div className="text-xs font-bold text-text-muted">{m.name}</div>
                <div className="num text-lg font-extrabold text-text">{best > 0 ? `${best}점` : '—'}</div>
              </div>
            </div>
          );
        })}
      </div>

      {/* 정확도 추이 */}
      {state.recentAccuracy.length >= 2 && (
        <div className="mb-4 rounded-2xl border border-border bg-surface p-5">
          <div className="mb-2 text-sm font-bold text-text">최근 정확도 추이</div>
          <div className="text-accent"><Sparkline data={state.recentAccuracy} width={300} height={48} className="text-accent" /></div>
        </div>
      )}

      {/* 집중 공략 문제 — 오답 가중치가 남아 있는 동안만 */}
      {weakProblems.length > 0 && (
        <>
          <div className="mb-2 text-sm font-bold text-text-muted">집중 공략 문제</div>
          <div className="mb-4 flex flex-wrap gap-2">
            {weakProblems.map(([key]) => (
              <span key={key} className="num rounded-xl border border-danger/20 bg-danger/10 px-3 py-1.5 text-sm font-extrabold text-danger">
                {key.replace('x', ' × ')}
              </span>
            ))}
          </div>
        </>
      )}

      {/* 업적 */}
      <div className="mb-2 text-sm font-bold text-text-muted">업적 ({unlocked.size}/{ACHIEVEMENTS.length})</div>
      <div className="mb-4 grid grid-cols-4 gap-2.5">
        {ACHIEVEMENTS.map((a) => {
          const on = unlocked.has(a.id);
          const Icon = (Icons as unknown as Record<string, Icons.LucideIcon>)[a.icon] ?? Icons.Award;
          return (
            <div key={a.id} title={`${a.name} — ${a.description}`}
              className={`flex aspect-square flex-col items-center justify-center gap-1 rounded-2xl border text-center
                ${on ? 'border-accent/30 bg-accent/10 text-accent' : 'border-border bg-surface text-text-muted opacity-50'}`}>
              <Icon className="h-5 w-5" strokeWidth={2.2} />
              <span className="px-1 text-[9px] font-bold leading-tight">{a.name}</span>
            </div>
          );
        })}
      </div>

      {(() => {
        const adv = totalStats(progress);
        const bosses = REGIONS.filter((r) => progress.defeatedNpcs.includes(bossIdFor(r.table))).length;
        return (
          <div className="mb-4 rounded-2xl border border-border bg-surface px-5 py-4">
            <div className="mb-1 text-sm font-bold text-text-muted">어드벤처</div>
            <div className="num text-lg font-extrabold text-text">
              격파 {adv.defeated}/{adv.total}
              <span className="ml-2 text-sm font-bold text-text-muted">보스 {bosses}</span>
            </div>
          </div>
        );
      })()}

      <ParentReport />

      {role === 'guardian' && (
        <div className="mb-4 overflow-hidden rounded-2xl border border-border bg-surface">
          <button
            type="button"
            onClick={() => setPaywallOpen(true)}
            className="flex w-full items-center gap-3 px-5 py-4 text-left transition-colors hover:bg-surface-2"
          >
            <span className="text-text-muted"><Sparkles className="h-5 w-5" /></span>
            <span className="flex-1">
              <span className="block font-bold text-text">보호자 프리미엄</span>
              <span className="block text-xs text-text-muted">친절한 힌트 · 오래 보는 리포트 · 결제 없음(아이)</span>
            </span>
            <span className="text-sm font-bold text-accent">보기</span>
          </button>
        </div>
      )}
      <Paywall open={paywallOpen} onClose={() => setPaywallOpen(false)} />

      {/* 계정 — 로그인/구매 복원/계정 삭제 */}
      <AccountSection />

      {/* 설정 */}
      <div className="mb-2 text-sm font-bold text-text-muted">설정</div>
      <div className="overflow-hidden rounded-2xl border border-border bg-surface">
        <Row icon={theme === 'dark' ? <Moon className="h-5 w-5" /> : <Sun className="h-5 w-5" />} label="다크 모드" onClick={toggleTheme} action={theme === 'dark' ? '켜짐' : '꺼짐'} />
        <div className="h-px bg-border" />
        <Row icon={sound ? <Volume2 className="h-5 w-5" /> : <VolumeX className="h-5 w-5" />} label="효과음" onClick={toggleSound} action={sound ? '켜짐' : '꺼짐'} />
        <div className="h-px bg-border" />
        <Row icon={<Vibrate className="h-5 w-5" />} label="햅틱" onClick={toggleHaptic} action={haptic ? '켜짐' : '꺼짐'} />
        <div className="h-px bg-border" />
        <Row icon={<Volume1 className="h-5 w-5" />} label="문제 읽어주기" onClick={() => setTtsEnabled(!ttsEnabled)} action={ttsEnabled ? '켜짐' : '꺼짐'} />
      </div>

      <div className="mt-3 mb-2 text-sm font-bold text-text-muted">큰 글자</div>
      <div className="mb-4 flex items-center gap-3">
        <Type className="h-5 w-5 shrink-0 text-text-muted" />
        <Segmented
          className="flex-1"
          value={String(fontScale)}
          onChange={(v) => setFontScale(Number(v) as FontScale)}
          options={[
            { value: '1', label: '기본' },
            { value: '1.15', label: '크게' },
            { value: '1.3', label: '더 크게' },
          ]}
        />
      </div>

      <div className="mb-2 text-sm font-bold text-text-muted">3D 움직임</div>
      <div className="mb-4 grid grid-cols-3 gap-2">
        {([
          ['smooth', '부드럽게'],
          ['auto', '자동'],
          ['battery', '배터리'],
        ] as [GraphicsQuality, string][]).map(([id, label]) => (
          <button
            key={id}
            type="button"
            aria-pressed={graphicsQuality === id}
            onClick={() => setGraphicsQuality(id)}
            className={`rounded-2xl border px-3 py-3 text-sm font-extrabold transition-colors
              ${graphicsQuality === id ? 'border-accent bg-accent/10 text-accent' : 'border-border bg-surface text-text-muted'}`}
          >
            {label}
          </button>
        ))}
      </div>

      <div className="mb-2 text-sm font-bold text-text-muted">오늘의 목표</div>
      <div className="mb-4 grid grid-cols-3 gap-2">
        {([10, 20, 30] as const).map((n) => (
          <button
            key={n}
            type="button"
            aria-pressed={state.dailyGoal === n}
            onClick={() => setDailyGoal(n)}
            className={`rounded-2xl border px-4 py-3 text-sm font-extrabold transition-colors
              ${state.dailyGoal === n ? 'border-accent bg-accent/10 text-accent' : 'border-border bg-surface text-text-muted'}`}
          >
            {n}개
          </button>
        ))}
      </div>

      <div className="mb-2 text-sm font-bold text-text-muted">누가 쓰나요?</div>
      <div className="mb-4 grid grid-cols-2 gap-2">
        {(['child', 'guardian'] as UserRole[]).map((id) => (
          <button
            key={id}
            type="button"
            aria-pressed={role === id}
            onClick={() => setRole(id)}
            className={`rounded-2xl border px-4 py-3 text-sm font-extrabold transition-colors
              ${role === id ? 'border-accent bg-accent/10 text-accent' : 'border-border bg-surface text-text-muted'}`}
          >
            {id === 'child' ? '아이' : '보호자'}
          </button>
        ))}
      </div>

      {role === 'guardian' && (
        <>
          <div className="mb-2 text-sm font-bold text-text-muted">보호자</div>
          <div className="mb-4 overflow-hidden rounded-2xl border border-border bg-surface">
            <Row
              icon={<Shield className="h-5 w-5" />}
              label="이용 통계 보내기"
              onClick={() => setAnalyticsConsent(!analyticsConsent)}
              action={analyticsConsent ? '동의' : '안 함'}
            />
            <p className="px-5 pb-3 text-xs text-text-muted">
              Google Analytics를 익명(IP 마스킹)으로 켭니다. 아이 역할에서는 전송하지 않아요.
            </p>
          </div>
        </>
      )}

      <div className="mt-5 pb-4">
        {!confirmReset ? (
          <Button variant="ghost" className="w-full text-danger" onClick={() => setConfirmReset(true)}>
            <RotateCcw className="h-4 w-4" /> 기록 초기화
          </Button>
        ) : (
          <div className="flex gap-2">
            <Button variant="surface" className="flex-1" onClick={() => setConfirmReset(false)}>취소</Button>
            <Button variant="danger" className="flex-1" onClick={() => { resetProgress(); resetAdventure(); setConfirmReset(false); }}>초기화 확인</Button>
          </div>
        )}
      </div>
    </div>
  );
}

function MiniStat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-2xl border border-border bg-surface px-3 py-3 text-center">
      <div className="num text-xl font-extrabold text-text">{value}</div>
      <div className="text-xs font-bold text-text-muted">{label}</div>
    </div>
  );
}
function Row({ icon, label, action, onClick }: { icon: React.ReactNode; label: string; action: string; onClick: () => void }) {
  return (
    <button onClick={onClick} className="flex w-full items-center gap-3 px-5 py-4 text-left transition-colors hover:bg-surface-2">
      <span className="text-text-muted">{icon}</span>
      <span className="flex-1 font-bold text-text">{label}</span>
      <span className="text-sm font-bold text-accent">{action}</span>
    </button>
  );
}
