package site.smap.gugudan.core.games

// B 몬스터 도감 규칙 (Unity 시험판 Core/MonsterDex.cs 이식, iOS MonsterDex.swift 와 1:1)
// 몬스터는 8종이고 처음 4종은 바로 나오며, 누적 처치가 기준을 넘을 때마다 새 종이 하나씩 해금된다.
// 다음에 나올 몬스터: "해금됐지만 아직 도감에 없는 종"이 있으면 그 종을 먼저(새 몬스터를 바로 만나게),
// 다 만났으면 해금된 종을 차례로 돌린다(같은 종이 연달아 나오지 않게).
// 도감 = 한 번이라도 물리친 종(비트 묶음, 비트 위치 = ordinal). 수치는 아이 관찰 전 초안이다.

/** 몬스터 종 — 순서는 해금 순서이자 저장 비트 위치라 바꾸지 말 것. 이름은 그림 파일(monster_<색>)과 같다 */
enum class MonsterKind {
    // 처음부터 열림
    GREEN, ORANGE, PURPLE, SKY,
    // 누적 처치 10·25·45·70 에 해금
    PINK, RED, NIGHT, GOLD;

    /** 도감 비트 */
    val bit: Int get() = 1 shl ordinal
}

object MonsterDex {
    val KINDS = MonsterKind.entries.size

    /** 종마다 해금되는 누적 처치 수(앞 종보다 작지 않음 — 해금된 종은 항상 0번부터 이어진 앞쪽) */
    private val UNLOCK_AT_DEFEATS = listOf(0, 0, 0, 0, 10, 25, 45, 70)

    fun unlockAt(kind: MonsterKind): Int = UNLOCK_AT_DEFEATS[kind.ordinal]

    fun isUnlocked(kind: MonsterKind, totalDefeated: Int): Boolean = totalDefeated >= unlockAt(kind)

    fun unlockedCount(totalDefeated: Int): Int {
        require(totalDefeated >= 0) { "누적 처치는 0 이상이어야 함: $totalDefeated" }
        var n = 0
        while (n < KINDS && totalDefeated >= UNLOCK_AT_DEFEATS[n]) n += 1
        return n
    }

    fun isSeen(kind: MonsterKind, seenMask: Int): Boolean = (seenMask and kind.bit) != 0

    fun markSeen(kind: MonsterKind, seenMask: Int): Int = seenMask or kind.bit

    fun seenCount(seenMask: Int): Int = MonsterKind.entries.count { isSeen(it, seenMask) }

    /**
     * spawnIndex: 순환 위치(0부터 — 게임은 누적 처치 수를 넣어 판을 넘어 이어 돈다),
     * previous: 바로 앞 몬스터(판의 첫 몬스터면 null)
     */
    fun nextKind(totalDefeated: Int, seenMask: Int, spawnIndex: Int, previous: MonsterKind?): MonsterKind {
        require(spawnIndex >= 0) { "순환 위치는 0 이상이어야 함: $spawnIndex" }
        val unlocked = unlockedCount(totalDefeated)
        for (kind in MonsterKind.entries.take(unlocked)) {
            if (!isSeen(kind, seenMask)) return kind
        }
        var index = spawnIndex % unlocked
        // 새 종이 해금되며 돌리는 개수가 바뀌면 같은 종이 연달아 나올 수 있음 → 다음 종으로
        if (index == previous?.ordinal && unlocked > 1) index = (index + 1) % unlocked
        return MonsterKind.entries[index]
    }

    /** 누적 처치가 before → after 로 늘 때 새로 해금된 종(없으면 null). 처치는 한 마리씩 늘어 한 번에 최대 1종이다 */
    fun newlyUnlocked(before: Int, after: Int): MonsterKind? {
        require(after >= before) { "누적 처치가 줄 수 없음 ($before → $after)" }
        val a = unlockedCount(before)
        val b = unlockedCount(after)
        return if (b > a) MonsterKind.entries[b - 1] else null
    }
}
