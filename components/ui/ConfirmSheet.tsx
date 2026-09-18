"use client";
import React from 'react';
import { Button } from './Button';

interface ConfirmSheetProps {
  title: string;
  body: string;
  confirmLabel: string;
  cancelLabel: string;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ConfirmSheet({
  title, body, confirmLabel, cancelLabel, onConfirm, onCancel,
}: ConfirmSheetProps) {
  return (
    <div
      className="absolute inset-0 z-[60] flex items-end justify-center bg-bg/70 px-5 pb-[calc(env(safe-area-inset-bottom)+1.25rem)] backdrop-blur-sm"
      role="dialog"
      aria-modal="true"
      aria-labelledby="confirm-sheet-title"
    >
      <div className="w-full max-w-md rounded-3xl border border-border bg-surface p-5 shadow-xl">
        <h2 id="confirm-sheet-title" className="text-lg font-extrabold text-text">{title}</h2>
        <p className="mt-1.5 mb-4 text-sm font-bold text-text-muted">{body}</p>
        <div className="flex flex-col gap-2">
          <Button variant="primary" size="lg" onClick={onConfirm}>{confirmLabel}</Button>
          <Button variant="surface" size="md" onClick={onCancel}>{cancelLabel}</Button>
        </div>
      </div>
    </div>
  );
}
