// content/learning-path.json steps 순서 (읽기 전용 사본)
export const ROADMAP_TABLES = [2, 5, 3, 4, 6, 9, 7, 8];

// 오답 힌트 — content/learning-path.json steps[].keyIdea 의 읽기 전용 사본
// (content/ 는 다른 워커 담당이라 파일을 수정·직접 import 하지 않는다)
const KEY_IDEAS: Record<number, string> = {
  2: '두 배로 생각하기',
  3: '2단에 한 번 더 더하기',
  4: '두 배를 두 번',
  5: '답이 5 또는 0으로 끝남 / 시계 눈금',
  6: '3단의 두 배 / 5단에서 한 묶음 더',
  7: '5단 + 2단으로 쪼개기 / 56 = 7 × 8',
  8: '두 배를 세 번 / 10배에서 2배 빼기',
  9: '10배에서 한 번 빼기 / 손가락 셈',
};

const FALLBACK = '천천히 다시 생각해 봐요';

export function hintForTable(table: number): string {
  return KEY_IDEAS[table] ?? FALLBACK;
}

// content/learning-path.json parentTips 읽기 전용 사본
const PARENT_TIPS = [
  '틀린 문제를 지적하기보다 \'어떻게 그 답이 나왔는지\' 물어보세요. 오답의 경로를 알면 고칠 지점이 정확해집니다.',
  '속도를 먼저 요구하지 마세요. 정확도가 안정된 뒤에 속도를 올리는 순서라야 잘못된 답이 굳지 않습니다.',
  '하루 분량은 한 단으로 제한하는 편이 좋습니다. 여러 단을 한꺼번에 하면 값이 비슷한 식끼리 서로 간섭합니다.',
  '잠들기 전 5분 복습은 기억 정착에 특히 효과가 좋습니다.',
];

export function parentTipOfWeek(now: Date = new Date()): string {
  const idx = Math.floor(now.getTime() / 86_400_000) % PARENT_TIPS.length;
  return PARENT_TIPS[idx];
}
