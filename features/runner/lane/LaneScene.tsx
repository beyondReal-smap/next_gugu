"use client";

import { useId } from 'react';
import { HIT_MS, JUDGE_X, laneOffset, leveledUp, type LaneRunnerState } from '@/lib/laneRunner';
import { RunnerArtwork, RunnerBackdrop, RunnerDino, RunnerObstacle } from '../art';

// 레인별 발 기준선(y). 위 레인이 먼 쪽입니다.
const LANE_BASE = [252, 300, 348] as const;
const LANE_TOP = 214;
const LANE_HEIGHT = 48;
const DINO_X = 104;
const TILE_W = 84;

function baseAt(offset: number): number {
  const lower = Math.floor(offset);
  const upper = Math.min(2, lower + 1);
  return LANE_BASE[lower] + (LANE_BASE[upper] - LANE_BASE[lower]) * (offset - lower);
}

export function LaneScene({ game, reducedMotion }: { game: LaneRunnerState; reducedMotion: boolean }) {
  const id = useId().replace(/:/g, '');
  const travel = reducedMotion ? 0 : game.distance * 20;
  const running = game.phase === 'running' && game.hitMs === 0;
  const walking = !reducedMotion && running;
  const stride = walking ? Math.sin(travel / 18) : 0;
  const bob = walking ? Math.abs(stride) * 1.5 : 0;
  const offset = reducedMotion ? game.lane : laneOffset(game);
  const base = baseAt(offset);
  const hurt = game.hitMs > 0 || game.phase === 'over';
  const shifting = !reducedMotion && game.laneAnimMs > 0 ? (game.lane < game.laneFrom ? -6 : 6) : 0;
  const impact = !reducedMotion && game.hitMs > 0 ? Math.sin((HIT_MS - game.hitMs) / 45) * (game.hitMs / HIT_MS) * 3 : 0;
  const answer = game.question ? game.question.a * game.question.b : null;
  const gate = game.gate;
  const reveal = game.outcome === 'correct' || game.outcome === 'wrong';
  const reward = game.outcome === 'correct' && gate ? Math.max(0, Math.min(1, (JUDGE_X - gate.x) / 120)) : 0;
  const readyObstacles = game.phase === 'ready' ? [{ id: -1, lane: 0, x: 470, kind: 'rock' as const }, { id: -2, lane: 2, x: 610, kind: 'stump' as const }] : [];

  return (
    <svg viewBox="0 0 720 360" preserveAspectRatio="xMinYMax slice" className="h-full w-full" aria-hidden="true">
      <RunnerArtwork id={id} />
      <RunnerBackdrop id={id} travel={travel} />
      <path d="M0 212q160-8 360 0t360 0v8H0Z" fill="#b4ca80" />
      <rect y={LANE_TOP} width="720" height="146" fill={`url(#${id}-soil)`} />
      {[0, 1, 2].map((lane) => (
        <rect key={lane} y={LANE_TOP + lane * LANE_HEIGHT} width="720" height={LANE_HEIGHT} fill={lane === game.lane && game.phase === 'running' ? '#fff6d8' : lane % 2 === 0 ? '#ffffff' : '#7a5a3c'} opacity={lane === game.lane && game.phase === 'running' ? 0.22 : 0.06} />
      ))}
      <g stroke="#a98362" strokeWidth="3" strokeLinecap="round" strokeDasharray="22 26" strokeDashoffset={travel % 48} opacity="0.45">
        <path d={`M0 ${LANE_TOP + LANE_HEIGHT}h720M0 ${LANE_TOP + LANE_HEIGHT * 2}h720`} />
      </g>

      {gate && [0, 1, 2].map((lane) => {
        const value = gate.values[lane];
        const correct = reveal && value === answer;
        const chosen = reveal && value === game.given && value !== answer;
        const fill = correct ? '#e3f6c6' : chosen ? '#fbd9c4' : '#fff9e6';
        const stroke = correct ? '#2f8a57' : chosen ? '#c2562b' : '#b98a55';
        const top = LANE_TOP + lane * LANE_HEIGHT + 5;
        return (
          <g key={lane} data-lane-gate={lane} transform={`translate(${gate.x - 10} 0)`} opacity={reveal && !correct && !chosen ? 0.45 : 1}>
            <rect x="0" y={top} width={TILE_W} height={LANE_HEIGHT - 10} rx="10" fill={fill} stroke={stroke} strokeWidth="3" />
            <text x={TILE_W / 2} y={top + 29} textAnchor="middle" fontSize="28" fontWeight="900" fill="#27463a">{value}</text>
          </g>
        );
      })}

      {[0, 1, 2].map((lane) => (
        <g key={lane}>
          {[...readyObstacles, ...game.obstacles].filter((o) => o.lane === lane).map((o) => (
            <g key={o.id} transform={`translate(${o.x} ${LANE_BASE[lane] - 220})`} strokeLinecap="round" strokeLinejoin="round">
              <RunnerObstacle id={id} kind={o.kind} />
            </g>
          ))}
        </g>
      ))}

      {walking && (
        <g fill="#f7e2b6" opacity="0.65">
          {[0, 1, 2].map((i) => {
            const age = ((travel + i * 16) % 48) / 48;
            return <ellipse key={i} cx={DINO_X + 8 - age * 37} cy={base - Math.sin(age * Math.PI) * 5} rx={2 + age * 4} ry={1 + age * 2} opacity={1 - age} />;
          })}
        </g>
      )}
      <ellipse cx={DINO_X + 29} cy={base + 3} rx="24" ry="4" fill="#355f45" opacity="0.23" />
      <g data-runner-character="true" data-lane={game.lane} transform={`translate(${DINO_X + impact} ${base - 63 - bob})`}>
        <RunnerDino id={id} stride={stride} airborne={game.laneAnimMs > 0 && !reducedMotion} hurt={hurt} tilt={shifting} />
      </g>

      {reward > 0 && reward < 1 && !reducedMotion && <text x={DINO_X + 40} y={base - 78 - reward * 25} fill="#316649" stroke="#fff9db" strokeWidth="3" paintOrder="stroke" fontSize="20" fontWeight="900" textAnchor="middle" opacity={1 - reward}>+1</text>}
      {reward > 0 && reward < 1 && leveledUp(game) && <text data-lane-speedup="true" x="360" y={120 - reward * 12} fill="#c2562b" stroke="#fff9db" strokeWidth="5" paintOrder="stroke" fontSize="40" fontWeight="900" textAnchor="middle" opacity={reducedMotion ? 1 : Math.min(1, (1 - reward) * 2)}>속도 UP!</text>}
      {game.hitMs > 0 && <g fill="#f8d170" stroke="#fff7d6" strokeWidth="0.8">{[-1, 0, 1].map((i) => <use key={i} href={`#${id}-sparkle`} transform={`translate(${DINO_X + 32 + i * 19} ${base - 77 - (i === 0 ? 8 : 0)}) rotate(${reducedMotion ? 0 : (HIT_MS - game.hitMs) / 8}) scale(.65)`} />)}</g>}
    </svg>
  );
}
