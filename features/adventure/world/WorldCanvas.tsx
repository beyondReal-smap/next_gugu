"use client";
// R3F 월드 씬 — three를 import하는 유일한 진입점 (dynamic ssr:false로 청크 분리)
import React, { useCallback, useMemo, useRef, MutableRefObject } from 'react';
import { Canvas, useFrame } from '@react-three/fiber';
import * as THREE from 'three';
import { NpcDef, RegionDef } from '@/lib/adventure/types';
import { ENCOUNTER_DIST, ENCOUNTER_EXIT_DIST } from '@/lib/adventure/world';
import { MoveState } from './controls';
import { Terrain } from './Terrain';
import { Npc } from './Npc';
import { Player } from './Player';

// 3인칭 팔로우 카메라 (프레임률 무관 지수 보간)
function CameraRig({ posRef }: { posRef: MutableRefObject<[number, number]> }) {
  const target = useMemo(() => new THREE.Vector3(), []);
  useFrame(({ camera }, delta) => {
    const [x, z] = posRef.current;
    target.set(x, 8.5, z + 9.5);
    camera.position.lerp(target, 1 - Math.pow(0.0001, delta));
    camera.lookAt(x, 0.8, z);
  });
  return null;
}

// 근접 NPC 감지 — 120ms 간격, 변화가 있을 때만 콜백 (히스테리시스로 깜빡임 방지)
function EncounterWatcher({
  posRef,
  npcs,
  onEncounter,
}: {
  posRef: MutableRefObject<[number, number]>;
  npcs: NpcDef[];
  onEncounter: (npc: NpcDef | null) => void;
}) {
  const currentRef = useRef<string | null>(null);
  const accRef = useRef(0);

  useFrame((_, delta) => {
    accRef.current += delta;
    if (accRef.current < 0.12) return;
    accRef.current = 0;

    const [px, pz] = posRef.current;
    const dist = (n: NpcDef) => Math.hypot(n.pos[0] - px, n.pos[1] - pz);

    let nearest: NpcDef | null = null;
    let nearestD = Infinity;
    for (const n of npcs) {
      const d = dist(n);
      if (d < nearestD) {
        nearestD = d;
        nearest = n;
      }
    }

    const curId = currentRef.current;
    if (curId) {
      const cur = npcs.find((n) => n.id === curId);
      const curD = cur ? dist(cur) : Infinity;
      if (curD > ENCOUNTER_EXIT_DIST) {
        if (nearest && nearestD <= ENCOUNTER_DIST) {
          currentRef.current = nearest.id;
          onEncounter(nearest);
        } else {
          currentRef.current = null;
          onEncounter(null);
        }
      }
    } else if (nearest && nearestD <= ENCOUNTER_DIST) {
      currentRef.current = nearest.id;
      onEncounter(nearest);
    }
  });
  return null;
}

export interface WorldCanvasProps {
  region: RegionDef;
  defeatedIds: string[];
  moveRef: MutableRefObject<MoveState>;
  posRef: MutableRefObject<[number, number]>;
  onEncounter: (npc: NpcDef | null) => void;
}

export default function WorldCanvas({ region, defeatedIds, moveRef, posRef, onEncounter }: WorldCanvasProps) {
  // 부모 리렌더에도 useFrame 쪽 콜백은 안정적으로 유지
  const onEncounterRef = useRef(onEncounter);
  onEncounterRef.current = onEncounter;
  const handleEncounter = useCallback((npc: NpcDef | null) => onEncounterRef.current(npc), []);

  const defeatedSet = useMemo(() => new Set(defeatedIds), [defeatedIds]);

  return (
    <Canvas
      dpr={[1, 1.5]}
      camera={{ fov: 50, near: 0.5, far: 80, position: [posRef.current[0], 8.5, posRef.current[1] + 9.5] }}
      gl={{ antialias: true, powerPreference: 'high-performance' }}
    >
      <color attach="background" args={[region.theme.sky]} />
      <fog attach="fog" args={[region.theme.sky, 26, 55]} />
      <ambientLight intensity={0.85} />
      <directionalLight position={[6, 12, 4]} intensity={1.2} />

      <Terrain region={region} />
      {region.npcs.map((n, i) => (
        <Npc key={n.id} npc={n} defeated={defeatedSet.has(n.id)} idleOffset={i * 1.7} />
      ))}
      <Player moveRef={moveRef} posRef={posRef} />
      <CameraRig posRef={posRef} />
      <EncounterWatcher posRef={posRef} npcs={region.npcs} onEncounter={handleEncounter} />
    </Canvas>
  );
}
