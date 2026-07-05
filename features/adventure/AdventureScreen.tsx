"use client";
// 어드벤처 오케스트레이션 — map(지역 선택) → world(3D 탐험) → battle(대결) 상태 머신
import React, { useRef, useState } from 'react';
import { AnimatePresence } from 'framer-motion';
import { NpcDef, RegionDef } from '@/lib/adventure/types';
import { REGIONS, regionFor } from '@/lib/adventure/world';
import { isNpcDefeated, regionStats } from '@/lib/adventure/progress';
import { useAdventure } from '@/lib/state/AdventureProvider';
import { createMoveState, useKeyboardMove } from './world/controls';
import { WorldStage } from './world/WorldStage';
import { Joystick } from './ui/Joystick';
import { WorldHud } from './ui/WorldHud';
import { EncounterPrompt } from './ui/EncounterPrompt';
import { BattleScreen } from './battle/BattleScreen';
import { RegionMap } from './RegionMap';

type Stage =
  | { kind: 'map' }
  | { kind: 'world'; table: number }
  | { kind: 'battle'; table: number; npc: NpcDef; token: number };

export function AdventureScreen({ onExit }: { onExit: () => void }) {
  const { progress } = useAdventure();
  const [stage, setStage] = useState<Stage>({ kind: 'map' });
  const [encounter, setEncounter] = useState<NpcDef | null>(null);

  // 이동 입력/위치는 ref — 배틀을 다녀와도 월드 위치 유지
  const moveRef = useRef(createMoveState());
  const posRef = useRef<[number, number]>([REGIONS[0].spawn[0], REGIONS[0].spawn[1]]);
  useKeyboardMove(moveRef);

  const enterWorld = (region: RegionDef) => {
    posRef.current = [region.spawn[0], region.spawn[1]]; // 지역별 입장 위치
    setEncounter(null);
    setStage({ kind: 'world', table: region.table });
  };

  if (stage.kind === 'map') {
    return <RegionMap onSelect={enterWorld} onExit={onExit} />;
  }

  const region = regionFor(stage.table);
  if (!region) return null;

  if (stage.kind === 'battle') {
    const backToWorld = () => setStage({ kind: 'world', table: stage.table });
    return (
      <BattleScreen
        key={`${stage.npc.id}-${stage.token}`} // 다시 도전 시 재마운트로 내부 ref 초기화
        npc={stage.npc}
        onWorld={backToWorld}
        onRetry={() => setStage({ ...stage, token: stage.token + 1 })}
        onFlee={backToWorld}
      />
    );
  }

  // world
  const stats = regionStats(progress, region);
  const normalsCleared = region.npcs.filter((n) => n.kind === 'normal').every((n) => isNpcDefeated(progress, n.id));
  const startBattle = (npc: NpcDef) => {
    setEncounter(null);
    setStage({ kind: 'battle', table: stage.table, npc, token: 0 });
  };

  return (
    <div className="relative h-full overflow-hidden">
      <WorldStage
        region={region}
        defeatedIds={progress.defeatedNpcs}
        moveRef={moveRef}
        posRef={posRef}
        onEncounter={setEncounter}
      />
      <WorldHud
        region={region}
        stats={stats}
        onBack={() => {
          setEncounter(null);
          setStage({ kind: 'map' });
        }}
      />
      <Joystick moveRef={moveRef} />
      <AnimatePresence>
        {encounter && (
          <EncounterPrompt
            key={encounter.id}
            npc={encounter}
            defeated={isNpcDefeated(progress, encounter.id)}
            bossLocked={encounter.kind === 'boss' && !normalsCleared}
            onBattle={() => startBattle(encounter)}
          />
        )}
      </AnimatePresence>
    </div>
  );
}
