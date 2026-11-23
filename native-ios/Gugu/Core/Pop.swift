import Foundation

// 구구 팡팡 — 떨어지는 숫자 풍선 중 미션에 맞는 것만 눌러 터뜨린다 (Missile Command식 탭 조준 + 웨이브 미션)
// 순수 규칙만 담는다: 미션·풍선 생성·판정·난이도·체험. 화면 문구는 없다(미션은 enum + 값, 화면이 번역한다).
// 좌표계: x 0~1 = 놀이 영역 가로 비율, y 0~1 = 등장선(0) → 지붕선(1) 진행. 시간 단위는 ms.
// Android core/Pop.kt 와 1:1 미러 — 같은 random 열이면 같은 결과가 나오도록 규칙 계산에는 사칙연산만 쓴다(sin 등 금지).

/// 웨이브 미션 — 화면이 문구로 바꾼다 ("7단 정답만 팡!", "곱해서 36 만들기")
enum PopMission: Equatable {
    /// 배수 사냥 — table 의 배수(table × 1~9)만 터뜨린다
    case multiples(table: Int)
    /// 짝꿍 보스 — 곱해서 target 이 되는 작은 풍선 두 개를 차례로 터뜨린다
    case pairBoss(target: Int)
}

enum PopBalloonKind: String {
    /// 배수 사냥의 보통 풍선 (정답·오답 모두)
    case normal
    /// 황금 정답 풍선 — 터뜨리면 잠깐 느려진다 (웨이브 2부터 웨이브마다 1개)
    case golden
    /// 짝꿍 보스 웨이브의 작은 풍선 — 떨어지지 않고 제자리에 있다
    case small
}

struct PopBalloon: Equatable {
    let id: Int
    let value: Int
    let kind: PopBalloonKind
    /// 미션에 맞는 풍선인지 — 배수 사냥: table 의 배수, 짝꿍 보스: 어떤 짝꿍 쌍의 한쪽
    let correct: Bool
    let x: Double
    var y: Double
}

struct PopBoss: Equatable {
    let value: Int
    /// 남은 짝꿍 수 — 0 이 되면 보스를 물리친다
    var hitsLeft: Int
    /// 0(등장) → 1(지붕선). 1 에 닿으면 하트 -1 후 웨이브가 끝난다
    var y: Double
}

/// 곱해서 보스 수가 되는 짝꿍 (a ≤ b, 둘 다 1~9, b ≥ 2)
struct PopPair: Equatable {
    let a: Int
    let b: Int
}

/// 배수 사냥 웨이브에 내보낼 풍선 순서 (값은 내보내는 순간 화면과 겹치지 않게 정한다)
enum PopSlot: String { case correct, golden, distractor }

/// advance/tap 한 번이 만든 일 — 소리·햅틱·입자 연출용. 다음 호출에서 비워진다
enum PopEvent: Equatable {
    /// 미션 안내가 끝나고 풍선이 움직이기 시작
    case waveStarted(wave: Int)
    /// 정답 풍선을 터뜨림 (points = 이번에 얻은 점수)
    case popped(id: Int, value: Int, points: Int, golden: Bool)
    /// 미션에 안 맞는 풍선을 누름 — 콤보 초기화 + 재장전. 풍선은 그대로 남는다
    case wrongTap(id: Int, value: Int)
    /// 재장전 중이라 탭을 무시함
    case reloading
    /// 정답 풍선이 지붕선에 닿음 — 하트 -1
    case missed(id: Int, value: Int)
    /// 오답 풍선이 지붕선을 그냥 지나감 (감점 없음)
    case passed(id: Int, value: Int)
    /// 보스 웨이브: 짝꿍 첫 번째를 터뜨림 (PopState.pending 에 남는다)
    case bossPick(id: Int, value: Int, points: Int)
    /// 보스 웨이브: 짝꿍을 완성해 보스를 맞힘
    case bossHit(a: Int, b: Int, points: Int, hitsLeft: Int)
    case bossDefeated
    /// 보스가 지붕선에 닿음 — 하트 -1
    case bossEscaped
    case waveCleared(wave: Int)
    /// 황금 풍선으로 느려짐 시작 (Pop.slowDurationMs 동안)
    case slowMoStarted
    /// 하트를 다 써서 판이 끝남
    case gameOver
    /// 무료 체험 웨이브를 다 깨서 판이 끝남
    case trialEnded
}

struct PopState {
    var phase: RunnerPhase
    /// 고른 단 (nil = 섞어서 — 웨이브마다 로드맵 순서로 바뀐다)
    var table: Int?
    /// 지금 웨이브 (1부터)
    var wave: Int
    /// 지금 웨이브의 단 — 섞어서면 Pop.waveTable 이 정한다
    var waveTable: Int
    var mission: PopMission
    /// 미션 안내 남은 시간 — 0 보다 크면 풍선이 움직이지 않고 탭도 받지 않는다
    var introMs: Double
    /// 화면의 풍선 (배수 사냥: 떨어지는 풍선, 보스: 제자리 작은 풍선 6개)
    var balloons: [PopBalloon]
    /// 짝꿍 보스 웨이브에서만 있다
    var boss: PopBoss?
    /// 보스 웨이브에서 먼저 터뜨린 짝꿍 한쪽 ("6 × ?")
    var pending: Int?
    /// 아직 내보내지 않은 풍선 순서 (배수 사냥)
    var queue: [PopSlot]
    /// 다음 풍선까지 남은 시간 (0 이하면 자리가 나는 대로 내보낸다)
    var spawnMs: Double
    /// 이번 웨이브에서 처리한 정답 풍선 수 (터뜨림 + 놓침) — Pop.waveGoal 이 되면 웨이브 끝
    var handled: Int
    var score: Int
    var hearts: Int
    var combo: Int
    var maxCombo: Int
    /// 오답 뒤 재장전 남은 시간 — 0 보다 크면 탭을 무시한다
    var reloadMs: Double
    /// 황금 풍선 느려짐 남은 시간 (실제 시간 기준)
    var slowMs: Double
    var nextId: Int
    /// 결과 화면용 통계
    var pops: Int
    var wrongTaps: Int
    var bossesDefeated: Int
    /// 무료 체험에서 남은 웨이브 수(지금 웨이브 포함). nil 이면 제한 없음, 0 이면 체험을 다 해서 끝난 판
    var trialLeft: Int?
    /// 마지막 advance/tap 이 만든 일
    var events: [PopEvent]
}

enum Pop {
    // MARK: - 규칙 상수

    static let maxHearts = 3
    /// 배수 사냥 웨이브의 정답 풍선 수 — 모두 처리(터뜨림·놓침)하면 웨이브 끝
    static let waveGoal = 6
    /// 웨이브 시작 전 미션 안내 시간
    static let introMs: Double = 1800
    /// 웨이브 3마다 짝꿍 보스
    static let bossEvery = 3
    static let bossFallMs: Double = 20_000
    /// 보스를 물리치는 데 필요한 짝꿍 수 (실기 조정 대상)
    static let bossHits = 3
    static let pointsPerCorrect = 10
    /// 오답 탭 뒤 재장전 시간
    static let reloadMs: Double = 800
    static let goldenFromWave = 2
    static let slowDurationMs: Double = 4000
    /// 느려짐 동안 풍선·보스가 움직이는 비율
    static let slowFactor = 0.5
    /// 이 웨이브까지는 오답을 먼 수에서 먼저 고른다 (첫 웨이브는 쉽게)
    static let farDistractorWaves = 1

    // MARK: - 좌표 상수 (x·탭 반경은 가로 폭 단위)

    /// 떨어지는 풍선이 나오는 세로 줄 수 — 줄 가운데 x = (줄 + 0.5) / lanes
    static let lanes = 5
    /// 같은 줄의 앞 풍선이 이만큼 내려가야 그 줄에 새 풍선을 낸다 (겹침 방지)
    static let laneClearY = 0.3
    static let balloonDiameter = 0.15
    static let bossDiameter = 0.26
    static let bossX = 0.5
    /// 누른 곳에서 풍선 중심까지 이 거리 안이면 맞힌 것으로 본다 (넉넉하게)
    static let tapRadius = 0.11
    /// 가장 가까운 풍선과 거리 차이가 이 안이면 '겹침'으로 보고 지붕에 가까운 풍선을 고른다
    static let tieTolerance = 0.02
    /// 보스 웨이브 작은 풍선 자리 6개 — 가운데로 내려오는 보스와 겹치지 않게 양옆에 둔다
    static let smallSlots: [(x: Double, y: Double)] = [
        (0.11, 0.38), (0.27, 0.58), (0.11, 0.78),
        (0.89, 0.38), (0.73, 0.58), (0.89, 0.78),
    ]

    // MARK: - 난이도

    static func isBossWave(_ wave: Int) -> Bool { wave % bossEvery == 0 }

    /// 고른 단이 있으면 그 단, 섞어서(nil)면 로드맵 순서(2·5·3·4·6·9·7·8)를 웨이브마다 돈다
    static func waveTable(wave: Int, table: Int?) -> Int {
        table ?? Hints.roadmapTables[(wave - 1) % Hints.roadmapTables.count]
    }

    /// 풍선이 등장선에서 지붕선까지 내려오는 시간 — 7초 → 6초 → 웨이브마다 0.5초씩, 하한 3.5초
    static func fallMs(wave: Int) -> Double {
        wave <= 1 ? 7000 : max(3500, 6000 - 500 * Double(wave - 2))
    }

    /// 화면에 동시에 있는 풍선 수 상한 — 3 → 4 → … 최대 6
    static func concurrent(wave: Int) -> Int { min(6, 2 + wave) }

    /// 오답 비율(%) — 40 → 50 → 최대 60
    static func wrongPercent(wave: Int) -> Int { min(60, 30 + 10 * wave) }

    /// 웨이브의 오답 풍선 수 — 정답 6개에 오답 비율을 맞춘다 (40% → 4, 50% → 6, 60% → 9)
    static func distractorCount(wave: Int) -> Int {
        let p = wrongPercent(wave: wave)
        return (waveGoal * p + (100 - p) / 2) / (100 - p)
    }

    /// 콤보 배율 — 콤보 5부터 ×2, 10부터 ×3 (이번 정답까지 센 콤보 기준)
    static func multiplier(combo: Int) -> Int { combo >= 10 ? 3 : (combo >= 5 ? 2 : 1) }

    // MARK: - 짝꿍

    /// n 을 만드는 짝꿍 전부 (a ≤ b, b ≥ 2) — 순서를 바꾼 쌍은 같은 쌍으로 본다.
    /// 짝꿍은 지금 범위의 곱셈식(b단 × a)이어야 한다: 작은 수 a ≤ 곱하는 수 상한, 큰 수 b ≤ 최대 단.
    /// 상한이 9여도 19단 보스(19 × 5 = 95)의 짝꿍 (5, 19)가 남는다
    static func pairs(of n: Int) -> [PopPair] {
        (Problems.minB...Problems.maxB).compactMap { a in
            guard n % a == 0 else { return nil }
            let b = n / a
            return b >= a && b <= Problems.maxTable && b >= 2 ? PopPair(a: a, b: b) : nil
        }
    }

    /// 보스 웨이브 작은 풍선에 쓰는 수 — 짝꿍의 큰 수가 최대 단까지 가므로 1~최대 단
    private static var smallRange: ClosedRange<Int> { Problems.minB...Problems.maxTable }

    /// v 가 target 의 어떤 짝꿍 한쪽인지
    static func isPairMember(_ v: Int, target: Int) -> Bool {
        pairs(of: target).contains { $0.a == v || $0.b == v }
    }

    /// 보스 수 — 그 웨이브 단 × 2~9 (단과 곱하는 수의 짝꿍이 반드시 있다)
    static func bossTarget(table: Int, random: () -> Double) -> Int {
        table * (2 + pick(Problems.maxB - 1, random))
    }

    // MARK: - 풍선 값

    /// 오답 후보 — 모두 1~81, table 의 배수가 아니고, 서로 겹치지 않는다 (각각 오름차순)
    struct DistractorPool: Equatable {
        /// 배수 ±1 (63 → 62, 64)
        let near: [Int]
        /// 이웃 단의 곱 (7단이면 6단·8단의 곱)
        let neighbour: [Int]
        /// 배수의 숫자 뒤집기 (56 → 65)
        let reversal: [Int]
        /// 그 밖의 먼 수
        let far: [Int]
    }

    static func distractorPool(table t: Int) -> DistractorPool {
        func ok(_ v: Int) -> Bool { v >= 1 && v <= Problems.maxTable * Problems.maxB && v % t != 0 }
        func uniqueSorted(_ xs: [Int]) -> [Int] { Array(Set(xs)).sorted() }
        let multiples = (Problems.minB...Problems.maxB).map { t * $0 }
        let near = uniqueSorted(multiples.flatMap { [$0 - 1, $0 + 1] }.filter(ok))
        let neighbour = uniqueSorted([t - 1, t + 1]
            .filter { (Problems.minTable...Problems.maxTable).contains($0) }
            .flatMap { n in (Problems.minB...Problems.maxB).map { n * $0 } }
            .filter { ok($0) && !near.contains($0) })
        let reversal = uniqueSorted(multiples
            .filter { $0 >= 10 && $0 % 10 != 0 }
            .compactMap { Int(String(String($0).reversed())) }
            .filter { ok($0) && !near.contains($0) && !neighbour.contains($0) })
        let far = (1...(Problems.maxTable * Problems.maxB)).filter { ok($0) && !near.contains($0) && !neighbour.contains($0) && !reversal.contains($0) }
        return DistractorPool(near: near, neighbour: neighbour, reversal: reversal, far: far)
    }

    /// 오답 값 — 첫 웨이브는 먼 수, 그 뒤는 헷갈리는 수(근접 3 : 이웃 단 2 : 뒤집기 1)를 먼저. exclude(화면의 값)와 겹치지 않는다
    static func distractor(table: Int, wave: Int, exclude: [Int], random: () -> Double) -> Int {
        let pool = distractorPool(table: table)
        let far = pool.far.filter { !exclude.contains($0) }
        if wave <= farDistractorWaves && !far.isEmpty { return far[pick(far.count, random)] }

        let tiers: [(values: [Int], weight: Int)] = [(pool.near, 3), (pool.neighbour, 2), (pool.reversal, 1)]
            .map { (values: $0.0.filter { !exclude.contains($0) }, weight: $0.1) }
            .filter { !$0.values.isEmpty }
        if !tiers.isEmpty {
            var r = pick(tiers.reduce(0) { $0 + $1.weight }, random)
            for tier in tiers {
                if r < tier.weight { return tier.values[pick(tier.values.count, random)] }
                r -= tier.weight
            }
        }
        // 화면 풍선은 많아야 6개라 먼 수가 바닥나는 일은 없다
        precondition(!far.isEmpty, "오답 후보 없음: \(table)단")
        return far[pick(far.count, random)]
    }

    /// 정답 값 — table × 1~9 중 화면에 없는 것
    static func correctValue(table: Int, exclude: [Int], random: () -> Double) -> Int {
        let options = (Problems.minB...Problems.maxB).map { table * $0 }.filter { !exclude.contains($0) }
        precondition(!options.isEmpty, "정답 후보 없음: \(table)단")
        return options[pick(options.count, random)]
    }

    /// 보스 웨이브 작은 풍선 6개 값 — 짝꿍 한 쌍 + 어떤 짝꿍에도 안 드는 수 4개, 자리는 섞는다
    static func smallValues(target: Int, random: () -> Double) -> [Int] {
        let all = pairs(of: target)
        let pair = all[pick(all.count, random)]
        let others = shuffled(smallRange.filter { !isPairMember($0, target: target) }, random)
        // 1~9 중 짝꿍에 드는 수는 많아야 4개(예: 12 → 2·3·4·6)라 남는 수가 늘 5개 이상이다
        precondition(others.count >= smallSlots.count - 2, "보스 \(target): 오답 후보 부족")
        return shuffled([pair.a, pair.b] + others.prefix(smallSlots.count - 2), random)
    }

    // MARK: - 생성

    /// trial: 무료 체험 웨이브 수 (nil = 제한 없음, 보통 PremiumConfig.popTrialWaves)
    static func create(table: Int?, phase: RunnerPhase = .running, trial: Int? = nil,
                       random: () -> Double = { Double.random(in: 0..<1) }) -> PopState {
        precondition(table.map { (Problems.minTable...Problems.maxTable).contains($0) } ?? true, "단 범위 밖: \(table!)")
        var s = PopState(
            phase: phase, table: table, wave: 0, waveTable: waveTable(wave: 1, table: table),
            mission: .multiples(table: waveTable(wave: 1, table: table)), introMs: 0,
            balloons: [], boss: nil, pending: nil, queue: [], spawnMs: 0, handled: 0,
            score: 0, hearts: maxHearts, combo: 0, maxCombo: 0, reloadMs: 0, slowMs: 0, nextId: 1,
            pops: 0, wrongTaps: 0, bossesDefeated: 0, trialLeft: trial, events: []
        )
        startWave(&s, wave: 1, random: random)
        return s
    }

    // MARK: - 진행

    /// dt(ms) 만큼 진행 — 미션 안내 → 풍선 이동·지붕선 판정 → 웨이브 끝 → 새 풍선
    static func advance(_ state: PopState, dt: Double, random: () -> Double = { Double.random(in: 0..<1) }) -> PopState {
        var s = state
        s.events = []
        guard s.phase == .running, dt.isFinite, dt > 0 else { return s }

        s.reloadMs = max(0, s.reloadMs - dt)
        let worldDt = s.slowMs > 0 ? dt * slowFactor : dt
        s.slowMs = max(0, s.slowMs - dt)

        if s.introMs > 0 {
            s.introMs = max(0, s.introMs - dt)
            if s.introMs == 0 { s.events.append(.waveStarted(wave: s.wave)) }
            return s
        }

        if var boss = s.boss {
            boss.y = min(1, boss.y + worldDt / bossFallMs)
            s.boss = boss
            if boss.y >= 1 {
                s.events.append(.bossEscaped)
                loseHeart(&s)
                if s.phase == .running { clearWave(&s, random: random) }
            }
            return s
        }

        let fall = fallMs(wave: s.wave)
        var kept: [PopBalloon] = []
        for var balloon in s.balloons {
            balloon.y += worldDt / fall
            if balloon.y < 1 {
                kept.append(balloon)
            } else if balloon.correct {
                s.events.append(.missed(id: balloon.id, value: balloon.value))
                s.handled += 1
                loseHeart(&s)
            } else {
                s.events.append(.passed(id: balloon.id, value: balloon.value))
            }
        }
        s.balloons = kept
        guard s.phase == .running else { return s }
        if s.handled >= waveGoal {
            clearWave(&s, random: random)
            return s
        }

        s.spawnMs -= worldDt
        if s.spawnMs <= 0 {
            s.spawnMs = 0
            spawn(&s, random: random)
        }
        return s
    }

    // MARK: - 탭

    /// 누른 곳(x 0~1, y 0~1)에서 맞힐 풍선 id — 반경 안에서 가장 가까운 풍선, 겹치면 지붕에 가까운(y 큰) 풍선, 그다음 id 작은 풍선.
    /// aspect = 등장선~지붕선 높이 ÷ 놀이 영역 폭 (y 차이를 가로 폭 단위로 바꾼다). 보스는 대상이 아니다.
    static func target(_ state: PopState, x: Double, y: Double, aspect: Double) -> Int? {
        guard x.isFinite, y.isFinite, aspect.isFinite, aspect > 0 else { return nil }
        let inRange: [(balloon: PopBalloon, distance: Double)] = state.balloons.compactMap { b in
            let dx = b.x - x
            let dy = (b.y - y) * aspect
            let d = (dx * dx + dy * dy).squareRoot()
            return d <= tapRadius ? (b, d) : nil
        }
        guard let nearest = inRange.map(\.distance).min() else { return nil }
        let ties = inRange.filter { $0.distance <= nearest + tieTolerance }.map(\.balloon)
        return ties.min { a, b in a.y != b.y ? a.y > b.y : a.id < b.id }?.id
    }

    /// 누른 곳으로 탭 — 맞힐 풍선이 없으면 아무 일도 없다
    static func tap(_ state: PopState, x: Double, y: Double, aspect: Double,
                    random: () -> Double = { Double.random(in: 0..<1) }) -> PopState {
        guard let id = target(state, x: x, y: y, aspect: aspect) else {
            var s = state
            s.events = []
            return s
        }
        return tap(state, id: id, random: random)
    }

    /// 풍선 id 로 탭 — 미션 안내 중·재장전 중이면 무시
    static func tap(_ state: PopState, id: Int, random: () -> Double = { Double.random(in: 0..<1) }) -> PopState {
        var s = state
        s.events = []
        guard s.phase == .running, s.introMs == 0,
              let index = s.balloons.firstIndex(where: { $0.id == id }) else { return s }
        if s.reloadMs > 0 {
            s.events.append(.reloading)
            return s
        }
        let balloon = s.balloons[index]

        if let boss = s.boss {
            tapSmall(&s, index: index, boss: boss, random: random)
            return s
        }
        guard balloon.correct else {
            wrongTap(&s, balloon)
            return s
        }
        s.balloons.remove(at: index)
        let points = award(&s)
        s.handled += 1
        let golden = balloon.kind == .golden
        s.events.append(.popped(id: balloon.id, value: balloon.value, points: points, golden: golden))
        if golden {
            s.slowMs = slowDurationMs
            s.events.append(.slowMoStarted)
        }
        if s.handled >= waveGoal { clearWave(&s, random: random) }
        return s
    }

    // MARK: - 기록

    /// 단별 최고 점수 저장 키 ("all" = 섞어서, "2"~"9") — Persistence.popBestKey 아래 [키: 점수]
    static func bestKey(_ table: Int?) -> String { table.map(String.init) ?? "all" }

    /// 저장본에서 알 수 없는 키·음수 점수를 걸러 낸다 (다른 미니게임 loadBests 와 같은 규칙)
    static func sanitizeBests(_ raw: [String: Int]) -> [String: Int] {
        let allowed = Set(["all"] + (Problems.minTable...Problems.maxTable).map(String.init))
        return raw.filter { allowed.contains($0.key) && $0.value >= 0 }
    }

    // MARK: - 내부

    /// 0..<count 중 하나 (random 이 1 에 아주 가까워도 범위를 넘지 않게)
    private static func pick(_ count: Int, _ random: () -> Double) -> Int {
        min(Int(random() * Double(count)), count - 1)
    }

    /// Fisher–Yates (Runner.shuffled 와 같은 순서로 random 을 쓴다)
    private static func shuffled<T>(_ values: [T], _ random: () -> Double) -> [T] {
        var result = values
        var i = result.count - 1
        while i > 0 {
            let j = Int(random() * Double(i + 1))
            result.swapAt(i, min(j, i))
            i -= 1
        }
        return result
    }

    private static func laneX(_ lane: Int) -> Double { (Double(lane) + 0.5) / Double(lanes) }

    private static func startWave(_ s: inout PopState, wave: Int, random: () -> Double) {
        s.wave = wave
        s.waveTable = waveTable(wave: wave, table: s.table)
        s.introMs = introMs
        s.handled = 0
        s.pending = nil
        s.spawnMs = 0
        if isBossWave(wave) {
            let target = bossTarget(table: s.waveTable, random: random)
            s.mission = .pairBoss(target: target)
            s.boss = PopBoss(value: target, hitsLeft: bossHits, y: 0)
            s.queue = []
            let smalls = makeSmalls(&s, target: target, random: random)
            s.balloons = smalls
        } else {
            s.mission = .multiples(table: s.waveTable)
            s.boss = nil
            var slots = Array(repeating: PopSlot.correct, count: waveGoal)
                + Array(repeating: PopSlot.distractor, count: distractorCount(wave: wave))
            if wave >= goldenFromWave { slots[0] = .golden }
            s.queue = shuffled(slots, random)
            s.balloons = []
        }
    }

    private static func makeSmalls(_ s: inout PopState, target: Int, random: () -> Double) -> [PopBalloon] {
        let values = smallValues(target: target, random: random)
        return values.enumerated().map { i, value in
            defer { s.nextId += 1 }
            return PopBalloon(id: s.nextId, value: value, kind: .small,
                              correct: isPairMember(value, target: target),
                              x: smallSlots[i].x, y: smallSlots[i].y)
        }
    }

    /// 다음 풍선 하나 — 자리(동시 수·빈 줄)가 없으면 다음 프레임에 다시
    private static func spawn(_ s: inout PopState, random: () -> Double) {
        guard let slot = s.queue.first, s.balloons.count < concurrent(wave: s.wave) else { return }
        let busy = Set(s.balloons.filter { $0.y < laneClearY }.map { min(Int($0.x * Double(lanes)), lanes - 1) })
        let free = (0..<lanes).filter { !busy.contains($0) }
        guard !free.isEmpty else { return }
        let lane = free[pick(free.count, random)]
        let onScreen = s.balloons.map(\.value)
        let value = slot == .distractor
            ? distractor(table: s.waveTable, wave: s.wave, exclude: onScreen, random: random)
            : correctValue(table: s.waveTable, exclude: onScreen, random: random)
        s.balloons.append(PopBalloon(id: s.nextId, value: value, kind: slot == .golden ? .golden : .normal,
                                     correct: slot != .distractor, x: laneX(lane), y: 0))
        s.nextId += 1
        s.queue.removeFirst()
        s.spawnMs = fallMs(wave: s.wave) / Double(concurrent(wave: s.wave))
    }

    /// 보스 웨이브 작은 풍선 탭 — 첫 번째는 짝꿍에 드는 수, 두 번째는 첫 번째와 곱해 보스 수가 되는 수
    private static func tapSmall(_ s: inout PopState, index: Int, boss: PopBoss, random: () -> Double) {
        let balloon = s.balloons[index]
        if let first = s.pending {
            guard first * balloon.value == boss.value else {
                wrongTap(&s, balloon)
                return
            }
            s.balloons.remove(at: index)
            let points = award(&s)
            var hit = boss
            hit.hitsLeft -= 1
            s.boss = hit
            s.pending = nil
            s.events.append(.bossHit(a: first, b: balloon.value, points: points, hitsLeft: hit.hitsLeft))
            if hit.hitsLeft == 0 {
                s.bossesDefeated += 1
                s.events.append(.bossDefeated)
                clearWave(&s, random: random)
            } else {
                // 새 짝꿍 세트 — 매번 완성할 수 있는 쌍이 하나 이상 있다
                let smalls = makeSmalls(&s, target: boss.value, random: random)
                s.balloons = smalls
            }
            return
        }
        guard balloon.correct else {
            wrongTap(&s, balloon)
            return
        }
        s.balloons.remove(at: index)
        let points = award(&s)
        s.pending = balloon.value
        s.events.append(.bossPick(id: balloon.id, value: balloon.value, points: points))
        // 빈자리를 채운다 — 짝이 화면에 없으면 짝을, 있으면 짝꿍에 안 드는 수를
        let present = s.balloons.map(\.value)
        let partner = boss.value / balloon.value
        let value = present.contains(partner) ? filler(target: boss.value, exclude: present, random: random) : partner
        s.balloons.insert(PopBalloon(id: s.nextId, value: value, kind: .small,
                                     correct: isPairMember(value, target: boss.value),
                                     x: balloon.x, y: balloon.y), at: index)
        s.nextId += 1
    }

    /// 빈자리용 수 — 화면에 없는 '짝꿍에 안 드는 수', 없으면 화면에 없는 짝꿍 수, 그것도 없으면 아무 '짝꿍에 안 드는 수'
    private static func filler(target: Int, exclude: [Int], random: () -> Double) -> Int {
        let others = smallRange.filter { !isPairMember($0, target: target) }
        let members = smallRange.filter { isPairMember($0, target: target) }
        let fresh = others.filter { !exclude.contains($0) }
        if !fresh.isEmpty { return fresh[pick(fresh.count, random)] }
        let freshMembers = members.filter { !exclude.contains($0) }
        if !freshMembers.isEmpty { return freshMembers[pick(freshMembers.count, random)] }
        return others[pick(others.count, random)]
    }

    /// 정답 처리 — 콤보를 올리고 배율을 곱한 점수를 더한다
    private static func award(_ s: inout PopState) -> Int {
        s.combo += 1
        s.maxCombo = max(s.maxCombo, s.combo)
        s.pops += 1
        let points = pointsPerCorrect * multiplier(combo: s.combo)
        s.score += points
        return points
    }

    private static func wrongTap(_ s: inout PopState, _ balloon: PopBalloon) {
        s.combo = 0
        s.reloadMs = reloadMs
        s.wrongTaps += 1
        s.events.append(.wrongTap(id: balloon.id, value: balloon.value))
    }

    private static func loseHeart(_ s: inout PopState) {
        s.combo = 0
        guard s.hearts > 0 else { return }
        s.hearts -= 1
        if s.hearts == 0 {
            s.phase = .over
            s.events.append(.gameOver)
        }
    }

    /// 웨이브 끝 — 남은 풍선을 치우고, 체험이 끝났으면 판을 끝내고, 아니면 다음 웨이브
    private static func clearWave(_ s: inout PopState, random: () -> Double) {
        s.events.append(.waveCleared(wave: s.wave))
        s.balloons = []
        s.boss = nil
        s.pending = nil
        s.queue = []
        if let left = s.trialLeft {
            s.trialLeft = left - 1
            if left <= 1 {
                s.phase = .over
                s.events.append(.trialEnded)
                return
            }
        }
        startWave(&s, wave: s.wave + 1, random: random)
    }
}
