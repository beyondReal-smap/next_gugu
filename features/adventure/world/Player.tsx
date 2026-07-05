"use client";
// 플레이어 캐릭터 — 이동/회전/걸음 모션 전부 useFrame + ref (setState 금지)
import React, { useRef, MutableRefObject } from 'react';
import { useFrame } from '@react-three/fiber';
import * as THREE from 'three';
import { FIELD_BOUND } from '@/lib/adventure/world';
import { MoveState, moveVector } from './controls';

const PLAYER_COLOR = '#6366f1'; // 앱 액센트(인디고)와 톤 일치
const SPEED = 6.2;

interface PlayerProps {
  moveRef: MutableRefObject<MoveState>;
  posRef: MutableRefObject<[number, number]>; // [x, z] — 배틀 복귀 시 위치 유지용
}

export function Player({ moveRef, posRef }: PlayerProps) {
  const groupRef = useRef<THREE.Group>(null);
  const bodyRef = useRef<THREE.Group>(null);
  const angleRef = useRef(Math.PI); // 시작은 월드 안쪽(-z)을 바라봄

  useFrame((state, delta) => {
    const g = groupRef.current;
    if (!g) return;
    const [dx, dz] = moveVector(moveRef.current);
    const moving = dx !== 0 || dz !== 0;

    if (moving) {
      const bound = FIELD_BOUND - 0.6;
      const nx = Math.max(-bound, Math.min(bound, g.position.x + dx * SPEED * delta));
      const nz = Math.max(-bound, Math.min(bound, g.position.z + dz * SPEED * delta));
      g.position.x = nx;
      g.position.z = nz;
      posRef.current[0] = nx;
      posRef.current[1] = nz;

      // 이동 방향으로 최단 회전 보간
      const target = Math.atan2(dx, dz);
      let diff = target - angleRef.current;
      while (diff > Math.PI) diff -= Math.PI * 2;
      while (diff < -Math.PI) diff += Math.PI * 2;
      angleRef.current += diff * Math.min(1, delta * 12);
      g.rotation.y = angleRef.current;
    }

    // 걸을 때 통통, 멈추면 잔잔한 숨쉬기
    if (bodyRef.current) {
      const t = state.clock.getElapsedTime();
      bodyRef.current.position.y = 0.62 + (moving ? Math.abs(Math.sin(t * 9)) * 0.12 : Math.sin(t * 2) * 0.04);
    }
  });

  return (
    <group ref={groupRef} position={[posRef.current[0], 0, posRef.current[1]]} rotation={[0, Math.PI, 0]}>
      {/* 그림자 블롭 */}
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, 0.02, 0]}>
        <circleGeometry args={[0.5, 24]} />
        <meshBasicMaterial color="#000000" transparent opacity={0.18} />
      </mesh>
      <group ref={bodyRef}>
        <mesh>
          <capsuleGeometry args={[0.4, 0.5, 6, 16]} />
          <meshStandardMaterial color={PLAYER_COLOR} />
        </mesh>
        {/* 눈 — 바라보는 방향(+z 로컬) */}
        <group position={[0, 0.32, 0.36]}>
          {[-0.14, 0.14].map((x) => (
            <group key={x} position={[x, 0, 0]}>
              <mesh>
                <sphereGeometry args={[0.09, 12, 12]} />
                <meshStandardMaterial color="#ffffff" />
              </mesh>
              <mesh position={[0, 0, 0.065]}>
                <sphereGeometry args={[0.045, 10, 10]} />
                <meshStandardMaterial color="#20242c" />
              </mesh>
            </group>
          ))}
        </group>
      </group>
    </group>
  );
}
