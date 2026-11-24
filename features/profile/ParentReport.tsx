"use client";
import React, { useEffect, useMemo, useState } from 'react';
import { ChevronDown, Lock, Shield } from 'lucide-react';
import { useGame } from '@/lib/state/GameProvider';
import { useAdventure } from '@/lib/state/AdventureProvider';
import { usePrefs } from '@/lib/state/PrefsProvider';
import { buildWeeklyReport } from '@/lib/report/weekly';
import { parentTipOfWeek } from '@/lib/hints';
import { Button } from '@/components/ui/Button';
import { WeeklyReport } from '@/lib/types';
import { useAuth } from '@/lib/state/AuthProvider';
import { usePremium } from '@/lib/state/PremiumProvider';
import { readLearningIdentity } from '@/lib/learning/identity';
import { fetchLearnerReport, LearnerReport } from '@/lib/learning/remote';
import { Sparkline } from '@/components/ui/Sparkline';
import { Paywall } from '@/features/premium/Paywall';

export function ParentReport() {
  const { state } = useGame();
  const { progress } = useAdventure();
  const {
    guardianPin, setGuardianPin,
    reminderEnabled, reminderHour,
    setReminderEnabled, setReminderHour,
    role,
  } = usePrefs();

  const [open, setOpen] = useState(false);
  const [unlocked, setUnlocked] = useState(false);
  const [pinA, setPinA] = useState('');
  const [pinB, setPinB] = useState('');
  const [pinIn, setPinIn] = useState('');
  const [error, setError] = useState<string | null>(null);

  const report = useMemo(() => buildWeeklyReport(state, progress), [state, progress]);
  const tip = useMemo(() => parentTipOfWeek(), []);

  const resetGate = () => {
    setPinA('');
    setPinB('');
    setPinIn('');
    setError(null);
  };

  const toggle = () => {
    if (open) {
      setOpen(false);
      setUnlocked(false);
      resetGate();
      return;
    }
    setOpen(true);
    resetGate();
  };

  const submitSetup = () => {
    if (!/^\d{4}$/.test(pinA)) {
      setError('숫자 4자리를 입력해 주세요');
      return;
    }
    if (pinA !== pinB) {
      setError('PIN이 서로 달라요');
      return;
    }
    setGuardianPin(pinA);
    setUnlocked(true);
    setError(null);
  };

  const submitUnlock = () => {
    if (pinIn !== guardianPin) {
      setError('PIN이 맞지 않아요');
      return;
    }
    setUnlocked(true);
    setError(null);
  };

  return (
    <div className="mb-4">
      <div className="mb-2 text-sm font-bold text-text-muted">보호자</div>
      <div className="overflow-hidden rounded-2xl border border-border bg-surface">
        <button
          type="button"
          onClick={toggle}
          className="flex w-full items-center gap-3 px-5 py-4 text-left transition-colors hover:bg-surface-2"
        >
          <span className="text-text-muted"><Shield className="h-5 w-5" /></span>
          <span className="flex-1">
            <span className="block font-bold text-text">주간 리포트</span>
            <span className="block text-xs text-text-muted">PIN으로 잠긴 보호자 보기</span>
          </span>
          <ChevronDown className={`h-5 w-5 text-text-muted transition-transform ${open ? 'rotate-180' : ''}`} />
        </button>

        {open && !unlocked && !guardianPin && (
          <div className="border-t border-border px-5 py-4">
            <p className="mb-3 text-sm font-bold text-text-muted">보호자 PIN 4자리를 정해 주세요</p>
            <PinField value={pinA} onChange={setPinA} label="PIN" />
            <PinField value={pinB} onChange={setPinB} label="한 번 더" />
            {error && <p className="mb-2 text-sm font-bold text-danger">{error}</p>}
            <Button variant="primary" size="md" className="w-full" onClick={submitSetup}>저장하고 열기</Button>
          </div>
        )}

        {open && !unlocked && guardianPin && (
          <div className="border-t border-border px-5 py-4">
            <p className="mb-3 flex items-center gap-1.5 text-sm font-bold text-text-muted">
              <Lock className="h-4 w-4" /> PIN을 입력하세요
            </p>
            <PinField value={pinIn} onChange={setPinIn} label="PIN" />
            {error && <p className="mb-2 text-sm font-bold text-danger">{error}</p>}
            <Button variant="primary" size="md" className="w-full" onClick={submitUnlock}>열기</Button>
          </div>
        )}

        {open && unlocked && (
          <div className="border-t border-border px-5 py-4">
            <ReportBody report={report} tip={tip} />
            <LongitudinalSection />
            <div className="mt-4">
              <div className="mb-2 text-xs font-bold text-text-muted">학습 알림 (이 기기)</div>
              {role !== 'guardian' && (
                <p className="mb-2 text-xs text-text-muted">알림 권한은 위에서 ‘보호자’ 역할일 때만 요청해요.</p>
              )}
              <button
                type="button"
                onClick={() => setReminderEnabled(!reminderEnabled)}
                className="mb-2 flex w-full items-center justify-between rounded-xl bg-surface-2 px-4 py-3 text-sm font-bold"
              >
                <span>매일 리마인더</span>
                <span className="text-accent">{reminderEnabled ? '켜짐' : '꺼짐'}</span>
              </button>
              <label className="flex items-center justify-between rounded-xl bg-surface-2 px-4 py-3 text-sm font-bold">
                <span>시각</span>
                <select
                  value={reminderHour}
                  onChange={(e) => setReminderHour(Number(e.target.value))}
                  className="rounded-lg bg-surface px-2 py-1 font-extrabold text-text"
                >
                  {Array.from({ length: 24 }, (_, h) => (
                    <option key={h} value={h}>{`${String(h).padStart(2, '0')}:00`}</option>
                  ))}
                </select>
              </label>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function PinField({ value, onChange, label }: { value: string; onChange: (v: string) => void; label: string }) {
  return (
    <label className="mb-2 flex items-center gap-2 rounded-2xl border border-border bg-surface-2 px-4">
      <span className="w-16 text-xs font-bold text-text-muted">{label}</span>
      <input
        type="password"
        inputMode="numeric"
        autoComplete="off"
        maxLength={4}
        value={value}
        onChange={(e) => onChange(e.target.value.replace(/\D/g, '').slice(0, 4))}
        className="h-11 w-full bg-transparent text-[15px] font-extrabold tracking-[0.4em] text-text outline-none"
      />
    </label>
  );
}

function ReportBody({ report, tip }: { report: WeeklyReport; tip: string }) {
  return (
    <div>
      <p className="mb-3 text-xs font-bold text-text-muted">{report.from} ~ {report.to}</p>
      <div className="mb-3 grid grid-cols-2 gap-2">
        <Mini label="학습한 날" value={`${report.daysActive}일`} />
        <Mini label="정답 / 오답" value={`${report.correct} / ${report.wrong}`} />
        <Mini label="평균 속도" value={report.avgMs ? `${(report.avgMs / 1000).toFixed(1)}초` : '—'} />
        <Mini label="로드맵" value={`${report.roadmapStep}단계`} />
      </div>
      <div className="mb-3 rounded-2xl bg-surface-2 px-4 py-3">
        <div className="mb-1 text-xs font-bold text-text-muted">단별 별</div>
        <div className="flex flex-wrap gap-1.5">
          {Object.entries(report.stars).map(([t, n]) => (
            <span key={t} className="num rounded-lg bg-surface px-2 py-1 text-xs font-extrabold text-text">
              {t}단 {'★'.repeat(n)}{'☆'.repeat(Math.max(0, 3 - n))}
            </span>
          ))}
        </div>
      </div>
      <div className="mb-3 rounded-2xl bg-surface-2 px-4 py-3">
        <div className="mb-1 text-xs font-bold text-text-muted">약한 식 Top 5</div>
        {report.weakKeys.length === 0 ? (
          <p className="text-sm font-bold text-text-muted">이번 주 오답이 아직 없어요</p>
        ) : (
          <div className="flex flex-wrap gap-1.5">
            {report.weakKeys.map((w) => (
              <span key={w.key} className="num rounded-lg bg-danger/10 px-2 py-1 text-xs font-extrabold text-danger">
                {w.key.replace('x', ' × ')} · {w.misses}
              </span>
            ))}
          </div>
        )}
      </div>
      <div className="mb-3 rounded-2xl bg-surface-2 px-4 py-3 text-sm font-bold text-text">
        어드벤처 격파 {report.adventure.defeated}/{report.adventure.total}
        <span className="ml-2 text-text-muted">보스 {report.adventure.bosses}</span>
      </div>
      <p className="text-xs font-bold leading-relaxed text-text-muted">보호자 팁 · {tip}</p>
    </div>
  );
}

function ymd(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function LongitudinalSection() {
  const { user, getAccessToken } = useAuth();
  const { hasFeature } = usePremium();
  const { role } = usePrefs();
  const [data, setData] = useState<LearnerReport | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [paywall, setPaywall] = useState(false);

  const allowed = role === 'guardian' && !!user && hasFeature('longitudinal_report');

  useEffect(() => {
    if (!allowed) {
      setData(null);
      return;
    }
    if (typeof navigator !== 'undefined' && !navigator.onLine) {
      setError('오프라인이라 이 기기 기록만 보여요');
      return;
    }
    let learnerId: string | null = null;
    try {
      learnerId = readLearningIdentity().learnerId;
    } catch (e) {
      console.error('학습 신원 읽기 실패:', e);
      setError('학습자 연결을 확인할 수 없어요');
      return;
    }
    if (!learnerId) {
      setError('아직 서버 기록이 없어요. 기기 기록을 계정에 연결하면 보여요.');
      return;
    }
    let cancelled = false;
    void (async () => {
      try {
        const token = await getAccessToken();
        if (!token || cancelled) return;
        const to = new Date();
        const from = new Date(to.getTime() - 90 * 24 * 60 * 60 * 1000);
        const res = await fetchLearnerReport(token, learnerId, { from: ymd(from), to: ymd(to) });
        if (!cancelled) {
          setData(res);
          setError(null);
        }
      } catch (e) {
        console.error('장기 리포트 조회 실패:', e);
        if (!cancelled) setError('서버 추이를 불러오지 못했어요. 아래 로컬 리포트는 그대로예요.');
      }
    })();
    return () => { cancelled = true; };
  }, [allowed, getAccessToken]);

  if (role !== 'guardian') return null;

  if (!user || !hasFeature('longitudinal_report')) {
    return (
      <div className="mt-4 rounded-2xl bg-surface-2 px-4 py-3">
        <div className="mb-1 text-xs font-bold text-text-muted">오래 보는 추이</div>
        <button
          type="button"
          onClick={() => setPaywall(true)}
          className="text-left text-sm font-bold text-text-muted underline-offset-2 hover:underline"
        >
          여러 주 기록은 보호자에게 보여 주세요
        </button>
        <Paywall open={paywall} onClose={() => setPaywall(false)} showToParent={!user} />
      </div>
    );
  }

  return (
    <div className="mt-4">
      <div className="mb-2 text-xs font-bold text-text-muted">오래 보는 추이 (90일)</div>
      {error && <p className="mb-2 text-xs font-bold text-text-muted">{error}</p>}
      {data && (
        <>
          {data.accuracyTrend.length >= 2 && (
            <div className="mb-3 rounded-2xl bg-surface-2 px-4 py-3">
              <div className="mb-1 text-xs font-bold text-text-muted">정확도</div>
              <Sparkline data={data.accuracyTrend.map((p) => p.value)} width={280} height={40} className="text-accent" />
            </div>
          )}
          {data.weakFacts.length > 0 && (
            <div className="mb-3 rounded-2xl bg-surface-2 px-4 py-3">
              <div className="mb-1 text-xs font-bold text-text-muted">서버 약한 식</div>
              <div className="flex flex-wrap gap-1.5">
                {data.weakFacts.slice(0, 8).map((w) => (
                  <span key={w.factId} className="num rounded-lg bg-danger/10 px-2 py-1 text-xs font-extrabold text-danger">
                    {w.factId.replace('x', ' × ')} · {w.misses}
                  </span>
                ))}
              </div>
            </div>
          )}
          {data.dueReviews.length > 0 && (
            <div className="mb-3 rounded-2xl bg-surface-2 px-4 py-3">
              <div className="mb-1 text-xs font-bold text-text-muted">복습 예정</div>
              <div className="flex flex-wrap gap-1.5">
                {data.dueReviews.slice(0, 8).map((d) => (
                  <span key={d.factId} className="num rounded-lg bg-surface px-2 py-1 text-xs font-extrabold text-text">
                    {d.factId.replace('x', ' × ')}
                  </span>
                ))}
              </div>
            </div>
          )}
          {data.recommendations.length > 0 && (
            <ul className="mb-1 list-disc pl-5 text-xs font-bold text-text-muted">
              {data.recommendations.slice(0, 4).map((r) => <li key={r}>{r}</li>)}
            </ul>
          )}
        </>
      )}
    </div>
  );
}

function Mini({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-2xl bg-surface-2 px-3 py-2">
      <div className="text-[11px] font-bold text-text-muted">{label}</div>
      <div className="num text-lg font-extrabold text-text">{value}</div>
    </div>
  );
}
