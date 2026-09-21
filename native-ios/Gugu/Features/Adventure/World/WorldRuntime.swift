import RealityKit
import QuartzCore
import simd

// 월드 게임 루프 (WorldCanvas.tsx의 useFrame 로직 이식) — CADisplayLink로 매 프레임 구동.
// 플레이어 이동/회전/바운스, 3인칭 팔로우 카메라, 근접 NPC 조우 감지(히스테리시스),
// NPC 대기 모션(둥실+쳐다보기), 보스 오라 펄스, 별 조각 회전·수집.

final class WorldRuntime: NSObject {
    // NPC 애니메이션 핸들 (WorldView가 엔티티 생성 후 전달)
    struct NpcHandle {
        let def: NpcDef
        let body: Entity
        let ring: Entity
        let defeated: Bool
    }

    private struct NpcAnim {
        let handle: NpcHandle
        let pos: SIMD2<Float>
        let baseY: Float       // 몸통 기본 높이 (보스 스케일 반영)
        let offset: Float      // 개체별 둥실 위상
        var angle: Float = 0   // 쳐다보기 회전 보간 상태
    }

    private struct Shard {
        let entity: Entity
        let pos: SIMD2<Float>
        let offset: Float
        var collected: Bool = false
    }

    // 엔티티 참조
    private var playerRoot: Entity?
    private var playerBody: Entity?
    private var camera: Entity?
    private var npcAnims: [NpcAnim] = []
    private var shards: [Shard] = []

    // 다음 지역 포털 (보스 격파 후 등장)
    private var portalEntity: Entity?
    private var portalPos: SIMD2<Float>?
    private var portalEntered = false
    private var onEnterPortal: (() -> Void)?

    // 입력 (조이스틱이 기록)
    var moveX: Float = 0
    var moveZ: Float = 0

    #if DEBUG
    /// 테스트 훅 — 입력이 없으면 가장 가까운 별 조각으로 자동 걷기 (수집 E2E 검증용)
    var devAutoWalk = false
    #endif

    // 상태
    private(set) var pos: SIMD2<Float> = .zero
    private var angle: Float = .pi

    // 콜백
    private var onEncounter: ((NpcDef?) -> Void)?
    private var onCollectShard: (() -> Void)?

    // 조우 감지
    private var region: RegionDef?
    private var currentEncounterId: String?
    private var encounterAcc: Double = 0

    private var link: CADisplayLink?
    private var lastTime: CFTimeInterval = 0

    func configure(playerRoot: Entity, playerBody: Entity, camera: Entity,
                   region: RegionDef, startPos: SIMD2<Float>,
                   npcHandles: [NpcHandle],
                   shardEntities: [(entity: Entity, pos: SIMD2<Float>)],
                   portal: (entity: Entity, pos: SIMD2<Float>)? = nil,
                   onEncounter: @escaping (NpcDef?) -> Void,
                   onCollectShard: @escaping () -> Void,
                   onEnterPortal: (() -> Void)? = nil) {
        self.portalEntity = portal?.entity
        self.portalPos = portal?.pos
        self.onEnterPortal = onEnterPortal
        self.playerRoot = playerRoot
        self.playerBody = playerBody
        self.camera = camera
        self.region = region
        self.npcAnims = npcHandles.enumerated().map { i, h in
            let scale: Float = h.def.kind == .boss ? 1.45 : 1
            return NpcAnim(handle: h,
                           pos: SIMD2(Float(h.def.pos.x), Float(h.def.pos.z)),
                           baseY: 0.62 * scale,
                           offset: Float(i) * 1.7)
        }
        self.shards = shardEntities.enumerated().map { i, s in
            Shard(entity: s.entity, pos: s.pos, offset: Float(i) * 1.3)
        }
        self.onEncounter = onEncounter
        self.onCollectShard = onCollectShard
        self.pos = startPos
        playerRoot.position = [startPos.x, 0, startPos.y]
        // 카메라 초기 배치
        camera.position = [startPos.x, 8.5, startPos.y + 9.5]
        camera.look(at: [startPos.x, 0.8, startPos.y], from: camera.position, relativeTo: nil)
    }

    func start() {
        stop()
        lastTime = CACurrentMediaTime()
        let l = CADisplayLink(target: self, selector: #selector(step))
        l.add(to: .main, forMode: .common)
        link = l
    }

    func stop() {
        link?.invalidate()
        link = nil
    }

    // 정규화된 이동 벡터 (moveVector 이식)
    private func moveVector() -> SIMD2<Float> {
        var x = moveX, z = moveZ
        let len = sqrt(x * x + z * z)
        if len == 0 {
            #if DEBUG
            if devAutoWalk {
                // 별 조각 우선, 다 모으면 포털로 (수집→포털 E2E 검증)
                let target = shards.first(where: { !$0.collected })?.pos ?? portalPos
                if let target {
                    let d = target - pos
                    let dl = simd_length(d)
                    if dl > 0.1 { return d / dl }
                }
            }
            #endif
            return .zero
        }
        if len > 1 { x /= len; z /= len }
        return SIMD2(x, z)
    }

    @objc private func step(_ link: CADisplayLink) {
        let now = link.timestamp
        let dt = Float(min(0.05, max(0, now - lastTime)))
        lastTime = now
        guard let root = playerRoot, let cam = camera else { return }
        let t = Float(now)

        let v = moveVector()
        let moving = v.x != 0 || v.y != 0
        let speed: Float = 6.2

        if moving {
            let bound = Float(World.fieldBound) - 0.6
            let nx = min(bound, max(-bound, pos.x + v.x * speed * dt))
            let nz = min(bound, max(-bound, pos.y + v.y * speed * dt))
            pos = SIMD2(nx, nz)
            root.position = [nx, 0, nz]

            // 이동 방향으로 최단 회전 보간
            let target = atan2(v.x, v.y)
            var diff = target - angle
            while diff > .pi { diff -= .pi * 2 }
            while diff < -(.pi) { diff += .pi * 2 }
            angle += diff * min(1, dt * 12)
            root.orientation = simd_quatf(angle: angle, axis: [0, 1, 0])
        }

        // 몸통 바운스 (걸을 때 통통, 멈추면 숨쉬기)
        if let body = playerBody {
            body.position.y = 0.62 + (moving ? abs(sin(t * 9)) * 0.12 : sin(t * 2) * 0.04)
        }

        // NPC 대기 모션 — 둥실 + 흔들, 플레이어가 가까우면 쳐다보기
        for i in npcAnims.indices {
            let a = npcAnims[i]
            let body = a.handle.body
            body.position.y = a.baseY + sin(t * 2 + a.offset) * 0.05

            let d = simd_distance(a.pos, pos)
            let targetAngle: Float
            if d < 5.5 && !a.handle.defeated {
                // 플레이어 방향으로 회전 (눈이 +z를 봄)
                targetAngle = atan2(pos.x - a.pos.x, pos.y - a.pos.y)
            } else {
                targetAngle = sin(t * 0.8 + a.offset) * 0.15
            }
            var diff = targetAngle - a.angle
            while diff > .pi { diff -= .pi * 2 }
            while diff < -(.pi) { diff += .pi * 2 }
            npcAnims[i].angle += diff * min(1, dt * 8)
            body.orientation = simd_quatf(angle: npcAnims[i].angle, axis: [0, 1, 0])

            // 보스 오라 펄스 (미격파일 때만)
            if a.handle.def.kind == .boss && !a.handle.defeated {
                let s = 1 + 0.1 * sin(t * 3.2)
                a.handle.ring.scale = [s, 1, s]
            }
        }

        // 별 조각 — 회전·부유, 근접 시 수집
        for i in shards.indices where !shards[i].collected {
            let s = shards[i]
            s.entity.orientation = simd_quatf(angle: t * 2 + s.offset, axis: [0, 1, 0])
            s.entity.position.y = 0.8 + sin(t * 2.4 + s.offset) * 0.12
            if simd_distance(s.pos, pos) < 1.3 {
                shards[i].collected = true
                s.entity.removeFromParent()
                DispatchQueue.main.async { [weak self] in self?.onCollectShard?() }
            }
        }

        // 포털 — 부유 펄스, 걸어 들어가면 다음 지역으로
        if let pe = portalEntity, let pp = portalPos {
            pe.position.y = sin(t * 1.8) * 0.08
            let s = 1 + 0.04 * sin(t * 2.6)
            pe.scale = [s, s, s]
            if !portalEntered && simd_distance(pp, pos) < 1.6 {
                portalEntered = true
                DispatchQueue.main.async { [weak self] in self?.onEnterPortal?() }
            }
        }

        // 카메라 팔로우 (프레임률 무관 지수 보간)
        let camTarget = SIMD3<Float>(pos.x, 8.5, pos.y + 9.5)
        let k = 1 - pow(Float(0.0001), dt)
        cam.position = mix(cam.position, camTarget, t: k)
        cam.look(at: [pos.x, 0.8, pos.y], from: cam.position, relativeTo: nil)

        // 조우 감지 (120ms 간격 + 히스테리시스)
        encounterAcc += Double(dt)
        if encounterAcc >= 0.12 {
            encounterAcc = 0
            detectEncounter()
        }
    }

    private func detectEncounter() {
        guard let region else { return }
        func dist(_ n: NpcDef) -> Float {
            simd_distance(SIMD2(Float(n.pos.x), Float(n.pos.z)), pos)
        }
        var nearest: (id: String, d: Float)? = nil
        for n in region.npcs {
            let d = dist(n)
            if nearest == nil || d < nearest!.d { nearest = (n.id, d) }
        }
        let enterD = Float(World.encounterDist)
        let exitD = Float(World.encounterExitDist)

        if let curId = currentEncounterId {
            let curD = region.npcs.first { $0.id == curId }.map { dist($0) } ?? .infinity
            if curD > exitD {
                if let near = nearest, near.d <= enterD {
                    currentEncounterId = near.id
                    emit(near.id)
                } else {
                    currentEncounterId = nil
                    emit(nil)
                }
            }
        } else if let near = nearest, near.d <= enterD {
            currentEncounterId = near.id
            emit(near.id)
        }
    }

    private func emit(_ id: String?) {
        let npc = id.flatMap { nid in region?.npcs.first { $0.id == nid } }
        DispatchQueue.main.async { [weak self] in self?.onEncounter?(npc) }
    }
}
