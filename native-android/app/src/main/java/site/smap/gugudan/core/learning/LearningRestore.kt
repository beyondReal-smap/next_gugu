package site.smap.gugudan.core.learning

import site.smap.gugudan.core.Problems

// 서버 학습 기록 → 기기 오답 가중치(wrongPool) 복원 (iOS LearningRestore.swift 이식).
//
// 별·레벨·코인은 기기에만 두고, 문제별로 의미가 있는 값인 wrongPool 만 서버 기록으로 채운다
// (약한 문제·복습 큐·출제 가중이 되살아난다).

object LearningRestore {
    /**
     * 서버 누적 통계를 오답 가중치(0~6)로 바꾼다.
     * 기기 규칙(오답 +2, 정답 −1, 상한 6)을 흉내 낸다 — 서버엔 순서가 없어 누적값으로 근사한다.
     */
    fun weight(stats: LearningFactStats): Int =
        (2 * stats.wrong - stats.correct).coerceIn(0, 6)

    /**
     * 문제마다 기기 값과 서버 값 중 큰 쪽을 남긴다.
     *
     * 기기 wrongPool 엔 시각이 없어 "최신 우선"은 할 수 없고, 양쪽이 서로 모르는 기록을 갖고 있다
     * (익명 시절 기기 학습 / 다른 기기 학습). max 는 여러 번 적용해도 결과가 같고,
     * 틀려도 복습 문제가 조금 늘어날 뿐이다. 형식이 깨졌거나 출제 범위 밖의 키는 버린다.
     */
    fun merge(local: Map<String, Int>, server: Map<String, LearningFactStats>): Map<String, Int> {
        val merged = local.toMutableMap()
        for ((key, stats) in server) {
            if (Problems.parseKey(key) == null) continue
            val restored = weight(stats)
            if (restored <= 0) continue
            merged[key] = maxOf(merged[key] ?: 0, restored)
        }
        return merged
    }
}
