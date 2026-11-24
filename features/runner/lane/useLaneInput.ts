"use client";

import { useEffect, useRef, type PointerEvent } from 'react';
import type { LaneRunnerState } from '@/lib/laneRunner';

const SWIPE_PX = 28;

interface LaneInputHandlers {
  getState: () => LaneRunnerState;
  move: (delta: -1 | 1) => void;
  setLane: (lane: number) => void;
  togglePause: () => void;
  pause: () => void;
}

// 키보드(↑↓·W/S·1~3·Space·Esc, W/S는 입력기와 무관하게 물리 키 기준)와 스테이지 스와이프를 레인 이동으로 바꿉니다.
export function useLaneInput({ getState, move, setLane, togglePause, pause }: LaneInputHandlers) {
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.repeat || event.altKey || event.ctrlKey || event.metaKey) return;
      if (event.target instanceof HTMLElement && (event.target.isContentEditable || /^(INPUT|SELECT|TEXTAREA)$/.test(event.target.tagName))) return;
      const phase = getState().phase;
      const key = event.key.toLowerCase();
      if (phase === 'running' && (key === 'arrowup' || event.code === 'KeyW')) {
        event.preventDefault(); // 화살표로 페이지가 스크롤되지 않게 합니다.
        move(-1);
      } else if (phase === 'running' && (key === 'arrowdown' || event.code === 'KeyS')) {
        event.preventDefault();
        move(1);
      } else if (phase === 'running' && /^[123]$/.test(key)) {
        event.preventDefault();
        setLane(Number(key) - 1);
      } else if (event.code === 'Space' && (phase === 'running' || phase === 'paused')) {
        // 버튼에 포커스가 있으면 기본 키보드 클릭을 유지합니다.
        if (event.target instanceof HTMLElement && event.target.closest('button, a')) return;
        event.preventDefault();
        togglePause();
      } else if (event.key === 'Escape') {
        pause();
      }
    };
    window.addEventListener('keydown', onKey);
    window.addEventListener('blur', pause);
    return () => {
      window.removeEventListener('keydown', onKey);
      window.removeEventListener('blur', pause);
    };
  }, [getState, move, setLane, togglePause, pause]);

  // 한 번의 제스처는 한 칸만 이동합니다.
  const swipe = useRef<{ pointerId: number; x: number; y: number; done: boolean } | null>(null);
  return {
    onPointerDown: (event: PointerEvent<HTMLElement>) => {
      if (getState().phase !== 'running' || !event.isPrimary) return;
      swipe.current = { pointerId: event.pointerId, x: event.clientX, y: event.clientY, done: false };
      event.currentTarget.setPointerCapture(event.pointerId);
    },
    onPointerMove: (event: PointerEvent<HTMLElement>) => {
      const current = swipe.current;
      if (!current || current.done || current.pointerId !== event.pointerId) return;
      const dx = event.clientX - current.x;
      const dy = event.clientY - current.y;
      if (Math.abs(dy) < SWIPE_PX || Math.abs(dy) <= Math.abs(dx)) return;
      current.done = true;
      move(dy < 0 ? -1 : 1);
    },
    onPointerUp: (event: PointerEvent<HTMLElement>) => {
      if (swipe.current?.pointerId === event.pointerId) swipe.current = null;
    },
    onPointerCancel: () => {
      swipe.current = null;
    },
  };
}
