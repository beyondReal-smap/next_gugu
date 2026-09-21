import Foundation
import Observation

// 어드벤처 진행 상태 (AdventureProvider.tsx 이식) — @Observable + UserDefaults

@Observable
final class AdventureStore {
    private(set) var progress: AdventureProgress
    private(set) var loaded: Bool = false
    var open: Bool = false

    init() {
        var p = Persistence.load(AdventureProgress.self, key: Persistence.adventureKey) ?? AdvProgress.defaultProgress
        #if DEBUG
        // 테스트 훅 — 2단 지역 클리어 상태로 시작 (포털/클리어 연출 검증용)
        if ProcessInfo.processInfo.environment["GUGU_CLEAR_R2"] == "1" {
            for n in World.regions[0].npcs where !p.defeatedNpcs.contains(n.id) {
                p.defeatedNpcs.append(n.id)
            }
        }
        #endif
        // 업적 소급 반영 — 도입 전 저장소도 이미 달성한 조건은 조용히 해금
        let backfill = AdvAchievements.newlyUnlocked(p)
        if !backfill.isEmpty { p.achievements.append(contentsOf: backfill) }
        self.progress = p
        self.loaded = true
        persist()
    }

    private func persist() {
        Persistence.save(progress, key: Persistence.adventureKey)
    }

    func openAdventure() { open = true }
    func closeAdventure() { open = false }

    /// 배틀 결과 반영 후, 이번에 새로 해금된 어드벤처 업적 id 반환
    @discardableResult
    func recordBattle(npcId: String, won: Bool) -> [String] {
        let (next, unlocked) = AdvProgress.applyBattle(progress, npcId: npcId, won: won)
        progress = next
        persist()
        return unlocked
    }

    /// 월드에서 별 조각 수집 (잔액 증가)
    func collectShard() {
        progress.starShards += 1
        persist()
    }

    // MARK: - 꾸미기 상점

    /// 장착 중인 색상 hex — 월드 플레이어/배틀 아바타 공용
    var equippedColorHex: String {
        Shop.color(progress.equippedColor)?.hex ?? "#6366f1"
    }

    func ownsColor(_ id: String) -> Bool {
        id == Shop.defaultColorID || progress.ownedColors.contains(id)
    }
    func ownsHat(_ id: String) -> Bool {
        progress.ownedHats.contains(id)
    }

    /// 구매 즉시 장착 (한 번의 탭으로 — 아이 친화)
    @discardableResult
    func buyColor(_ id: String) -> Bool {
        guard let item = Shop.color(id), !ownsColor(id), progress.starShards >= item.price else { return false }
        progress.starShards -= item.price
        progress.ownedColors.append(id)
        progress.equippedColor = id
        persist()
        return true
    }

    @discardableResult
    func buyHat(_ id: String) -> Bool {
        guard let item = Shop.hat(id), !ownsHat(id), progress.starShards >= item.price else { return false }
        progress.starShards -= item.price
        progress.ownedHats.append(id)
        progress.equippedHat = id
        persist()
        return true
    }

    func equipColor(_ id: String) {
        guard ownsColor(id) else { return }
        progress.equippedColor = id
        persist()
    }

    /// nil = 모자 벗기
    func equipHat(_ id: String?) {
        if let id, !ownsHat(id) { return }
        progress.equippedHat = id
        persist()
    }

    func resetAdventure() {
        progress = AdvProgress.defaultProgress
        persist()
    }
}
