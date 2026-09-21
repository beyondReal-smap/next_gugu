import Foundation

// 출제 엔진 — 오답 가중 + 최근 출제 회피 (problems.ts 이식, 풀 SRS 아님)

enum Problems {
    static let minTable = 2
    static let maxTable = 9
    static let minB = 1
    static let maxB = 9

    static func key(_ a: Int, _ b: Int) -> String { "\(a)x\(b)" }

    /// 한 단(table)의 후보 식
    static func candidates(table: Int) -> [Problem] {
        (minB...maxB).map { Problem(a: table, b: $0) }
    }

    /// 혼합(전 범위) 후보
    static func candidatesAll() -> [Problem] {
        var list: [Problem] = []
        for a in minTable...maxTable {
            for b in minB...maxB { list.append(Problem(a: a, b: b)) }
        }
        return list
    }

    /// 가중 무작위 선택. 오답 풀 가중치 ↑, 최근 출제는 가중치 ↓.
    /// random: 0..<1 균등 난수 (테스트 주입 가능, TS Math.random 대응)
    static func pick(
        table: Int?,
        wrongPool: [String: Int],
        recentKeys: [String],
        random: () -> Double = { Double.random(in: 0..<1) }
    ) -> Problem {
        let pool = table == nil ? candidatesAll() : candidates(table: table!)
        let recent = Set(recentKeys.suffix(3))

        let weighted: [(p: Problem, w: Double)] = pool.map { p in
            let k = key(p.a, p.b)
            var w = 1 + Double(wrongPool[k] ?? 0) * 2.2   // 오답 가중
            if recent.contains(k) { w *= 0.15 }           // 직전 출제 회피
            return (p, w)
        }

        let total = weighted.reduce(0) { $0 + $1.w }
        var r = random() * total
        for x in weighted {
            r -= x.w
            if r <= 0 { return x.p }
        }
        return weighted[weighted.count - 1].p
    }

    /// 오답 가중치가 가장 많이 쌓인 단. 없으면 nil.
    static func dominantWrongTable(_ wrongPool: [String: Int]) -> Int? {
        var byTable: [Int: Int] = [:]
        for (key, weight) in wrongPool where weight > 0 {
            guard let sep = key.firstIndex(of: "x"), sep != key.startIndex else { continue }
            guard let table = Int(key[key.startIndex..<sep]), table >= minTable, table <= maxTable else { continue }
            byTable[table] = (byTable[table] ?? 0) + weight
        }
        var best: Int? = nil
        var bestW = 0
        for (t, w) in byTable.sorted(by: { $0.key < $1.key }) where w > bestW {
            bestW = w
            best = t
        }
        return best
    }

    /// 오답 풀 갱신
    static func updateWrongPool(_ pool: [String: Int], a: Int, b: Int, correct: Bool) -> [String: Int] {
        let k = key(a, b)
        let cur = pool[k] ?? 0
        var next = pool
        if correct {
            let v = max(0, cur - 1)
            if v == 0 { next.removeValue(forKey: k) } else { next[k] = v }
        } else {
            next[k] = min(6, cur + 2)
        }
        return next
    }
}

// OX 퀴즈 — 화면에 표시할 식 (맞는 식 또는 그럴듯한 오답)
struct Statement {
    var shown: Int    // 표시되는 곱셈 결과
    var isTrue: Bool  // 실제로 맞는 식인지
}

extension Problems {
    static func makeStatement(
        _ p: Problem,
        random: () -> Double = { Double.random(in: 0..<1) }
    ) -> Statement {
        let answer = p.a * p.b
        if random() < 0.5 { return Statement(shown: answer, isTrue: true) }
        // 흔히 헷갈리는 "한 끗 차이" 오답 후보
        let candidates = [p.a * (p.b + 1), p.a * (p.b - 1), (p.a + 1) * p.b, (p.a - 1) * p.b]
            .filter { $0 > 0 && $0 != answer }
        let idx = Int(random() * Double(candidates.count))
        let shown = candidates[min(idx, candidates.count - 1)]
        return Statement(shown: shown, isTrue: false)
    }
}

/// 세션 정답 슬롯의 자릿수 — 정답 자릿수만큼 폭을 예약, 초과 입력 시 입력 길이 (테스트 스펙)
func sessionAnswerSlotDigits(problem: Problem, mode: GameMode, input: String) -> Int {
    let expected = mode == .missing ? problem.b : problem.a * problem.b
    return max(String(expected).count, input.count)
}
