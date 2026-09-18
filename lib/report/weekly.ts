// 부모 주간 리포트 — 로컬 상태에서 GET /api/progress/weekly 와 같은 형태로 파생
import { AdventureProgress } from '../adventure/types';
import { bossIdFor, REGIONS } from '../adventure/world';
import { totalStats } from '../adventure/progress';
import { todayStr } from '../state/commit';
import { GameState, WeeklyReport } from '../types';
import { ROADMAP_TABLES } from '../hints';

function shiftDate(base: Date, days: number): string {
  const d = new Date(base.getFullYear(), base.getMonth(), base.getDate() + days);
  return todayStr(d);
}

export function currentRoadmapStep(mastery: GameState['tableMastery']): number {
  for (let i = 0; i < ROADMAP_TABLES.length; i++) {
    const stars = mastery[ROADMAP_TABLES[i]]?.stars ?? 0;
    if (stars < 1) return i + 1;
  }
  return ROADMAP_TABLES.length;
}

export function buildWeeklyReport(
  state: GameState,
  adventure: AdventureProgress,
  now: Date = new Date()
): WeeklyReport {
  const to = todayStr(now);
  const from = shiftDate(now, -6);
  const log = (state.dayLog ?? []).filter((d) => d.date >= from && d.date <= to);

  let correct = 0;
  let wrong = 0;
  let msSum = 0;
  const missAgg: Record<string, number> = {};
  for (const d of log) {
    correct += d.correct;
    wrong += d.wrong;
    msSum += d.msSum;
    for (const [k, n] of Object.entries(d.misses)) {
      missAgg[k] = (missAgg[k] || 0) + n;
    }
  }

  const daysActive = log.filter((d) => d.correct + d.wrong > 0).length;
  const answered = correct + wrong;
  const avgMs = answered ? Math.round(msSum / answered) : 0;

  const weakKeys = Object.entries(missAgg)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 5)
    .map(([key, misses]) => ({ key, misses }));

  const stars: Record<number, number> = {};
  for (let t = 2; t <= 9; t++) stars[t] = state.tableMastery[t]?.stars ?? 0;

  const totals = totalStats(adventure);
  const bosses = REGIONS.filter((r) => adventure.defeatedNpcs.includes(bossIdFor(r.table))).length;

  return {
    from,
    to,
    daysActive,
    correct,
    wrong,
    avgMs,
    weakKeys,
    stars,
    adventure: { defeated: totals.defeated, total: totals.total, bosses },
    roadmapStep: currentRoadmapStep(state.tableMastery),
  };
}
