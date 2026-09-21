import SwiftUI
import RealityKit

// 위치 유지 박스 — 배틀을 다녀와도 월드 위치 유지 (web posRef 대응)
final class PositionBox {
    var pos: SIMD2<Float>
    init(_ p: SIMD2<Float>) { pos = p }
}

// 어드벤처 3D 월드 (WorldCanvas.tsx 이식) — RealityView + 조이스틱 + 나침반
struct WorldView: View {
    let region: RegionDef
    let defeatedIds: Set<String>
    let positionBox: PositionBox
    var playerColorHex: String = "#6366f1"
    var playerHatID: String? = nil
    var portalTo: RegionDef? = nil   // 보스 격파 후 다음 지역 (북쪽 끝 포털)
    var onEncounter: (NpcDef?) -> Void
    var onCollectShard: () -> Void
    var onEnterPortal: () -> Void = {}

    @State private var runtime = WorldRuntime()

    /// 포털 고정 위치 — 필드 북쪽 끝 중앙
    static let portalPos = SIMD2<Float>(0, -15)

    var body: some View {
        ZStack(alignment: .bottom) {
            Color(hex: region.theme.sky).ignoresSafeArea()

            RealityView { content in
                // 스카이 돔 — 큰 구를 뒤집어 배경색 채움
                let sky = ModelEntity(mesh: .generateSphere(radius: 60), materials: [UnlitMaterial(color: WorldFX.hexColor(region.theme.sky))])
                sky.scale = [-1, 1, 1]  // 내부 면이 보이도록 와인딩 뒤집기
                content.add(sky)

                // 조명 — 키 + 필(앰비언트 근사). SimpleMaterial이 IBL 없이도 방향광에 반응
                let key = DirectionalLight()
                key.light.intensity = 6000
                key.look(at: [0, 0, 0], from: [6, 12, 4], relativeTo: nil)
                content.add(key)
                let fill = DirectionalLight()
                fill.light.intensity = 2600
                fill.look(at: [0, 0, 0], from: [-5, 6, -4], relativeTo: nil)
                content.add(fill)

                // 카메라
                let cam = PerspectiveCamera()
                cam.camera.fieldOfViewInDegrees = 50
                cam.camera.near = 0.5
                cam.camera.far = 120
                content.add(cam)

                // 지형 + 소품 + 랜드마크
                content.add(WorldFX.terrain(region))

                // NPC + 이름 라벨(빌보드 3D 텍스트)
                var handles: [WorldRuntime.NpcHandle] = []
                for npc in region.npcs {
                    let defeated = defeatedIds.contains(npc.id)
                    let bundle = WorldFX.npc(npc, defeated: defeated)
                    bundle.root.addChild(WorldFX.nameLabel(npc, defeated: defeated))
                    content.add(bundle.root)
                    handles.append(WorldRuntime.NpcHandle(def: npc, body: bundle.body, ring: bundle.ring, defeated: defeated))
                }

                // 별 조각 — 방문마다 리스폰 (수집 동선이 탐험 유도)
                var shardEntities: [(entity: Entity, pos: SIMD2<Float>)] = []
                for spot in region.starSpots {
                    let shard = WorldFX.starShard()
                    shard.position = [Float(spot.x), 0.8, Float(spot.z)]
                    content.add(shard)
                    shardEntities.append((shard, SIMD2(Float(spot.x), Float(spot.z))))
                }

                // 다음 지역 포털 — 보스 격파 후 북쪽 끝에 상시 등장
                var portal: (entity: Entity, pos: SIMD2<Float>)? = nil
                if let next = portalTo {
                    let p = WorldFX.portal(to: next)
                    p.position = [Self.portalPos.x, 0, Self.portalPos.y]
                    content.add(p)
                    portal = (p, Self.portalPos)
                }

                // 플레이어 — 상점 장착 색상/모자 반영
                let (proot, pbody) = WorldFX.player(colorHex: playerColorHex, hatID: playerHatID)
                content.add(proot)

                runtime.configure(
                    playerRoot: proot, playerBody: pbody, camera: cam,
                    region: region, startPos: positionBox.pos,
                    npcHandles: handles,
                    shardEntities: shardEntities,
                    portal: portal,
                    onEncounter: onEncounter,
                    onCollectShard: onCollectShard,
                    onEnterPortal: onEnterPortal
                )
                #if DEBUG
                runtime.devAutoWalk = ProcessInfo.processInfo.environment["GUGU_AUTOWALK"] == "1"
                #endif
                runtime.start()
            }
            .ignoresSafeArea()

            // 하단 조작부 — 조이스틱(좌) + 다음 상대 나침반(우)
            HStack(alignment: .bottom) {
                Joystick { x, z in
                    runtime.moveX = x
                    runtime.moveZ = z
                }
                Spacer()
                CompassChip(region: region, defeatedIds: defeatedIds,
                            hasPortal: portalTo != nil, runtime: runtime)
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 24)
        }
        .onDisappear {
            positionBox.pos = runtime.pos
            runtime.stop()
        }
    }
}

// 다음 상대 나침반 — 미격파 주민 → 보스 → (전부 격파 시) 포털 순으로 안내
private struct CompassChip: View {
    let region: RegionDef
    let defeatedIds: Set<String>
    let hasPortal: Bool
    let runtime: WorldRuntime

    private enum Target {
        case npc(NpcDef)
        case portal
    }

    var body: some View {
        TimelineView(.periodic(from: .now, by: 0.2)) { _ in
            if let target = nearestTarget() {
                VStack(spacing: 4) {
                    Image(systemName: icon(target))
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(tint(target))
                        .rotationEffect(.radians(angle(target)))
                    Text(label(target))
                        .font(.suite(.extrabold, 10)).foregroundStyle(.white)
                        .lineLimit(1)
                }
                .frame(width: 74)
                .padding(.vertical, 10)
                .background(.black.opacity(0.3), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            }
        }
    }

    private func nearestTarget() -> Target? {
        let p = runtime.pos
        func d(_ n: NpcDef) -> Float { simd_distance(SIMD2(Float(n.pos.x), Float(n.pos.z)), p) }
        let normals = region.npcs.filter { $0.kind == .normal && !defeatedIds.contains($0.id) }
        let pool = normals.isEmpty
            ? region.npcs.filter { $0.kind == .boss && !defeatedIds.contains($0.id) }
            : normals
        if let npc = pool.min(by: { d($0) < d($1) }) { return .npc(npc) }
        return hasPortal ? .portal : nil
    }

    private func icon(_ t: Target) -> String {
        "location.north.fill"   // 방향 화살표는 공통, 색으로 대상 구분
    }
    private func tint(_ t: Target) -> Color {
        switch t {
        case .npc(let n): return n.kind == .boss ? Color(hex: "#ff5a76") : .white
        case .portal: return Color(hex: "#7dd3fc")
        }
    }
    private func label(_ t: Target) -> String {
        switch t {
        case .npc(let n): return n.kind == .boss ? "보스!" : n.name
        case .portal: return "포털"
        }
    }

    /// 팔로우 카메라 기준 화면 방향 — 화면 위 = 월드 -z, 화면 오른쪽 = 월드 +x
    private func angle(_ t: Target) -> Double {
        let target: SIMD2<Float>
        switch t {
        case .npc(let n): target = SIMD2(Float(n.pos.x), Float(n.pos.z))
        case .portal: target = WorldView.portalPos
        }
        let dx = Double(target.x - runtime.pos.x)
        let dz = Double(target.y - runtime.pos.y)
        return atan2(dx, -dz)
    }
}
