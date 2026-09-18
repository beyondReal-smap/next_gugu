"use client";
import React, { useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { Button } from '@/components/ui/Button';
import type { LearningClaimCandidate, LearningClaimResponse } from '@/lib/api/learning';

interface ClaimScreenProps {
  claim: LearningClaimResponse | null;
  onCreate: () => Promise<void>;
  onAttach: (learnerId: string) => Promise<void>;
  onDismiss: () => void;
}

export function ClaimScreen({ claim, onCreate, onAttach, onDismiss }: ClaimScreenProps) {
  const [picked, setPicked] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!claim) return null;

  const run = async (fn: () => Promise<void>) => {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      await fn();
    } catch (e) {
      console.error('게스트 기록 연결 실패:', e);
      setError(e instanceof Error ? e.message : '연결에 실패했어요. 학습은 이 기기에서 계속할 수 있어요.');
    } finally {
      setBusy(false);
    }
  };

  const confirmAttach = () => {
    const id = picked ?? claim.candidates[0]?.learnerId;
    if (!id) {
      console.error('연결할 학습자가 없습니다');
      setError('연결할 학습자가 없어요');
      return;
    }
    void run(() => onAttach(id));
  };

  return (
    <AnimatePresence>
      <motion.div
        initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
        className="fixed inset-0 z-[90] flex items-end justify-center bg-bg/70 backdrop-blur-md sm:items-center"
      >
        <motion.div
          initial={{ y: 40, opacity: 0 }} animate={{ y: 0, opacity: 1 }}
          className="w-full max-w-md rounded-t-3xl border border-border bg-surface p-6 shadow-2xl sm:rounded-3xl"
        >
          <div className="mb-2 text-lg font-extrabold text-text">이 기기의 기록을 계정에 연결할까요?</div>
          <p className="mb-4 text-sm font-bold text-text-muted">
            자동으로 합치거나 지우지 않아요. 보호자가 선택해 주세요.
          </p>

          {claim.action === 'create_learner' && (
            <p className="mb-4 text-sm text-text">이 계정에는 아직 연결된 학습자가 없어요. 이 기기 기록으로 새로 연결할 수 있어요.</p>
          )}

          {claim.action === 'attach_to_existing' && claim.candidates[0] && (
            <div className="mb-3 rounded-2xl border border-border bg-surface-2 p-3">
              <CandidateCard item={claim.candidates[0]} />
            </div>
          )}

          {claim.action === 'conflict' && (
            <div className="mb-3 flex flex-col gap-2">
              {claim.candidates.map((c) => (
                <button
                  key={c.learnerId}
                  type="button"
                  onClick={() => setPicked(c.learnerId)}
                  className={`rounded-2xl border p-3 text-left ${picked === c.learnerId ? 'border-accent bg-accent/10' : 'border-border bg-surface-2'}`}
                >
                  <CandidateCard item={c} />
                </button>
              ))}
            </div>
          )}

          {error && <p className="mb-2 text-sm font-bold text-danger">{error}</p>}

          <div className="mt-4 flex flex-col gap-2">
            {claim.action === 'create_learner' ? (
              <Button variant="primary" size="lg" className="w-full" onClick={() => { void run(onCreate); }} disabled={busy}>
                {busy ? '연결하는 중…' : '이 기기 기록으로 연결'}
              </Button>
            ) : (
              <Button
                variant="primary"
                size="lg"
                className="w-full"
                onClick={confirmAttach}
                disabled={busy || (claim.action === 'conflict' && !picked && claim.candidates.length !== 1)}
              >
                {busy ? '연결하는 중…' : '선택한 기록에 연결'}
              </Button>
            )}
            <Button variant="surface" size="md" className="w-full" onClick={onDismiss} disabled={busy}>나중에</Button>
          </div>
        </motion.div>
      </motion.div>
    </AnimatePresence>
  );
}

function CandidateCard({ item }: { item: LearningClaimCandidate }) {
  return (
    <div>
      <div className="font-extrabold text-text">{item.displayName}</div>
      <div className="text-xs font-bold text-text-muted">최근 {item.updatedAt}</div>
    </div>
  );
}
