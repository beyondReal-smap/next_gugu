"use client";
// NPC — 프리미티브 캐릭터(캡슐+눈) + 이름 라벨 + 상태 링
import React, { useEffect, useMemo, useRef } from 'react';
import { useFrame } from '@react-three/fiber';
import * as THREE from 'three';
import { NpcDef } from '@/lib/adventure/types';
import { makeLabelTexture } from './spriteLabel';

const DEFEATED_COLOR = '#a5adba';

function Eyes() {
  return (
    <group position={[0, 0.35, 0.38]}>
      {[-0.15, 0.15].map((x) => (
        <group key={x} position={[x, 0, 0]}>
          <mesh>
            <sphereGeometry args={[0.1, 12, 12]} />
            <meshStandardMaterial color="#ffffff" />
          </mesh>
          <mesh position={[0, 0, 0.07]}>
            <sphereGeometry args={[0.05, 10, 10]} />
            <meshStandardMaterial color="#20242c" />
          </mesh>
        </group>
      ))}
    </group>
  );
}

interface NpcProps {
  npc: NpcDef;
  defeated: boolean;
  idleOffset: number; // 개체마다 다른 둥실거림 위상
}

export function Npc({ npc, defeated, idleOffset }: NpcProps) {
  const bodyRef = useRef<THREE.Group>(null);
  const isBoss = npc.kind === 'boss';
  const bodyScale = isBoss ? 1.45 : 1;
  const color = defeated ? DEFEATED_COLOR : npc.color;

  const label = useMemo(
    () => makeLabelTexture(`${defeated ? '✓ ' : ''}${npc.name} · ${npc.table}단`),
    [npc.name, npc.table, defeated]
  );
  useEffect(() => () => label?.texture.dispose(), [label]);

  // 둥실거리는 대기 모션 (ref 직접 갱신 — setState 없음)
  useFrame(({ clock }) => {
    if (!bodyRef.current) return;
    const t = clock.getElapsedTime();
    bodyRef.current.position.y = 0.62 * bodyScale + Math.sin(t * 2 + idleOffset) * 0.05;
    bodyRef.current.rotation.y = Math.sin(t * 0.8 + idleOffset) * 0.15;
  });

  const labelH = 0.62;
  return (
    <group position={[npc.pos[0], 0, npc.pos[1]]}>
      {/* 상태 링 — 격파 시 초록 */}
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, 0.03, 0]}>
        <ringGeometry args={[0.75 * bodyScale, 0.92 * bodyScale, 32]} />
        <meshBasicMaterial color={defeated ? '#22c55e' : '#ffffff'} transparent opacity={defeated ? 0.75 : 0.35} />
      </mesh>
      {/* 그림자 블롭 */}
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, 0.02, 0]}>
        <circleGeometry args={[0.55 * bodyScale, 24]} />
        <meshBasicMaterial color="#000000" transparent opacity={0.16} />
      </mesh>
      {/* 몸통 */}
      <group ref={bodyRef} scale={bodyScale}>
        <mesh>
          <capsuleGeometry args={[0.42, 0.5, 6, 16]} />
          <meshStandardMaterial color={color} />
        </mesh>
        <Eyes />
        {isBoss && (
          <mesh position={[0, 0.85, 0]}>
            <coneGeometry args={[0.26, 0.34, 5]} />
            <meshStandardMaterial color="#ffd166" flatShading />
          </mesh>
        )}
      </group>
      {/* 이름 라벨 */}
      {label && (
        <sprite position={[0, isBoss ? 2.5 : 1.95, 0]} scale={[labelH * label.aspect, labelH, 1]}>
          <spriteMaterial map={label.texture} transparent depthWrite={false} />
        </sprite>
      )}
    </group>
  );
}
