// 서버 복습 계획 → 로컬 Problem. 파싱 실패 항목은 건너뛰고 로컬 출제에 맡긴다.
import { Problem } from '../types';
import { PracticePlanItem, SelectionReason } from './remote';

export interface PlannedItem {
  problem: Problem;
  reason: SelectionReason;
}

export function factIdToProblem(factId: string): Problem | null {
  const match = /^([2-9])x([1-9])$/.exec(factId);
  if (!match) return null;
  return { a: Number(match[1]), b: Number(match[2]) };
}

export function plannedItemsFrom(
  items: PracticePlanItem[],
  table: number | null
): PlannedItem[] {
  const out: PlannedItem[] = [];
  for (const item of items) {
    const problem = factIdToProblem(item.factId);
    if (!problem) continue;
    if (table != null && problem.a !== table) continue;
    out.push({ problem, reason: item.selectionReason });
  }
  return out;
}
