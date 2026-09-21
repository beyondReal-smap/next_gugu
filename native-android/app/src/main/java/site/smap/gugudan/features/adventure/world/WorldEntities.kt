package site.smap.gugudan.features.adventure.world

import androidx.compose.ui.graphics.Color
import com.google.android.filament.Engine
import io.github.sceneview.geometries.Geometry
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.GeometryNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.PlaneNode
import io.github.sceneview.node.SphereNode
import site.smap.gugudan.core.adventure.DecoKind
import site.smap.gugudan.core.adventure.LandmarkKind
import site.smap.gugudan.core.adventure.NpcDef
import site.smap.gugudan.core.adventure.NpcKind
import site.smap.gugudan.core.adventure.RegionDef
import site.smap.gugudan.core.adventure.RegionTheme
import site.smap.gugudan.core.adventure.World
import site.smap.gugudan.designsystem.hexColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// 3D 월드 엔티티 빌더 (iOS WorldEntities.swift 이식) — SceneView 노드 조립.
// 좌표계 동일(Y-up, XZ 평면). 소품/랜드마크 전부 프로시저럴.

class WorldFactory(private val engine: Engine, private val materials: MaterialLoader) {

    private val matCache = mutableMapOf<Long, com.google.android.filament.MaterialInstance>()
    private fun mat(color: Color): com.google.android.filament.MaterialInstance =
        matCache.getOrPut(color.value.toLong()) { materials.createColorInstance(color) }
    private fun mat(hex: String) = mat(hexColor(hex))

    // MARK: 프리미티브

    private fun cylinder(radius: Float, height: Float, color: Color) =
        CylinderNode(engine, radius = radius, height = height, materialInstance = mat(color))

    private fun sphere(radius: Float, color: Color) =
        SphereNode(engine, radius = radius, materialInstance = mat(color))

    /** 원뿔 — SceneView 미제공이라 커스텀 지오메트리로 생성 */
    private fun cone(radius: Float, height: Float, color: Color, segments: Int = 16): GeometryNode {
        val verts = mutableListOf<Geometry.Vertex>()
        val idx = mutableListOf<Int>()
        val h2 = height / 2f
        // 측면 — 세그먼트별 삼각형 (플랫 셰이딩 느낌의 노멀)
        for (i in 0 until segments) {
            val a0 = (i.toDouble() / segments * 2 * PI).toFloat()
            val a1 = ((i + 1.0) / segments * 2 * PI).toFloat()
            val p0 = Position(radius * cos(a0), -h2, radius * sin(a0))
            val p1 = Position(radius * cos(a1), -h2, radius * sin(a1))
            val apex = Position(0f, h2, 0f)
            val mid = (a0 + a1) / 2f
            val n = io.github.sceneview.math.Direction(cos(mid), radius / height, sin(mid))
            val base = verts.size
            verts += Geometry.Vertex(position = apex, normal = n)
            verts += Geometry.Vertex(position = p1, normal = n)
            verts += Geometry.Vertex(position = p0, normal = n)
            idx += listOf(base, base + 1, base + 2)
        }
        // 밑면 캡
        val downN = io.github.sceneview.math.Direction(0f, -1f, 0f)
        val centerIdx = verts.size
        verts += Geometry.Vertex(position = Position(0f, -h2, 0f), normal = downN)
        for (i in 0 until segments) {
            val a0 = (i.toDouble() / segments * 2 * PI).toFloat()
            val a1 = ((i + 1.0) / segments * 2 * PI).toFloat()
            val base = verts.size
            verts += Geometry.Vertex(position = Position(radius * cos(a0), -h2, radius * sin(a0)), normal = downN)
            verts += Geometry.Vertex(position = Position(radius * cos(a1), -h2, radius * sin(a1)), normal = downN)
            idx += listOf(centerIdx, base, base + 1)
        }
        val geometry = Geometry.Builder()
            .vertices(verts)
            .indices(idx)
            .build(engine)
        return GeometryNode(engine, geometry = geometry, materialInstance = mat(color))
    }

    /** 캡슐 — 실린더 + 구 2 */
    private fun capsule(radius: Float, length: Float, color: Color): Node {
        val g = Node(engine)
        g.addChildNode(cylinder(radius, length, color))
        g.addChildNode(sphere(radius, color).apply { position = Position(0f, length / 2, 0f) })
        g.addChildNode(sphere(radius, color).apply { position = Position(0f, -length / 2, 0f) })
        return g
    }

    /** 눈 2개 — 앞면(+z) */
    private fun eyes(): Node {
        val g = Node(engine)
        g.position = Position(0f, 0.34f, 0.38f)
        for (x in listOf(-0.15f, 0.15f)) {
            g.addChildNode(sphere(0.1f, Color.White).apply { position = Position(x, 0f, 0f) })
            g.addChildNode(sphere(0.05f, Color(0xFF20242C)).apply { position = Position(x, 0f, 0.07f) })
        }
        return g
    }

    /** 그림자 블롭 (바닥 원반) */
    private fun shadowBlob(radius: Float): Node =
        cylinder(radius, 0.01f, Color(0x2E000000)).apply { position = Position(0f, 0.02f, 0f) }

    private fun disc(radius: Float, color: Color, y: Float): Node =
        cylinder(radius, 0.02f, color).apply { position = Position(0f, y, 0f) }

    // MARK: 소품

    fun deco(kind: DecoKind, accent: String, scale: Float): Node = when (kind) {
        DecoKind.TREE -> tree(accent, scale)
        DecoKind.ROCK -> rock(accent, scale)
        DecoKind.CRYSTAL -> crystal(accent, scale)
    }

    private fun tree(accent: String, s: Float): Node {
        val g = Node(engine)
        g.addChildNode(cylinder(0.16f, 0.6f, hexColor("#8a6f52")).apply { position = Position(0f, 0.3f, 0f) })
        g.addChildNode(cone(0.62f, 1.2f, hexColor(accent)).apply { position = Position(0f, 1.0f, 0f) })
        g.addChildNode(cone(0.4f, 0.8f, hexColor(accent)).apply { position = Position(0f, 1.7f, 0f) })
        g.scale = Scale(s, s, s)
        return g
    }

    private fun rock(accent: String, s: Float): Node =
        sphere(0.5f, hexColor(accent)).apply {
            scale = Scale(s, s * 0.8f, s)
            position = Position(0f, 0.3f * s, 0f)
        }

    private fun crystal(accent: String, s: Float): Node {
        val g = Node(engine)
        val c = hexColor(accent)
        g.addChildNode(cone(0.32f, 0.7f, c).apply { position = Position(0f, 0.35f, 0f) })
        g.addChildNode(cone(0.32f, 0.5f, c).apply {
            position = Position(0f, -0.1f, 0f)
            rotation = Rotation(180f, 0f, 0f)
        })
        g.scale = Scale(s * 0.7f, s * 1.4f, s * 0.7f)
        g.position = Position(0f, 0.55f * s, 0f)
        return g
    }

    // MARK: 캐릭터

    data class Player(val root: Node, val body: Node)

    fun player(colorHex: String, hatId: String?): Player {
        val root = Node(engine)
        root.addChildNode(shadowBlob(0.5f))
        val body = Node(engine)
        body.addChildNode(capsule(0.4f, 0.5f, hexColor(colorHex)))
        body.addChildNode(eyes())
        hatId?.let { hat(it)?.let(body::addChildNode) }
        body.position = Position(0f, 0.62f, 0f)
        root.addChildNode(body)
        return Player(root, body)
    }

    /** 상점 모자 (Shop 카탈로그 id와 1:1) */
    fun hat(id: String): Node? {
        val g = Node(engine)
        when (id) {
            "sprout" -> {
                g.addChildNode(cylinder(0.04f, 0.25f, hexColor("#3e7d3a")).apply { position = Position(0f, 0.82f, 0f) })
                g.addChildNode(sphere(0.13f, hexColor("#6cbf5a")).apply {
                    position = Position(0f, 0.98f, 0f); scale = Scale(1.4f, 0.7f, 1f)
                })
            }
            "straw" -> {
                g.addChildNode(cone(0.42f, 0.3f, hexColor("#eab308")).apply { position = Position(0f, 0.86f, 0f) })
                g.addChildNode(cylinder(0.6f, 0.04f, hexColor("#facc15")).apply { position = Position(0f, 0.72f, 0f) })
            }
            "tophat" -> {
                g.addChildNode(cylinder(0.28f, 0.4f, hexColor("#26262e")).apply { position = Position(0f, 0.92f, 0f) })
                g.addChildNode(cylinder(0.46f, 0.04f, hexColor("#26262e")).apply { position = Position(0f, 0.72f, 0f) })
            }
            "crown" -> {
                g.addChildNode(cone(0.26f, 0.34f, hexColor("#ffd166")).apply { position = Position(0f, 0.85f, 0f) })
            }
            else -> return null
        }
        return g
    }

    data class NpcBundle(val root: Node, val body: Node, val ring: Node)

    fun npc(def: NpcDef, defeated: Boolean): NpcBundle {
        val isBoss = def.kind == NpcKind.BOSS
        val bodyScale = if (isBoss) 1.45f else 1f
        val color = if (defeated) hexColor("#a5adba") else hexColor(def.color)

        val root = Node(engine)
        root.position = Position(def.pos.x.toFloat(), 0f, def.pos.z.toFloat())
        root.addChildNode(shadowBlob(0.55f * bodyScale))
        val ringColor = when {
            defeated -> Color(0xB322C55E)
            isBoss -> Color(0x73F5405E)
            else -> Color(0x47FFFFFF)
        }
        val ring = cylinder(0.9f * bodyScale, 0.015f, ringColor).apply { position = Position(0f, 0.03f, 0f) }
        root.addChildNode(ring)

        val body = Node(engine)
        body.addChildNode(capsule(0.42f, 0.5f, color))
        body.addChildNode(eyes())
        if (isBoss) {
            body.addChildNode(cone(0.26f, 0.34f, hexColor("#ffd166")).apply { position = Position(0f, 0.85f, 0f) })
        }
        body.scale = Scale(bodyScale, bodyScale, bodyScale)
        body.position = Position(0f, 0.62f * bodyScale, 0f)
        root.addChildNode(body)
        return NpcBundle(root, body, ring)
    }

    /** 별 조각 — 노란 발광 팔면체(콘 2개) */
    fun starShard(): Node {
        val g = Node(engine)
        val c = hexColor("#ffd34d")
        g.addChildNode(cone(0.2f, 0.34f, c).apply { position = Position(0f, 0.17f, 0f) })
        g.addChildNode(cone(0.2f, 0.34f, c).apply {
            position = Position(0f, -0.17f, 0f); rotation = Rotation(180f, 0f, 0f)
        })
        return g
    }

    /** 다음 지역 포털 — 세로 발광 게이트 */
    fun portal(next: RegionDef): Node {
        val g = Node(engine)
        val stand = Rotation(90f, 0f, 0f)
        g.addChildNode(cylinder(1.15f, 0.08f, Color.White).apply {
            position = Position(0f, 1.5f, 0f); rotation = stand
        })
        g.addChildNode(cylinder(0.98f, 0.1f, hexColor(next.theme.sky)).apply {
            position = Position(0f, 1.5f, 0.06f); rotation = stand
        })
        g.addChildNode(cylinder(1.3f, 0.03f, Color(0x59FFFFFF)).apply { position = Position(0f, 0.02f, 0f) })
        return g
    }

    // MARK: 지형 + 랜드마크

    fun terrain(region: RegionDef): Node {
        val g = Node(engine)
        val size = (World.FIELD_BOUND * 2 + 6).toFloat()
        g.addChildNode(PlaneNode(engine, size = Size(size, 0f, size), materialInstance = mat(region.theme.ground)))
        region.decoItems.forEach { item ->
            g.addChildNode(deco(region.deco, region.theme.accent, item.scale.toFloat()).apply {
                position = Position(item.x.toFloat(), position.y, item.z.toFloat())
            })
        }
        g.addChildNode(landmark(region.landmark.kind, region.theme).apply {
            position = Position(region.landmark.pos.x.toFloat(), 0f, region.landmark.pos.z.toFloat())
        })
        return g
    }

    private fun landmark(kind: LandmarkKind, theme: RegionTheme): Node = when (kind) {
        LandmarkKind.FLOWERBED -> flowerbed()
        LandmarkKind.OASIS -> oasis()
        LandmarkKind.SEA -> sea()
        LandmarkKind.ANCIENT_TREE -> ancientTree(theme.accent)
        LandmarkKind.SNOWMAN -> snowman()
        LandmarkKind.POND -> pond()
        LandmarkKind.VOLCANO -> volcano()
        LandmarkKind.PILLARS -> pillars()
    }

    private fun flowerbed(): Node {
        val g = Node(engine)
        g.addChildNode(disc(2.4f, hexColor("#a8dd6a"), 0.03f))
        val spots = listOf(
            Triple(-1.2f, 0.5f, "#ff8fab"), Triple(0.8f, 1.1f, "#ffd166"), Triple(1.5f, -0.6f, "#ffffff"),
            Triple(-0.3f, -1.3f, "#c77dff"), Triple(0.2f, 0.2f, "#ff6b6b"), Triple(-1.7f, -0.5f, "#ffd166"))
        for ((x, z, c) in spots) {
            g.addChildNode(cylinder(0.03f, 0.34f, hexColor("#3e7d3a")).apply { position = Position(x, 0.17f, z) })
            g.addChildNode(sphere(0.15f, hexColor(c)).apply { position = Position(x, 0.42f, z) })
        }
        return g
    }

    private fun palm(tilt: Float): Node {
        val g = Node(engine)
        g.addChildNode(cylinder(0.12f, 1.5f, hexColor("#8a6f52")).apply { position = Position(0f, 0.75f, 0f) })
        g.addChildNode(sphere(0.6f, hexColor("#3e9b4f")).apply {
            position = Position(0f, 1.62f, 0f); scale = Scale(1f, 0.45f, 1f)
        })
        g.rotation = Rotation(0f, 0f, tilt)
        return g
    }

    private fun oasis(): Node {
        val g = Node(engine)
        g.addChildNode(disc(2.2f, hexColor("#4cc9f0"), 0.04f))
        g.addChildNode(palm(9f).apply { position = Position(-1.5f, 0f, -1.6f) })
        g.addChildNode(palm(-11f).apply { position = Position(1.8f, 0f, 1.0f) })
        return g
    }

    private fun sea(): Node {
        val g = Node(engine)
        g.addChildNode(PlaneNode(engine,
            size = Size(7f, 0f, (World.FIELD_BOUND * 2 + 6).toFloat()),
            materialInstance = mat(Color(0xEB2F9EC7))).apply { position = Position(0f, 0.05f, 0f) })
        return g
    }

    private fun ancientTree(accent: String): Node {
        val g = Node(engine)
        g.addChildNode(cylinder(0.6f, 2.8f, hexColor("#7a5230")).apply { position = Position(0f, 1.4f, 0f) })
        listOf(Triple(2.3f, 2.1f, 3.0f), Triple(1.6f, 1.7f, 4.2f), Triple(1.0f, 1.4f, 5.2f)).forEach { (r, h, y) ->
            g.addChildNode(cone(r, h, hexColor(accent)).apply { position = Position(0f, y, 0f) })
        }
        return g
    }

    private fun snowman(): Node {
        val g = Node(engine)
        listOf(0.85f to 0.85f, 0.6f to 2.0f, 0.42f to 2.85f).forEach { (r, y) ->
            g.addChildNode(sphere(r, Color.White).apply { position = Position(0f, y, 0f) })
        }
        g.addChildNode(cone(0.08f, 0.4f, hexColor("#ff8c42")).apply {
            position = Position(0f, 2.9f, 0.42f); rotation = Rotation(90f, 0f, 0f)
        })
        for (x in listOf(-0.13f, 0.13f)) {
            g.addChildNode(sphere(0.05f, Color(0xFF20242C)).apply { position = Position(x, 3.02f, 0.36f) })
        }
        return g
    }

    private fun pond(): Node {
        val g = Node(engine)
        g.addChildNode(disc(2.6f, hexColor("#2f4858"), 0.04f))
        listOf(Triple(-1.2f, 1.0f, 0.6f), Triple(0.8f, 1.3f, -0.9f), Triple(1.7f, 0.8f, 1.2f),
            Triple(-0.5f, 1.5f, -1.8f), Triple(0.1f, 1.1f, 1.9f)).forEach { (x, y, z) ->
            g.addChildNode(sphere(0.09f, hexColor("#ffe066")).apply { position = Position(x, y, z) })
        }
        return g
    }

    private fun volcano(): Node {
        val g = Node(engine)
        g.addChildNode(cone(3.4f, 4.4f, hexColor("#5a3a35")).apply { position = Position(0f, 2.2f, 0f) })
        g.addChildNode(cylinder(1.0f, 0.05f, hexColor("#ff5a2a")).apply { position = Position(0f, 4.42f, 0f) })
        return g
    }

    private fun pillars(): Node {
        val g = Node(engine)
        listOf(-3.5f to 0f, 3.5f to 0f, -3.5f to -3f, 3.5f to -3f).forEach { (x, z) ->
            g.addChildNode(cylinder(0.5f, 4.2f, hexColor("#8f93b8")).apply { position = Position(x, 2.1f, z) })
            g.addChildNode(CylinderNode(engine, radius = 0.75f, height = 0.35f,
                materialInstance = mat(hexColor("#a9adcc"))).apply { position = Position(x, 4.35f, z) })
        }
        return g
    }
}
