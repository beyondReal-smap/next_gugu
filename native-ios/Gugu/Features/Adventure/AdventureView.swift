import SwiftUI

// 어드벤처 오케스트레이션 (AdventureScreen.tsx 이식) — map → world → battle 상태 머신
struct AdventureView: View {
    var onExit: () -> Void
    @Environment(AdventureStore.self) private var adventure
    @Environment(GameStore.self) private var game

    private enum Stage: Equatable {
        case map
        case world(table: Int)
        case battle(table: Int, npc: NpcDef, token: Int)
    }

    @State private var stage: Stage = .map
    @State private var encounter: NpcDef?
    @State private var positionBox = PositionBox(SIMD2(Float(World.regions[0].spawn.x), Float(World.regions[0].spawn.z)))
    // 지역 클리어 연출 — 보스 "첫" 격파 후 월드 복귀 시 1회
    @State private var bossOwnedBeforeBattle = false
    @State private var celebration: RegionDef?
    @State private var celebrationConfetti = 0

    var body: some View {
        switch stage {
        case .map:
            RegionMap(onSelect: enterWorld, onExit: onExit)
        case .world(let table):
            if let region = World.region(for: table) {
                worldStage(region).id(table)   // 포털 이동 시 월드 재구성 보장
            }
        case .battle(let table, let npc, let token):
            if World.region(for: table) != nil {
                BattleView(
                    npc: npc,
                    onWorld: { returnToWorld(table: table, npc: npc) },
                    onRetry: { stage = .battle(table: table, npc: npc, token: token + 1) },
                    onFlee: { stage = .world(table: table) }
                )
                .id("\(npc.id)-\(token)")
            }
        }
    }

    /// 배틀 종료 후 월드 복귀 — 보스를 처음 꺾었다면 클리어 연출 트리거
    private func returnToWorld(table: Int, npc: NpcDef) {
        if npc.kind == .boss, !bossOwnedBeforeBattle,
           AdvProgress.isNpcDefeated(adventure.progress, npc.id),
           let region = World.region(for: table) {
            celebration = region
            celebrationConfetti += 1
            DispatchQueue.main.asyncAfter(deadline: .now() + 3.4) {
                withAnimation(.easeOut(duration: 0.5)) { celebration = nil }
            }
        }
        stage = .world(table: table)
    }

    private func enterWorld(_ region: RegionDef) {
        var spawn = SIMD2(Float(region.spawn.x), Float(region.spawn.z))
        #if DEBUG
        // 테스트 훅 — GUGU_ADV_SPAWN=1이면 첫 NPC 근처에 스폰해 조우/배틀 검증
        if ProcessInfo.processInfo.environment["GUGU_ADV_SPAWN"] == "1", let n = region.npcs.first {
            spawn = SIMD2(Float(n.pos.x), Float(n.pos.z) + 2.2)
        }
        #endif
        positionBox = PositionBox(spawn)
        encounter = nil
        stage = .world(table: region.table)
    }

    @ViewBuilder
    private func worldStage(_ region: RegionDef) -> some View {
        let stats = AdvProgress.regionStats(adventure.progress, region)
        let normalsCleared = region.npcs.filter { $0.kind == .normal }
            .allSatisfy { AdvProgress.isNpcDefeated(adventure.progress, $0.id) }
        // 보스 격파 시 다음 지역 포털 상시 등장
        let nextRegion = stats.bossDefeated ? World.region(for: region.table + 1) : nil

        ZStack(alignment: .bottom) {
            WorldView(
                region: region,
                defeatedIds: Set(adventure.progress.defeatedNpcs),
                positionBox: positionBox,
                playerColorHex: adventure.equippedColorHex,
                playerHatID: adventure.progress.equippedHat,
                portalTo: nextRegion,
                onEncounter: { encounter = $0 },
                onCollectShard: {
                    adventure.collectShard()
                    game.grantXp(2)   // 별 조각 = 소량 XP (통계 미오염)
                    Sound.shared.collect()
                    Haptics.impact(.light)
                },
                onEnterPortal: {
                    guard let next = nextRegion else { return }
                    Haptics.success()
                    Sound.shared.collect()
                    enterWorld(next)
                }
            )
            .ignoresSafeArea()

            WorldHud(region: region, stats: stats, shards: adventure.progress.starShards) {
                encounter = nil
                stage = .map
            }
            .frame(maxHeight: .infinity, alignment: .top)
            .padding(.top, 8)

            // 입장 배너 — 지역명 스플래시
            RegionBanner(region: region)
                .frame(maxHeight: .infinity, alignment: .center)

            // 지역 클리어 연출 — 보스 첫 격파 후 1회
            ConfettiView(trigger: celebrationConfetti).ignoresSafeArea()
            if let cleared = celebration {
                ClearCelebration(region: cleared, next: World.region(for: cleared.table + 1))
                    .frame(maxHeight: .infinity, alignment: .center)
                    .transition(.scale.combined(with: .opacity))
            }

            if let npc = encounter {
                EncounterPrompt(
                    npc: npc,
                    defeated: AdvProgress.isNpcDefeated(adventure.progress, npc.id),
                    bossLocked: npc.kind == .boss && !normalsCleared,
                    onBattle: { startBattle(npc, table: region.table) }
                )
                .padding(.bottom, 168)
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.spring(response: 0.4, dampingFraction: 0.8), value: encounter)
    }

    private func startBattle(_ npc: NpcDef, table: Int) {
        encounter = nil
        bossOwnedBeforeBattle = AdvProgress.isNpcDefeated(adventure.progress, npc.id)
        stage = .battle(table: table, npc: npc, token: 0)
    }
}
