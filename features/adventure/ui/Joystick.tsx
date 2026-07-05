"use client";
// 가상 조이스틱 — 입력은 moveRef에 기록, 노브는 DOM style 직접 갱신 (리렌더 0)
import React, { useCallback, useRef, MutableRefObject } from 'react';
import { MoveState } from '../world/controls';

const BASE = 118;   // 베이스 지름(px)
const KNOB = 52;    // 노브 지름(px)
const RADIUS = (BASE - KNOB) / 2;
const DEADZONE = 0.16;

export function Joystick({ moveRef }: { moveRef: MutableRefObject<MoveState> }) {
  const baseRef = useRef<HTMLDivElement>(null);
  const knobRef = useRef<HTMLDivElement>(null);
  const pointerIdRef = useRef<number | null>(null);

  const apply = useCallback(
    (clientX: number, clientY: number) => {
      const base = baseRef.current;
      if (!base) return;
      const rect = base.getBoundingClientRect();
      let dx = clientX - (rect.left + rect.width / 2);
      let dy = clientY - (rect.top + rect.height / 2);
      const len = Math.hypot(dx, dy);
      if (len > RADIUS) {
        dx = (dx / len) * RADIUS;
        dy = (dy / len) * RADIUS;
      }
      if (knobRef.current) knobRef.current.style.transform = `translate(${dx}px, ${dy}px)`;
      const nx = dx / RADIUS;
      const ny = dy / RADIUS;
      const mag = Math.hypot(nx, ny);
      moveRef.current.joyX = mag < DEADZONE ? 0 : nx;
      moveRef.current.joyZ = mag < DEADZONE ? 0 : ny;
    },
    [moveRef]
  );

  const reset = useCallback(() => {
    pointerIdRef.current = null;
    if (knobRef.current) knobRef.current.style.transform = 'translate(0px, 0px)';
    moveRef.current.joyX = 0;
    moveRef.current.joyZ = 0;
  }, [moveRef]);

  return (
    <div
      ref={baseRef}
      onPointerDown={(e) => {
        pointerIdRef.current = e.pointerId;
        e.currentTarget.setPointerCapture(e.pointerId);
        apply(e.clientX, e.clientY);
      }}
      onPointerMove={(e) => {
        if (pointerIdRef.current !== e.pointerId) return;
        apply(e.clientX, e.clientY);
      }}
      onPointerUp={reset}
      onPointerCancel={reset}
      className="absolute bottom-[calc(env(safe-area-inset-bottom)+1.5rem)] left-6 z-10 flex items-center justify-center
        rounded-full border border-white/30 bg-black/20 backdrop-blur-sm touch-none select-none"
      style={{ width: BASE, height: BASE }}
      aria-label="이동 조이스틱"
    >
      <div
        ref={knobRef}
        className="rounded-full bg-white/85 shadow-lg will-change-transform"
        style={{ width: KNOB, height: KNOB }}
      />
    </div>
  );
}
