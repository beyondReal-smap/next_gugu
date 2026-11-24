import Foundation
import Observation

// 게임 문제 순서 선택 기억 — 구구 계단·구구 몬스터마다 마지막으로 고른 [순서대로]·[무작위] (키 gugu.gameOrder.v1)
// 처음(저장본 없음)은 둘 다 순서대로. 보상(gugu.play.v1)·진행도(gugu.progress.v1) 저장본과 키를 나눠
// 이 형식이 바뀌어도 그쪽 구버전 호환에 영향을 주지 않는다.
// 못 읽는 저장본(모르는 순서 값·빠진 항목)은 Persistence 가 원본을 격리하고 nil 을 준다 → 순서대로로 시작
// (GameStore·PlayRewardsStore 와 같은 관례). 시작 때는 저장하지 않고, 시작 창에서 고를 때만 저장한다.

/// 저장본 — 두 게임 값이 모두 있어야 읽힌다(빠진 항목을 기본값으로 메우지 않음)
struct GameOrderChoice: Codable, Equatable {
    var stairs: ProblemOrder = .sequential
    var monster: ProblemOrder = .sequential
}

@Observable
final class GameOrderStore {
    private(set) var saved: GameOrderChoice
    /// DEBUG 훅(GUGU_STAIRS_ORDER·GUGU_MONSTER_ORDER) — 저장된 선택보다 우선하고, 저장하지는 않는다
    @ObservationIgnored private let overrides: [PlayGame: ProblemOrder]
    @ObservationIgnored private let key: String

    /// key 는 테스트가 고유 키를 넘겨 실제 저장본과 섞이지 않게 한다. environment 는 DEBUG 훅을 읽을 곳(테스트는 [:])
    init(key: String = Persistence.gameOrderKey,
         environment: [String: String] = ProcessInfo.processInfo.environment) {
        self.key = key
        saved = Persistence.load(GameOrderChoice.self, key: key) ?? GameOrderChoice()
        #if DEBUG
        overrides = Self.debugOverrides(environment)
        #else
        overrides = [:]
        #endif
    }

    /// 이 게임을 열 때 쓸 순서
    func order(_ game: PlayGame) -> ProblemOrder {
        if let forced = overrides[game] { return forced }
        switch game {
        case .stairs: return saved.stairs
        case .monster: return saved.monster
        }
    }

    /// 시작 창에서 고름 — 바로 저장한다
    func setOrder(_ order: ProblemOrder, for game: PlayGame) {
        switch game {
        case .stairs: saved.stairs = order
        case .monster: saved.monster = order
        }
        Persistence.save(saved, key: key)
    }

    #if DEBUG
    /// SIMCTL_CHILD_GUGU_STAIRS_ORDER·GUGU_MONSTER_ORDER = sequential|random. 잘못된 값은 조용히 넘기지 않고 멈춘다
    static func debugOverrides(_ env: [String: String]) -> [PlayGame: ProblemOrder] {
        var result: [PlayGame: ProblemOrder] = [:]
        for (name, game) in [("GUGU_STAIRS_ORDER", PlayGame.stairs), ("GUGU_MONSTER_ORDER", PlayGame.monster)] {
            guard let raw = env[name] else { continue }
            guard let order = ProblemOrder(rawValue: raw) else {
                preconditionFailure("\(name) 는 \(ProblemOrder.allCases.map(\.rawValue)) 중 하나: \(raw)")
            }
            result[game] = order
        }
        return result
    }
    #endif
}
