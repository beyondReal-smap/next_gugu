"use client";
import React, { useState, useEffect } from 'react';
import * as Icons from 'lucide-react';
import { Sun, Moon, Volume2, VolumeX, RotateCcw, Sparkles, RefreshCcw, FileText, ShieldCheck } from 'lucide-react';
import { GameMode } from '@/lib/types';
import { MODES } from '@/lib/modes';
import { MODE_ICONS, MODE_TINT } from '@/components/modeIcons';
import { useGame } from '@/lib/state/GameProvider';
import { useTheme } from '@/lib/state/ThemeProvider';
import { usePremium } from '@/lib/state/PremiumProvider';
import { LEGAL_URLS, FALLBACK_PRICE } from '@/lib/premiumConfig';
import { Sparkline } from '@/components/ui/Sparkline';
import { Button } from '@/components/ui/Button';
import { levelTitle } from '@/lib/level';
import { ACHIEVEMENTS } from '@/lib/achievements';
import { isSoundEnabled, setSoundEnabled } from '@/lib/sound';

const SCORED_MODES: GameMode[] = ['challenge', 'survival'];

export function Profile() {
  const { state, levelInfo, resetProgress } = useGame();
  const { theme, toggleTheme } = useTheme();
  const { isNative, isPremium, openPaywall, restore } = usePremium();
  const [sound, setSound] = useState(true);
  const [confirmReset, setConfirmReset] = useState(false);
  const [restoreMsg, setRestoreMsg] = useState<string | null>(null);

  useEffect(() => { setSound(isSoundEnabled()); }, []);
  const toggleSound = () => { const n = !sound; setSoundEnabled(n); setSound(n); };

  const onRestore = async () => {
    setRestoreMsg('확인 중…');
    const ok = await restore();
    setRestoreMsg(ok ? '구매가 복원되었어요! 🎉' : '복원할 구매 내역이 없어요.');
  };

  const unlocked = new Set(state.unlockedAchievements);
  const accuracyTotal = state.totalCorrect + state.totalWrong;
  const accuracy = accuracyTotal ? Math.round((state.totalCorrect / accuracyTotal) * 100) : 0;
  // 오답 가중치가 높은 순으로 집중 공략 문제 추출
  const weakProblems = Object.entries(state.wrongPool)
    .sort((x, y) => y[1] - x[1])
    .slice(0, 6);

  return (
    <div className="mx-auto max-w-md px-5 pt-[calc(env(safe-area-inset-top)+1rem)] animate-fade-in">
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

      {/* 이용권 */}
      <div className="mb-2 text-sm font-bold text-text-muted">이용권</div>
      <div className="mb-4 overflow-hidden rounded-2xl border border-border bg-surface">
        {isPremium ? (
          <div className="flex items-center gap-3 px-5 py-4">
            <span className="text-accent"><Sparkles className="h-5 w-5" /></span>
            <span className="flex-1 font-bold text-text">평생 이용권</span>
            <span className="rounded-full bg-accent/12 px-2.5 py-1 text-xs font-extrabold text-accent">이용 중</span>
          </div>
        ) : (
          <Row
            icon={<Sparkles className="h-5 w-5" />}
            label="평생 이용권 · 커피 한 잔 값"
            onClick={openPaywall}
            action={FALLBACK_PRICE}
          />
        )}
        {isNative && !isPremium && (
          <>
            <div className="h-px bg-border" />
            <Row icon={<RefreshCcw className="h-5 w-5" />} label="구매 복원" onClick={onRestore} action={restoreMsg ?? ''} />
          </>
        )}
        <div className="h-px bg-border" />
        <Row icon={<FileText className="h-5 w-5" />} label="이용약관" onClick={() => window.open(LEGAL_URLS.terms, '_blank')} action="" />
        <div className="h-px bg-border" />
        <Row icon={<ShieldCheck className="h-5 w-5" />} label="개인정보처리방침" onClick={() => window.open(LEGAL_URLS.privacy, '_blank')} action="" />
      </div>

      {/* 설정 */}
      <div className="mb-2 text-sm font-bold text-text-muted">설정</div>
      <div className="overflow-hidden rounded-2xl border border-border bg-surface">
        <Row icon={theme === 'dark' ? <Moon className="h-5 w-5" /> : <Sun className="h-5 w-5" />} label="다크 모드" onClick={toggleTheme} action={theme === 'dark' ? '켜짐' : '꺼짐'} />
        <div className="h-px bg-border" />
        <Row icon={sound ? <Volume2 className="h-5 w-5" /> : <VolumeX className="h-5 w-5" />} label="효과음" onClick={toggleSound} action={sound ? '켜짐' : '꺼짐'} />
      </div>

      <div className="mt-5 pb-4">
        {!confirmReset ? (
          <Button variant="ghost" className="w-full text-danger" onClick={() => setConfirmReset(true)}>
            <RotateCcw className="h-4 w-4" /> 기록 초기화
          </Button>
        ) : (
          <div className="flex gap-2">
            <Button variant="surface" className="flex-1" onClick={() => setConfirmReset(false)}>취소</Button>
            <Button variant="danger" className="flex-1" onClick={() => { resetProgress(); setConfirmReset(false); }}>초기화 확인</Button>
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
