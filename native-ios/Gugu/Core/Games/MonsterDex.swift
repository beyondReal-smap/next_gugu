import Foundation

// B 몬스터 도감 규칙 (Unity 시험판 Core/MonsterDex.cs 이식)
// 몬스터는 8종이고 처음 4종은 바로 나오며, 누적 처치가 기준을 넘을 때마다 새 종이 하나씩 해금된다.
// 다음에 나올 몬스터: "해금됐지만 아직 도감에 없는 종"이 있으면 그 종을 먼저(새 몬스터를 바로 만나게),
// 다 만났으면 해금된 종을 차례로 돌린다(같은 종이 연달아 나오지 않게).
// 도감 = 한 번이라도 물리친 종(비트 묶음, 비트 위치 = rawValue). 수치는 아이 관찰 전 초안이다.

/// 몬스터 종 — 순서는 해금 순서이자 저장 비트 위치라 바꾸지 말 것. 이름은 그림 파일(monster_<색>)과 같다
enum MonsterKind: Int, CaseIterable {
    case green, orange, purple, sky   // 처음부터 열림
    case pink, red, night, gold       // 누적 처치 10·25·45·70 에 해금

    /// 도감 비트
    var bit: Int { 1 << rawValue }
}

enum MonsterDex {
    static let kinds = MonsterKind.allCases.count

    /// 종마다 해금되는 누적 처치 수(앞 종보다 작지 않음 — 해금된 종은 항상 0번부터 이어진 앞쪽)
    private static let unlockAtDefeats = [0, 0, 0, 0, 10, 25, 45, 70]

    static func unlockAt(_ kind: MonsterKind) -> Int {
        unlockAtDefeats[kind.rawValue]
    }

    static func isUnlocked(_ kind: MonsterKind, totalDefeated: Int) -> Bool {
        totalDefeated >= unlockAt(kind)
    }

    static func unlockedCount(totalDefeated: Int) -> Int {
        precondition(totalDefeated >= 0, "누적 처치는 0 이상이어야 함: \(totalDefeated)")
        var n = 0
        while n < kinds && totalDefeated >= unlockAtDefeats[n] { n += 1 }
        return n
    }

    static func isSeen(_ kind: MonsterKind, seenMask: Int) -> Bool {
        seenMask & kind.bit != 0
    }

    static func markSeen(_ kind: MonsterKind, seenMask: Int) -> Int {
        seenMask | kind.bit
    }

    static func seenCount(_ seenMask: Int) -> Int {
        MonsterKind.allCases.filter { isSeen($0, seenMask: seenMask) }.count
    }

    /// spawnIndex: 순환 위치(0부터 — 게임은 누적 처치 수를 넣어 판을 넘어 이어 돈다),
    /// previous: 바로 앞 몬스터(판의 첫 몬스터면 nil)
    static func nextKind(totalDefeated: Int, seenMask: Int, spawnIndex: Int, previous: MonsterKind?) -> MonsterKind {
        precondition(spawnIndex >= 0, "순환 위치는 0 이상이어야 함: \(spawnIndex)")
        let unlocked = unlockedCount(totalDefeated: totalDefeated)
        for kind in MonsterKind.allCases.prefix(unlocked) where !isSeen(kind, seenMask: seenMask) {
            return kind
        }
        var index = spawnIndex % unlocked
        // 새 종이 해금되며 돌리는 개수가 바뀌면 같은 종이 연달아 나올 수 있음 → 다음 종으로
        if index == previous?.rawValue && unlocked > 1 { index = (index + 1) % unlocked }
        return MonsterKind.allCases[index]
    }

    /// 누적 처치가 before → after 로 늘 때 새로 해금된 종(없으면 nil). 처치는 한 마리씩 늘어 한 번에 최대 1종이다
    static func newlyUnlocked(before: Int, after: Int) -> MonsterKind? {
        precondition(after >= before, "누적 처치가 줄 수 없음 (\(before) → \(after))")
        let a = unlockedCount(totalDefeated: before)
        let b = unlockedCount(totalDefeated: after)
        return b > a ? MonsterKind.allCases[b - 1] : nil
    }
}
