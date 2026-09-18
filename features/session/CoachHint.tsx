"use client";
import React, { useEffect, useRef, useState } from 'react';
import { hintForTable } from '@/lib/hints';
import { speakText } from '@/lib/a11y/speak';
import { useAuth } from '@/lib/state/AuthProvider';
import { usePrefs } from '@/lib/state/PrefsProvider';
import { usePremium } from '@/lib/state/PremiumProvider';
import { readLearningIdentity } from '@/lib/learning/identity';
import { explainWrongAnswer } from '@/lib/learning/remote';
import { Paywall } from '@/features/premium/Paywall';

interface CoachHintProps {
  table: number;
  factId: string;
  submitted: number | boolean | string | undefined;
  attemptNo: number;
}

type View =
  | { kind: 'local'; extra?: string }
  | { kind: 'loading' }
  | { kind: 'coach'; hint: string; checkQuestion: string };

export function CoachHint({ table, factId, submitted, attemptNo }: CoachHintProps) {
  const { user, getAccessToken } = useAuth();
  const { role, ttsEnabled } = usePrefs();
  const { hasFeature } = usePremium();
  const [view, setView] = useState<View>({ kind: 'local' });
  const [paywall, setPaywall] = useState(false);
  const spoken = useRef<string | null>(null);

  const stuck = attemptNo >= 2;
  const canCoach = stuck && role === 'guardian' && !!user && hasFeature('ai_coach') && typeof navigator !== 'undefined' && navigator.onLine;

  useEffect(() => {
    if (!stuck) {
      setView({ kind: 'local' });
      return;
    }
    if (!canCoach) {
      setView({
        kind: 'local',
        extra: hasFeature('ai_coach')
          ? undefined
          : role === 'guardian'
            ? '더 친절한 설명은 여기서 켤 수 있어요'
            : '더 친절한 설명은 보호자에게 보여 주세요',
      });
      return;
    }
    const identity = readLearningIdentity();
    if (!identity.learnerId) {
      setView({ kind: 'local' });
      return;
    }
    if (submitted === undefined) {
      setView({ kind: 'local' });
      return;
    }

    let cancelled = false;
    setView({ kind: 'loading' });
    void (async () => {
      try {
        const token = await getAccessToken();
        if (!token || cancelled) return;
        const res = await explainWrongAnswer(token, {
          learnerId: identity.learnerId!,
          factId,
          submittedAnswer: submitted,
          attemptNo,
        });
        if (!cancelled) setView({ kind: 'coach', hint: res.hint, checkQuestion: res.checkQuestion });
      } catch (e) {
        console.error('코치 요청 실패, 기본 힌트 사용:', e);
        if (!cancelled) setView({ kind: 'local', extra: '지금은 기본 힌트로 볼게요' });
      }
    })();
    return () => { cancelled = true; };
  }, [stuck, canCoach, hasFeature, role, getAccessToken, factId, submitted, attemptNo]);

  const localHint = hintForTable(table);
  const spokenText = view.kind === 'coach'
    ? `${view.hint} ${view.checkQuestion}`
    : `힌트: ${localHint}`;

  useEffect(() => {
    if (!ttsEnabled) return;
    if (spoken.current === spokenText) return;
    spoken.current = spokenText;
    speakText(spokenText);
  }, [ttsEnabled, spokenText]);

  return (
    <div className="mt-2 text-left" role="status" aria-live="polite">
      {view.kind === 'coach' ? (
        <>
          <div className="text-accent">{view.hint}</div>
          <div className="mt-1 text-text">{view.checkQuestion}</div>
        </>
      ) : (
        <>
          <div className="text-accent">힌트: {localHint}</div>
          {view.kind === 'loading' && (
            <div className="mt-1 text-text-muted">더 쉬운 설명을 찾고 있어요</div>
          )}
          {view.kind === 'local' && view.extra && (
            view.extra.includes('설명') ? (
              <button
                type="button"
                onClick={() => setPaywall(true)}
                className="mt-1 text-left text-text-muted underline-offset-2 hover:underline"
              >
                {view.extra}
              </button>
            ) : (
              <div className="mt-1 text-text-muted">{view.extra}</div>
            )
          )}
        </>
      )}
      <Paywall open={paywall} onClose={() => setPaywall(false)} showToParent={role !== 'guardian'} />
    </div>
  );
}
