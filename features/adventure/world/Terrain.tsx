"use client";
// 지역 지형 — 바닥 + 시드 기반 소품 + 지역 랜드마크 (전부 프리미티브 도형)
import React from 'react';
import { DecoKind, LandmarkKind, RegionDef, RegionTheme } from '@/lib/adventure/types';
import { FIELD_BOUND } from '@/lib/adventure/world';

function Tree({ accent, scale }: { accent: string; scale: number }) {
  return (
    <group scale={scale}>
      <mesh position={[0, 0.3, 0]}>
        <cylinderGeometry args={[0.13, 0.18, 0.6, 8]} />
        <meshStandardMaterial color="#8a6f52" />
      </mesh>
      <mesh position={[0, 1.0, 0]}>
        <coneGeometry args={[0.62, 1.2, 8]} />
        <meshStandardMaterial color={accent} flatShading />
      </mesh>
      <mesh position={[0, 1.7, 0]}>
        <coneGeometry args={[0.4, 0.8, 8]} />
        <meshStandardMaterial color={accent} flatShading />
      </mesh>
    </group>
  );
}

function Rock({ accent, scale }: { accent: string; scale: number }) {
  return (
    <mesh position={[0, 0.3 * scale, 0]} scale={scale} rotation={[0.3, 0.8, 0.1]}>
      <dodecahedronGeometry args={[0.5, 0]} />
      <meshStandardMaterial color={accent} flatShading />
    </mesh>
  );
}

function Crystal({ accent, scale }: { accent: string; scale: number }) {
  return (
    <mesh position={[0, 0.55 * scale, 0]} scale={[scale * 0.7, scale * 1.4, scale * 0.7]} rotation={[0, 0.6, 0]}>
      <octahedronGeometry args={[0.45, 0]} />
      <meshStandardMaterial color={accent} flatShading emissive={accent} emissiveIntensity={0.25} />
    </mesh>
  );
}

const DECO_COMPONENTS: Record<DecoKind, React.ComponentType<{ accent: string; scale: number }>> = {
  tree: Tree,
  rock: Rock,
  crystal: Crystal,
};

// ---------- 지역 랜드마크 ----------

const FLOWER_SPOTS: [number, number, string][] = [
  [-1.2, 0.5, '#ff8fab'],
  [0.8, 1.1, '#ffd166'],
  [1.5, -0.6, '#ffffff'],
  [-0.3, -1.3, '#c77dff'],
  [0.2, 0.2, '#ff6b6b'],
  [-1.7, -0.5, '#ffd166'],
];

function Flowerbed() {
  return (
    <group>
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, 0.03, 0]}>
        <circleGeometry args={[2.4, 24]} />
        <meshStandardMaterial color="#a8dd6a" />
      </mesh>
      {FLOWER_SPOTS.map(([x, z, color], i) => (
        <group key={i} position={[x, 0, z]}>
          <mesh position={[0, 0.17, 0]}>
            <cylinderGeometry args={[0.03, 0.03, 0.34, 6]} />
            <meshStandardMaterial color="#3e7d3a" />
          </mesh>
          <mesh position={[0, 0.42, 0]}>
            <sphereGeometry args={[0.15, 10, 10]} />
            <meshStandardMaterial color={color} />
          </mesh>
        </group>
      ))}
    </group>
  );
}

function Palm({ tilt }: { tilt: number }) {
  return (
    <group rotation={[0, 0, tilt]}>
      <mesh position={[0, 0.75, 0]}>
        <cylinderGeometry args={[0.09, 0.15, 1.5, 8]} />
        <meshStandardMaterial color="#8a6f52" />
      </mesh>
      <mesh position={[0, 1.62, 0]} scale={[1, 0.45, 1]}>
        <sphereGeometry args={[0.6, 10, 10]} />
        <meshStandardMaterial color="#3e9b4f" flatShading />
      </mesh>
    </group>
  );
}

function Oasis() {
  return (
    <group>
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, 0.04, 0]}>
        <circleGeometry args={[2.2, 24]} />
        <meshStandardMaterial color="#4cc9f0" />
      </mesh>
      <group position={[-1.5, 0, -1.6]}><Palm tilt={0.16} /></group>
      <group position={[1.8, 0, 1.0]}><Palm tilt={-0.2} /></group>
    </group>
  );
}

// 필드 왼쪽 가장자리를 따라 흐르는 바다 (이동 범위 밖, 시각 전용)
function Sea() {
  return (
    <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, 0.05, 0]}>
      <planeGeometry args={[7, FIELD_BOUND * 2 + 6]} />
      <meshStandardMaterial color="#2f9ec7" transparent opacity={0.92} />
    </mesh>
  );
}

function AncientTree({ accent }: { accent: string }) {
  return (
    <group>
      <mesh position={[0, 1.4, 0]}>
        <cylinderGeometry args={[0.5, 0.72, 2.8, 9]} />
        <meshStandardMaterial color="#7a5230" />
      </mesh>
      <mesh position={[0, 3.0, 0]}>
        <coneGeometry args={[2.3, 2.1, 9]} />
        <meshStandardMaterial color={accent} flatShading />
      </mesh>
      <mesh position={[0, 4.2, 0]}>
        <coneGeometry args={[1.6, 1.7, 9]} />
        <meshStandardMaterial color={accent} flatShading />
      </mesh>
      <mesh position={[0, 5.2, 0]}>
        <coneGeometry args={[1.0, 1.4, 9]} />
        <meshStandardMaterial color={accent} flatShading />
      </mesh>
    </group>
  );
}

function Snowman() {
  return (
    <group>
      {[
        [0.85, 0.85],
        [0.6, 2.0],
        [0.42, 2.85],
      ].map(([r, y], i) => (
        <mesh key={i} position={[0, y, 0]}>
          <sphereGeometry args={[r, 14, 14]} />
          <meshStandardMaterial color="#ffffff" />
        </mesh>
      ))}
      <mesh position={[0, 2.9, 0.42]} rotation={[Math.PI / 2, 0, 0]}>
        <coneGeometry args={[0.08, 0.4, 8]} />
        <meshStandardMaterial color="#ff8c42" />
      </mesh>
      {[-0.13, 0.13].map((x) => (
        <mesh key={x} position={[x, 3.02, 0.36]}>
          <sphereGeometry args={[0.05, 8, 8]} />
          <meshStandardMaterial color="#20242c" />
        </mesh>
      ))}
    </group>
  );
}

const FIREFLY_SPOTS: [number, number, number][] = [
  [-1.2, 0.6, 1.0],
  [0.8, -0.9, 1.3],
  [1.7, 1.2, 0.8],
  [-0.5, -1.8, 1.5],
  [0.1, 1.9, 1.1],
];

function Pond() {
  return (
    <group>
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, 0.04, 0]}>
        <circleGeometry args={[2.6, 24]} />
        <meshStandardMaterial color="#2f4858" />
      </mesh>
      {FIREFLY_SPOTS.map(([x, z, y], i) => (
        <mesh key={i} position={[x, y, z]}>
          <sphereGeometry args={[0.09, 8, 8]} />
          <meshStandardMaterial color="#ffe066" emissive="#ffe066" emissiveIntensity={2} />
        </mesh>
      ))}
    </group>
  );
}

function Volcano() {
  return (
    <group>
      <mesh position={[0, 2.2, 0]}>
        <cylinderGeometry args={[1.1, 3.4, 4.4, 9]} />
        <meshStandardMaterial color="#5a3a35" flatShading />
      </mesh>
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, 4.42, 0]}>
        <circleGeometry args={[1.0, 12]} />
        <meshStandardMaterial color="#ff5a2a" emissive="#ff5a2a" emissiveIntensity={1.2} />
      </mesh>
    </group>
  );
}

const PILLAR_SPOTS: [number, number][] = [
  [-3.5, 0],
  [3.5, 0],
  [-3.5, -3],
  [3.5, -3],
];

function Pillars() {
  return (
    <group>
      {PILLAR_SPOTS.map(([x, z], i) => (
        <group key={i} position={[x, 0, z]}>
          <mesh position={[0, 2.1, 0]}>
            <cylinderGeometry args={[0.45, 0.55, 4.2, 10]} />
            <meshStandardMaterial color="#8f93b8" />
          </mesh>
          <mesh position={[0, 4.35, 0]}>
            <boxGeometry args={[1.3, 0.35, 1.3]} />
            <meshStandardMaterial color="#a9adcc" />
          </mesh>
        </group>
      ))}
    </group>
  );
}

function Landmark({ kind, theme }: { kind: LandmarkKind; theme: RegionTheme }) {
  switch (kind) {
    case 'flowerbed': return <Flowerbed />;
    case 'oasis': return <Oasis />;
    case 'sea': return <Sea />;
    case 'ancientTree': return <AncientTree accent={theme.accent} />;
    case 'snowman': return <Snowman />;
    case 'pond': return <Pond />;
    case 'volcano': return <Volcano />;
    case 'pillars': return <Pillars />;
  }
}

interface TerrainProps {
  region: RegionDef;
}

export function Terrain({ region }: TerrainProps) {
  const Deco = DECO_COMPONENTS[region.deco];
  const size = FIELD_BOUND * 2 + 6;
  return (
    <group>
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, 0, 0]}>
        <planeGeometry args={[size, size]} />
        <meshStandardMaterial color={region.theme.ground} />
      </mesh>
      {region.decoItems.map(([x, z, scale], i) => (
        <group key={i} position={[x, 0, z]}>
          <Deco accent={region.theme.accent} scale={scale} />
        </group>
      ))}
      <group position={[region.landmark.pos[0], 0, region.landmark.pos[1]]}>
        <Landmark kind={region.landmark.kind} theme={region.theme} />
      </group>
    </group>
  );
}
