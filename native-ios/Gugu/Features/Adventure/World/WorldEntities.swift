import RealityKit
import UIKit

// 어드벤처 3D 월드 엔티티 빌더 — three.js 프리미티브(Terrain/Npc/Player.tsx)를 RealityKit MeshResource로 재현.
// 좌표계는 three.js와 동일(Y-up, XZ 평면). 소품/랜드마크는 전부 프로시저럴.

enum WorldFX {
    // MARK: - 재질

    static func hexColor(_ hex: String) -> UIColor {
        var s = hex.hasPrefix("#") ? String(hex.dropFirst()) : hex
        if s.count == 3 { s = s.map { "\($0)\($0)" }.joined() }
        var v: UInt64 = 0
        Scanner(string: s).scanHexInt64(&v)
        return UIColor(
            red: CGFloat((v >> 16) & 0xff) / 255,
            green: CGFloat((v >> 8) & 0xff) / 255,
            blue: CGFloat(v & 0xff) / 255, alpha: 1)
    }

    static func mat(_ hex: String) -> SimpleMaterial {
        SimpleMaterial(color: hexColor(hex), roughness: 0.95, isMetallic: false)
    }
    static func mat(_ color: UIColor) -> SimpleMaterial {
        SimpleMaterial(color: color, roughness: 0.95, isMetallic: false)
    }
    /// 발광(용암/반딧불/크리스털) — UnlitMaterial로 항상 밝게
    static func glow(_ hex: String) -> UnlitMaterial {
        UnlitMaterial(color: hexColor(hex))
    }

    // MARK: - 프리미티브 헬퍼

    static func model(_ mesh: MeshResource, _ material: RealityKit.Material) -> ModelEntity {
        ModelEntity(mesh: mesh, materials: [material])
    }

    /// 캡슐(cylinder + 2 sphere) — RealityKit엔 캡슐 생성기가 없어 조합
    static func capsule(radius: Float, length: Float, color: UIColor) -> Entity {
        let m = mat(color)
        let e = Entity()
        let cyl = model(.generateCylinder(height: length, radius: radius), m)
        e.addChild(cyl)
        let top = model(.generateSphere(radius: radius), m); top.position.y = length / 2
        let bot = model(.generateSphere(radius: radius), m); bot.position.y = -length / 2
        e.addChild(top); e.addChild(bot)
        return e
    }

    /// 눈 2개 — 앞면(+z)에 흰자+검은자
    static func eyes() -> Entity {
        let g = Entity()
        g.position = [0, 0.34, 0.38]
        for x in [Float(-0.15), 0.15] {
            let white = model(.generateSphere(radius: 0.1), mat(.white))
            white.position = [x, 0, 0]
            let pupil = model(.generateSphere(radius: 0.05), mat(UIColor(red: 0.125, green: 0.14, blue: 0.17, alpha: 1)))
            pupil.position = [x, 0, 0.07]
            g.addChild(white); g.addChild(pupil)
        }
        return g
    }

    /// 그림자 블롭 (바닥 원반)
    static func shadowBlob(radius: Float) -> ModelEntity {
        let m = UnlitMaterial(color: UIColor(white: 0, alpha: 0.18))
        let disc = model(.generateCylinder(height: 0.01, radius: radius), m)
        disc.position.y = 0.02
        return disc
    }

    // MARK: - 소품 (deco)

    static func deco(_ kind: DecoKind, accent: String, scale: Float) -> Entity {
        switch kind {
        case .tree: return tree(accent: accent, scale: scale)
        case .rock: return rock(accent: accent, scale: scale)
        case .crystal: return crystal(accent: accent, scale: scale)
        }
    }

    static func tree(accent: String, scale: Float) -> Entity {
        let g = Entity()
        let trunk = model(.generateCylinder(height: 0.6, radius: 0.16), mat("#8a6f52")); trunk.position.y = 0.3
        let c1 = model(.generateCone(height: 1.2, radius: 0.62), mat(accent)); c1.position.y = 1.0
        let c2 = model(.generateCone(height: 0.8, radius: 0.4), mat(accent)); c2.position.y = 1.7
        g.addChild(trunk); g.addChild(c1); g.addChild(c2)
        g.scale = [scale, scale, scale]
        return g
    }

    static func rock(accent: String, scale: Float) -> Entity {
        let r = model(.generateSphere(radius: 0.5), mat(accent))
        r.scale = [scale, scale * 0.8, scale]
        r.position.y = 0.3 * scale
        r.orientation = simd_quatf(angle: 0.8, axis: [0.2, 1, 0.1])
        return r
    }

    static func crystal(accent: String, scale: Float) -> Entity {
        let g = Entity()
        let up = model(.generateCone(height: 0.7, radius: 0.32), glow(accent)); up.position.y = 0.35
        let down = model(.generateCone(height: 0.5, radius: 0.32), glow(accent))
        down.position.y = -0.1
        down.orientation = simd_quatf(angle: .pi, axis: [1, 0, 0])
        g.addChild(up); g.addChild(down)
        g.scale = [scale * 0.7, scale * 1.4, scale * 0.7]
        g.position.y = 0.55 * scale
        return g
    }

    // MARK: - 캐릭터

    /// 플레이어 — 상점에서 장착한 색상/모자 반영
    static func player(colorHex: String = "#6366f1", hatID: String? = nil) -> (root: Entity, body: Entity) {
        let root = Entity()
        root.addChild(shadowBlob(radius: 0.5))
        let body = Entity()
        body.addChild(capsule(radius: 0.4, length: 0.5, color: hexColor(colorHex)))
        body.addChild(eyes())
        if let hatID, let h = hat(hatID) { body.addChild(h) }
        body.position.y = 0.62
        root.addChild(body)
        return (root, body)
    }

    /// 상점 모자 — 전부 프로시저럴 (Shop 카탈로그 id와 1:1)
    static func hat(_ id: String) -> Entity? {
        let g = Entity()
        switch id {
        case "sprout":   // 새싹 — 줄기 + 잎
            let stem = model(.generateCylinder(height: 0.25, radius: 0.04), mat("#3e7d3a")); stem.position.y = 0.82
            let leaf = model(.generateSphere(radius: 0.13), mat("#6cbf5a")); leaf.position.y = 0.98
            leaf.scale = [1.4, 0.7, 1]
            g.addChild(stem); g.addChild(leaf)
        case "straw":    // 밀짚모자 — 낮은 콘 + 챙
            let top = model(.generateCone(height: 0.3, radius: 0.42), mat("#eab308")); top.position.y = 0.86
            let brim = model(.generateCylinder(height: 0.04, radius: 0.6), mat("#facc15")); brim.position.y = 0.72
            g.addChild(top); g.addChild(brim)
        case "tophat":   // 신사 모자 — 원통 + 챙
            let top = model(.generateCylinder(height: 0.4, radius: 0.28), mat("#26262e")); top.position.y = 0.92
            let brim = model(.generateCylinder(height: 0.04, radius: 0.46), mat("#26262e")); brim.position.y = 0.72
            g.addChild(top); g.addChild(brim)
        case "crown":    // 황금 왕관 — 보스와 같은 언어
            let crown = model(.generateCone(height: 0.34, radius: 0.26), mat("#ffd166")); crown.position.y = 0.85
            g.addChild(crown)
        default:
            return nil
        }
        return g
    }

    /// 3D 텍스트 빌보드 — 항상 카메라를 향하는 라벨
    static func textBillboard(_ text: String, y: Float, size: CGFloat = 0.5, color: UIColor = .white) -> Entity {
        let mesh = MeshResource.generateText(
            text,
            extrusionDepth: 0.01,
            font: .systemFont(ofSize: size, weight: .bold),
            containerFrame: .zero,
            alignment: .center,
            lineBreakMode: .byTruncatingTail)
        let label = ModelEntity(mesh: mesh, materials: [UnlitMaterial(color: color)])
        // 텍스트는 좌하단 기준 생성 → x 중앙 정렬 보정
        label.position = [-mesh.bounds.center.x, 0, 0]
        let holder = Entity()
        holder.position.y = y
        holder.addChild(label)
        holder.components.set(BillboardComponent())
        return holder
    }

    /// NPC 이름 3D 라벨 (빌보드) — 머리 위에 떠서 항상 카메라를 향함
    static func nameLabel(_ def: NpcDef, defeated: Bool) -> Entity {
        textBillboard("\(defeated ? "✓ " : "")\(def.name) · \(def.table)단",
                      y: def.kind == .boss ? 2.5 : 2.0)
    }

    /// 다음 지역 포털 — 보스 격파 후 북쪽 끝에 등장, 걸어 들어가면 이동
    static func portal(to next: RegionDef) -> Entity {
        let g = Entity()
        // 세로로 세운 발광 게이트 (흰 테두리 + 다음 지역 하늘색 코어)
        let stand = simd_quatf(angle: .pi / 2, axis: [1, 0, 0])
        let frame = model(.generateCylinder(height: 0.08, radius: 1.15), UnlitMaterial(color: UIColor(white: 1, alpha: 0.95)))
        frame.orientation = stand
        frame.position = [0, 1.5, 0]
        let core = model(.generateCylinder(height: 0.1, radius: 0.98), glow(next.theme.sky))
        core.orientation = stand
        core.position = [0, 1.5, 0.03]
        g.addChild(frame); g.addChild(core)
        // 받침 + 라벨
        let base = model(.generateCylinder(height: 0.03, radius: 1.3), UnlitMaterial(color: UIColor(white: 1, alpha: 0.35)))
        base.position.y = 0.02
        g.addChild(base)
        g.addChild(textBillboard("→ \(next.name)", y: 3.1))
        return g
    }

    struct NpcBundle {
        let root: Entity
        let body: Entity
        let ring: Entity   // 발밑 상태 링 — 보스는 런타임에서 펄스
    }

    static func npc(_ def: NpcDef, defeated: Bool) -> NpcBundle {
        let isBoss = def.kind == .boss
        let bodyScale: Float = isBoss ? 1.45 : 1
        let color = defeated ? hexColor("#a5adba") : hexColor(def.color)

        let root = Entity()
        root.position = [Float(def.pos.x), 0, Float(def.pos.z)]
        root.addChild(shadowBlob(radius: 0.55 * bodyScale))
        // 상태 디스크 (격파=초록 / 보스 미격파=붉은 오라 / 일반=흰색)
        let ringColor: UIColor = defeated
            ? UIColor(red: 0.13, green: 0.77, blue: 0.37, alpha: 0.7)
            : isBoss ? UIColor(red: 0.96, green: 0.25, blue: 0.37, alpha: 0.45)
                     : UIColor(white: 1, alpha: 0.28)
        let ring = model(.generateCylinder(height: 0.015, radius: 0.9 * bodyScale), UnlitMaterial(color: ringColor))
        ring.position.y = 0.03
        root.addChild(ring)

        let body = Entity()
        body.addChild(capsule(radius: 0.42, length: 0.5, color: color))
        body.addChild(eyes())
        if isBoss {
            let crown = model(.generateCone(height: 0.34, radius: 0.26), mat("#ffd166"))
            crown.position.y = 0.85
            body.addChild(crown)
        }
        body.scale = [bodyScale, bodyScale, bodyScale]
        body.position.y = 0.62 * bodyScale
        root.addChild(body)
        return NpcBundle(root: root, body: body, ring: ring)
    }

    /// 별 조각 — 노란 발광 팔면체(콘 2개), 런타임에서 회전·부유
    static func starShard() -> Entity {
        let g = Entity()
        let up = model(.generateCone(height: 0.34, radius: 0.2), glow("#ffd34d")); up.position.y = 0.17
        let down = model(.generateCone(height: 0.34, radius: 0.2), glow("#ffd34d"))
        down.position.y = -0.17
        down.orientation = simd_quatf(angle: .pi, axis: [1, 0, 0])
        g.addChild(up); g.addChild(down)
        return g
    }

    // MARK: - 지형 + 랜드마크

    static func terrain(_ region: RegionDef) -> Entity {
        let g = Entity()
        let size = Float(World.fieldBound) * 2 + 6
        let ground = model(.generatePlane(width: size, depth: size), mat(region.theme.ground))
        g.addChild(ground)
        for item in region.decoItems {
            let d = deco(region.deco, accent: region.theme.accent, scale: Float(item.scale))
            d.position = [Float(item.x), 0, Float(item.z)]
            g.addChild(d)
        }
        let lm = landmark(region.landmark.kind, theme: region.theme)
        lm.position = [Float(region.landmark.pos.x), 0, Float(region.landmark.pos.z)]
        g.addChild(lm)
        return g
    }

    static func landmark(_ kind: LandmarkKind, theme: RegionTheme) -> Entity {
        switch kind {
        case .flowerbed: return flowerbed()
        case .oasis: return oasis()
        case .sea: return sea()
        case .ancientTree: return ancientTree(theme.accent)
        case .snowman: return snowman()
        case .pond: return pond()
        case .volcano: return volcano()
        case .pillars: return pillars()
        }
    }

    private static func disc(radius: Float, color: UIColor, y: Float) -> ModelEntity {
        let d = model(.generateCylinder(height: 0.02, radius: radius), mat(color))
        d.position.y = y
        return d
    }

    private static func flowerbed() -> Entity {
        let g = Entity()
        g.addChild(disc(radius: 2.4, color: hexColor("#a8dd6a"), y: 0.03))
        let spots: [(Float, Float, String)] = [
            (-1.2, 0.5, "#ff8fab"), (0.8, 1.1, "#ffd166"), (1.5, -0.6, "#ffffff"),
            (-0.3, -1.3, "#c77dff"), (0.2, 0.2, "#ff6b6b"), (-1.7, -0.5, "#ffd166")]
        for (x, z, c) in spots {
            let stem = model(.generateCylinder(height: 0.34, radius: 0.03), mat("#3e7d3a")); stem.position = [x, 0.17, z]
            let head = model(.generateSphere(radius: 0.15), mat(c)); head.position = [x, 0.42, z]
            g.addChild(stem); g.addChild(head)
        }
        return g
    }

    private static func palm(tilt: Float) -> Entity {
        let g = Entity()
        let trunk = model(.generateCylinder(height: 1.5, radius: 0.12), mat("#8a6f52")); trunk.position.y = 0.75
        let leaves = model(.generateSphere(radius: 0.6), mat("#3e9b4f")); leaves.position.y = 1.62; leaves.scale = [1, 0.45, 1]
        g.addChild(trunk); g.addChild(leaves)
        g.orientation = simd_quatf(angle: tilt, axis: [0, 0, 1])
        return g
    }

    private static func oasis() -> Entity {
        let g = Entity()
        g.addChild(disc(radius: 2.2, color: hexColor("#4cc9f0"), y: 0.04))
        let p1 = palm(tilt: 0.16); p1.position = [-1.5, 0, -1.6]
        let p2 = palm(tilt: -0.2); p2.position = [1.8, 0, 1.0]
        g.addChild(p1); g.addChild(p2)
        return g
    }

    private static func sea() -> Entity {
        let g = Entity()
        let w = model(.generatePlane(width: 7, depth: Float(World.fieldBound) * 2 + 6),
                      SimpleMaterial(color: UIColor(red: 0.18, green: 0.62, blue: 0.78, alpha: 0.92), roughness: 0.3, isMetallic: false))
        w.position.y = 0.05
        g.addChild(w)
        return g
    }

    private static func ancientTree(_ accent: String) -> Entity {
        let g = Entity()
        let trunk = model(.generateCylinder(height: 2.8, radius: 0.6), mat("#7a5230")); trunk.position.y = 1.4
        g.addChild(trunk)
        let cones: [(Float, Float, Float)] = [(2.3, 2.1, 3.0), (1.6, 1.7, 4.2), (1.0, 1.4, 5.2)]
        for (r, h, y) in cones {
            let c = model(.generateCone(height: h, radius: r), mat(accent)); c.position.y = y
            g.addChild(c)
        }
        return g
    }

    private static func snowman() -> Entity {
        let g = Entity()
        for (r, y) in [(Float(0.85), Float(0.85)), (0.6, 2.0), (0.42, 2.85)] {
            let s = model(.generateSphere(radius: r), mat("#ffffff")); s.position.y = y
            g.addChild(s)
        }
        let nose = model(.generateCone(height: 0.4, radius: 0.08), mat("#ff8c42"))
        nose.position = [0, 2.9, 0.42]
        nose.orientation = simd_quatf(angle: .pi / 2, axis: [1, 0, 0])
        g.addChild(nose)
        for x in [Float(-0.13), 0.13] {
            let eye = model(.generateSphere(radius: 0.05), mat("#20242c")); eye.position = [x, 3.02, 0.36]
            g.addChild(eye)
        }
        return g
    }

    private static func pond() -> Entity {
        let g = Entity()
        g.addChild(disc(radius: 2.6, color: hexColor("#2f4858"), y: 0.04))
        let flies: [(Float, Float, Float)] = [(-1.2, 1.0, 0.6), (0.8, 1.3, -0.9), (1.7, 0.8, 1.2), (-0.5, 1.5, -1.8), (0.1, 1.1, 1.9)]
        for (x, y, z) in flies {
            let f = model(.generateSphere(radius: 0.09), glow("#ffe066")); f.position = [x, y, z]
            g.addChild(f)
        }
        return g
    }

    private static func volcano() -> Entity {
        let g = Entity()
        let cone = model(.generateCone(height: 4.4, radius: 3.4), mat("#5a3a35")); cone.position.y = 2.2
        g.addChild(cone)
        let lava = model(.generateCylinder(height: 0.05, radius: 1.0), glow("#ff5a2a")); lava.position.y = 4.42
        g.addChild(lava)
        return g
    }

    private static func pillars() -> Entity {
        let g = Entity()
        for (x, z) in [(Float(-3.5), Float(0)), (3.5, 0), (-3.5, -3), (3.5, -3)] {
            let col = model(.generateCylinder(height: 4.2, radius: 0.5), mat("#8f93b8")); col.position = [x, 2.1, z]
            let cap = model(.generateBox(size: [1.3, 0.35, 1.3]), mat("#a9adcc")); cap.position = [x, 4.35, z]
            g.addChild(col); g.addChild(cap)
        }
        return g
    }
}
