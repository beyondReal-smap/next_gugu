"use client";
// 이동 입력 — 조이스틱/키보드를 ref에 기록하고 useFrame에서 소비 (리렌더 없음)
import { useEffect, MutableRefObject } from 'react';

export interface MoveState {
  joyX: number; // 조이스틱 -1..1 (화면 x = 월드 x)
  joyZ: number; // 조이스틱 -1..1 (화면 y = 월드 z)
  keys: { up: boolean; down: boolean; left: boolean; right: boolean };
}

export function createMoveState(): MoveState {
  return { joyX: 0, joyZ: 0, keys: { up: false, down: false, left: false, right: false } };
}

// 조이스틱 우선, 없으면 키보드 — 정규화된 [x, z] 방향
export function moveVector(m: MoveState): [number, number] {
  let x = m.joyX;
  let z = m.joyZ;
  if (x === 0 && z === 0) {
    x = (m.keys.right ? 1 : 0) - (m.keys.left ? 1 : 0);
    z = (m.keys.down ? 1 : 0) - (m.keys.up ? 1 : 0);
  }
  const len = Math.hypot(x, z);
  if (len === 0) return [0, 0];
  if (len > 1) return [x / len, z / len];
  return [x, z];
}

// WASD/화살표 → MoveState.keys (등록은 월드가 떠 있는 동안 1회)
export function useKeyboardMove(moveRef: MutableRefObject<MoveState>) {
  useEffect(() => {
    const setKey = (e: KeyboardEvent, down: boolean): boolean => {
      const k = moveRef.current.keys;
      switch (e.key) {
        case 'ArrowUp': case 'w': case 'W': k.up = down; return true;
        case 'ArrowDown': case 's': case 'S': k.down = down; return true;
        case 'ArrowLeft': case 'a': case 'A': k.left = down; return true;
        case 'ArrowRight': case 'd': case 'D': k.right = down; return true;
        default: return false;
      }
    };
    const onDown = (e: KeyboardEvent) => { if (setKey(e, true) && e.key.startsWith('Arrow')) e.preventDefault(); };
    const onUp = (e: KeyboardEvent) => setKey(e, false);
    window.addEventListener('keydown', onDown);
    window.addEventListener('keyup', onUp);
    const ref = moveRef;
    return () => {
      window.removeEventListener('keydown', onDown);
      window.removeEventListener('keyup', onUp);
      ref.current.keys = { up: false, down: false, left: false, right: false };
    };
  }, [moveRef]);
}
