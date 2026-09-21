package site.smap.gugudan.features.adventure.world

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.CameraNode
import io.github.sceneview.node.Node
import site.smap.gugudan.core.adventure.NpcDef
import site.smap.gugudan.core.adventure.NpcKind
import site.smap.gugudan.core.adventure.RegionDef
import site.smap.gugudan.core.adventure.World
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// 월드 게임 루프 (iOS WorldRuntime.swift 이식) — Scene onFrame으로 매 프레임 구동.
// 이동/회전/바운스, 팔로우 카메라, 조우 감지(히스테리시스), NPC 둥실+쳐다보기, 보스 오라, 별 조각, 포털.

class WorldRuntime(
    private val region: RegionDef,
    startX: Float, startZ: Float,
    private val onEncounter: (NpcDef?) -> Unit,
    private val onCollectShard: () -> Unit,
    private val onEnterPortal: () -> Unit,
) {
    data class NpcHandle(val def: NpcDef, val body: Node, val ring: Node, val defeated: Boolean)
    private class NpcAnim(val h: NpcHandle, val x: Float, val z: Float, val baseY: Float, val offset: Float) {
        var angle = 0f
    }
    private class Shard(val node: Node, val x: Float, val z: Float, val offset: Float) {
        var collected = false
    }

    private var playerRoot: Node? = null
    private var playerBody: Node? = null
    private var camera: CameraNode? = null
    private var npcAnims = listOf<NpcAnim>()
    private var shards = listOf<Shard>()
    private var portalNode: Node? = null
    private var portalX = 0f; private var portalZ = -15f
    private var portalEntered = false

    // 조이스틱 입력
    var moveX = 0f
    var moveZ = 0f
    var devAutoWalk = false

    // 위치(외부 참조용 — 나침반)
    var posX by mutableStateOf(startX); private set
    var posZ by mutableStateOf(startZ); private set

    private var angle = Math.PI.toFloat()
    private var lastNanos = 0L
    private var encounterAcc = 0f
    private var currentEncounterId: String? = null

    fun configure(
        playerRoot: Node, playerBody: Node, camera: CameraNode,
        npcHandles: List<NpcHandle>,
        shardNodes: List<Pair<Node, Pair<Float, Float>>>,
        portal: Node?,
    ) {
        this.playerRoot = playerRoot
        this.playerBody = playerBody
        this.camera = camera
        npcAnims = npcHandles.mapIndexed { i, h ->
            val s = if (h.def.kind == NpcKind.BOSS) 1.45f else 1f
            NpcAnim(h, h.def.pos.x.toFloat(), h.def.pos.z.toFloat(), 0.62f * s, i * 1.7f)
        }
        shards = shardNodes.mapIndexed { i, (node, p) -> Shard(node, p.first, p.second, i * 1.3f) }
        portalNode = portal
        playerRoot.position = Position(posX, 0f, posZ)
        camera.position = Position(posX, 8.5f, posZ + 9.5f)
        camera.lookAt(Position(posX, 0.8f, posZ))
    }

    private fun moveVector(): Pair<Float, Float> {
        var x = moveX; var z = moveZ
        val len = hypot(x, z)
        if (len == 0f) {
            if (devAutoWalk) {
                val target = shards.firstOrNull { !it.collected }?.let { it.x to it.z }
                    ?: portalNode?.let { portalX to portalZ }
                if (target != null) {
                    val dx = target.first - posX; val dz = target.second - posZ
                    val dl = hypot(dx, dz)
                    if (dl > 0.1f) return dx / dl to dz / dl
                }
            }
            return 0f to 0f
        }
        return if (len > 1f) x / len to z / len else x to z
    }

    fun step(frameTimeNanos: Long) {
        if (lastNanos == 0L) { lastNanos = frameTimeNanos; return }
        val dt = min(0.05f, (frameTimeNanos - lastNanos) / 1e9f)
        lastNanos = frameTimeNanos
        val root = playerRoot ?: return
        val cam = camera ?: return
        val t = frameTimeNanos / 1e9f

        val (vx, vz) = moveVector()
        val moving = vx != 0f || vz != 0f
        val speed = 6.2f

        if (moving) {
            val bound = World.FIELD_BOUND.toFloat() - 0.6f
            posX = (posX + vx * speed * dt).coerceIn(-bound, bound)
            posZ = (posZ + vz * speed * dt).coerceIn(-bound, bound)
            root.position = Position(posX, 0f, posZ)

            // 이동 방향으로 최단 회전 보간 (Y축, 눈이 +z를 봄)
            val target = atan2(vx, vz)
            var diff = target - angle
            while (diff > Math.PI) diff -= (2 * Math.PI).toFloat()
            while (diff < -Math.PI) diff += (2 * Math.PI).toFloat()
            angle += diff * min(1f, dt * 12f)
            root.rotation = Rotation(0f, Math.toDegrees(angle.toDouble()).toFloat(), 0f)
        }

        // 몸통 바운스
        playerBody?.let {
            val y = 0.62f + if (moving) abs(sin(t * 9f)) * 0.12f else sin(t * 2f) * 0.04f
            it.position = Position(0f, y, 0f)
        }

        // NPC 둥실 + 쳐다보기 + 보스 오라 펄스
        for (a in npcAnims) {
            a.h.body.position = Position(0f, a.baseY + sin(t * 2f + a.offset) * 0.05f, 0f)
            val d = hypot(a.x - posX, a.z - posZ)
            val targetAngle = if (d < 5.5f && !a.h.defeated) {
                atan2(posX - a.x, posZ - a.z)
            } else {
                sin(t * 0.8f + a.offset) * 0.15f
            }
            var diff = targetAngle - a.angle
            while (diff > Math.PI) diff -= (2 * Math.PI).toFloat()
            while (diff < -Math.PI) diff += (2 * Math.PI).toFloat()
            a.angle += diff * min(1f, dt * 8f)
            a.h.body.rotation = Rotation(0f, Math.toDegrees(a.angle.toDouble()).toFloat(), 0f)

            if (a.h.def.kind == NpcKind.BOSS && !a.h.defeated) {
                val s = 1f + 0.1f * sin(t * 3.2f)
                a.h.ring.scale = Scale(s, 1f, s)
            }
        }

        // 별 조각 — 회전·부유·수집
        for (s in shards) {
            if (s.collected) continue
            s.node.rotation = Rotation(0f, Math.toDegrees((t * 2f + s.offset).toDouble()).toFloat(), 0f)
            s.node.position = Position(s.x, 0.8f + sin(t * 2.4f + s.offset) * 0.12f, s.z)
            if (hypot(s.x - posX, s.z - posZ) < 1.3f) {
                s.collected = true
                s.node.isVisible = false
                onCollectShard()
            }
        }

        // 포털 — 부유 펄스 + 진입
        portalNode?.let { p ->
            p.position = Position(portalX, sin(t * 1.8f) * 0.08f, portalZ)
            val s = 1f + 0.04f * sin(t * 2.6f)
            p.scale = Scale(s, s, s)
            if (!portalEntered && hypot(portalX - posX, portalZ - posZ) < 1.6f) {
                portalEntered = true
                onEnterPortal()
            }
        }

        // 카메라 팔로우 (프레임률 무관 지수 보간)
        val k = 1f - 0.0001f.pow(dt)
        val cp = cam.position
        cam.position = Position(
            cp.x + (posX - cp.x) * k,
            cp.y + (8.5f - cp.y) * k,
            cp.z + (posZ + 9.5f - cp.z) * k,
        )
        cam.lookAt(Position(posX, 0.8f, posZ))

        // 조우 감지 (120ms + 히스테리시스)
        encounterAcc += dt
        if (encounterAcc >= 0.12f) {
            encounterAcc = 0f
            detectEncounter()
        }
    }

    private fun detectEncounter() {
        fun dist(n: NpcDef) = hypot(n.pos.x.toFloat() - posX, n.pos.z.toFloat() - posZ)
        val nearest = region.npcs.minByOrNull { dist(it) }
        val enterD = World.ENCOUNTER_DIST.toFloat()
        val exitD = World.ENCOUNTER_EXIT_DIST.toFloat()

        val curId = currentEncounterId
        if (curId != null) {
            val curD = region.npcs.firstOrNull { it.id == curId }?.let { dist(it) } ?: Float.MAX_VALUE
            if (curD > exitD) {
                if (nearest != null && dist(nearest) <= enterD) {
                    currentEncounterId = nearest.id
                    onEncounter(nearest)
                } else {
                    currentEncounterId = null
                    onEncounter(null)
                }
            }
        } else if (nearest != null && dist(nearest) <= enterD) {
            currentEncounterId = nearest.id
            onEncounter(nearest)
        }
    }
}
