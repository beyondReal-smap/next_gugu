"use client";
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '@/lib/state/AuthProvider';
import { useGame } from '@/lib/state/GameProvider';
import { usePrefs } from '@/lib/state/PrefsProvider';
import { setSessionCommitListener } from '@/lib/learning/bridge';
import { enqueueSessionEvents } from '@/lib/learning/record';
import { bindLearner, readLearningIdentity } from '@/lib/learning/identity';
import { ClaimScreen } from '@/features/auth/ClaimScreen';
import {
  confirmLearningClaim,
  fetchLearningState,
  inspectLearningClaim,
  LearningClaimResponse,
} from '@/lib/api/learning';
import { flushLearningOutbox, readLearningOutbox } from '@/lib/sync/outbox';
import { remapOutboxLearnerId } from '@/lib/learning/outboxRemap';
import { SessionResult } from '@/lib/types';

function isOnline(): boolean {
  return typeof navigator === 'undefined' ? false : navigator.onLine;
}

export function LearningSync() {
  const { user, loading: authLoading, getAccessToken } = useAuth();
  const { loaded, state, mergeServerSnapshot } = useGame();
  const { role } = usePrefs();
  const [claim, setClaim] = useState<LearningClaimResponse | null>(null);
  const dismissed = useRef(false);
  const mergedFor = useRef<string | null>(null);

  const canSync = role === 'guardian' && !!user;

  const flush = useCallback(async () => {
    if (!canSync || !isOnline()) return;
    const token = await getAccessToken();
    if (!token) return;
    try {
      // 배치 100건. 수락/중복이 없으면(전부 거부) 같은 큐를 돌지 않는다.
      for (let i = 0; i < 50; i++) {
        const res = await flushLearningOutbox(token);
        if (!res) break;
        const progressed = res.acceptedEventIds.length + res.duplicateEventIds.length;
        if (progressed === 0) break;
      }
    } catch (e) {
      console.error('학습 아웃박스 업로드 실패 (큐에 남김):', e);
    }
  }, [canSync, getAccessToken]);

  const pullState = useCallback(async (learnerId: string) => {
    if (!isOnline()) return;
    const token = await getAccessToken();
    if (!token) return;
    try {
      const res = await fetchLearningState(token, learnerId);
      const snap = res.snapshots[0];
      if (snap) mergeServerSnapshot(snap.state);
    } catch (e) {
      console.error('학습 상태 조회 실패:', e);
    }
  }, [getAccessToken, mergeServerSnapshot]);

  useEffect(() => {
    setSessionCommitListener((result: SessionResult) => {
      if (role !== 'guardian' || !user) return;
      const id = readLearningIdentity();
      if (!id.learnerId) return;
      try {
        enqueueSessionEvents(result, {
          learnerId: id.learnerId,
          deviceId: id.installId,
          sessionId: crypto.randomUUID(),
        });
      } catch (e) {
        console.error('학습 이벤트 적재 실패:', e);
        return;
      }
      void flush();
    });
    return () => setSessionCommitListener(null);
  }, [role, user, flush]);

  useEffect(() => {
    const onOnline = () => { void flush(); };
    window.addEventListener('online', onOnline);
    return () => window.removeEventListener('online', onOnline);
  }, [flush]);

  useEffect(() => {
    if (!loaded || authLoading) return;
    if (!canSync || !user) {
      setClaim(null);
      return;
    }
    const id = readLearningIdentity();
    if (id.claimedUserId === user.id && id.learnerId) {
      if (mergedFor.current !== user.id) {
        mergedFor.current = user.id;
        void pullState(id.learnerId).then(() => flush());
      }
      return;
    }
    if (dismissed.current || !isOnline()) return;

    let cancelled = false;
    void (async () => {
      const token = await getAccessToken();
      if (!token || cancelled) return;
      const localCount = state.totalCorrect + state.totalWrong;
      try {
        const res = await inspectLearningClaim(token, id.installId, localCount);
        if (!cancelled) setClaim(res);
      } catch (e) {
        console.error('게스트 기록 귀속 조회 실패:', e);
      }
    })();
    return () => { cancelled = true; };
  }, [loaded, authLoading, canSync, user, state.totalCorrect, state.totalWrong, getAccessToken, pullState, flush]);

  const finalizeClaim = async (learnerId: string) => {
    if (!user) throw new Error('로그인이 필요합니다');
    const token = await getAccessToken();
    if (!token) throw new Error('로그인이 필요합니다');
    const installId = readLearningIdentity().installId;
    const confirmed = await confirmLearningClaim(token, installId, learnerId);
    bindLearner(user.id, confirmed.learnerId);
    const remap = remapOutboxLearnerId(confirmed.learnerId);
    if (remap.rewritten > 0) {
      console.info(`아웃박스 learnerId ${remap.rewritten}건을 서버 id로 맞춤 (남은 ${remap.remaining}건)`);
    }
    setClaim(null);
    mergedFor.current = user.id;
    await pullState(confirmed.learnerId);
    await flush();
    const after = readLearningOutbox();
    const stillUnknown = after.rejected.filter((item) => item.code === 'LEARNER_NOT_FOUND');
    if (stillUnknown.length > 0) {
      console.error('claim 확정 후에도 LEARNER_NOT_FOUND 잔여:', stillUnknown.length);
    }
  };

  const onCreate = async () => {
    if (!claim?.learnerId) {
      throw new Error('서버가 학습자를 아직 만들지 못했어요. 잠시 후 다시 시도해 주세요.');
    }
    await finalizeClaim(claim.learnerId);
  };

  const onAttach = async (learnerId: string) => {
    await finalizeClaim(learnerId);
  };

  const onDismiss = () => {
    dismissed.current = true;
    setClaim(null);
  };

  return (
    <ClaimScreen
      claim={canSync ? claim : null}
      onCreate={onCreate}
      onAttach={onAttach}
      onDismiss={onDismiss}
    />
  );
}
