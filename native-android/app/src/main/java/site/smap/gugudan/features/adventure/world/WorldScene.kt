package site.smap.gugudan.features.adventure.world

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import site.smap.gugudan.core.KoreanReading
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.google.android.filament.IndirectLight
import com.google.android.filament.Skybox
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.createEnvironment
import io.github.sceneview.math.Position
import io.github.sceneview.node.Node
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import site.smap.gugudan.core.adventure.NpcDef
import site.smap.gugudan.core.adventure.NpcKind
import site.smap.gugudan.core.adventure.RegionDef
import site.smap.gugudan.designsystem.hexColor
import site.smap.gugudan.designsystem.suite
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.hypot
import kotlin.math.roundToInt

// 어드벤처 3D 월드 (iOS WorldView.swift 이식) — Scene + 조이스틱 + 나침반

object PortalSpec {
    const val X = 0f
    const val Z = -15f   // 필드 북쪽 끝 중앙
}

@Composable
fun WorldScene(
    region: RegionDef,
    defeatedIds: Set<String>,
    startX: Float, startZ: Float,
    playerColorHex: String,
    playerHatId: String?,
    portalTo: RegionDef?,
    onEncounter: (NpcDef?) -> Unit,
    onCollectShard: () -> Unit,
    onEnterPortal: () -> Unit,
    onPosSaved: (Float, Float) -> Unit,
) {
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val cameraNode = rememberCameraNode(engine) {
        position = Position(startX, 8.5f, startZ + 9.5f)
        lookAt(Position(startX, 0.8f, startZ))
        // 기본 far clip(~30)이 하늘 돔(반경 80)을 잘라 지형 밖이 검게 나오던 문제 해결.
        far = 300f
    }
    val mainLight = rememberMainLightNode(engine) { intensity = 80_000f }

    val runtime = remember(region.table) {
        WorldRuntime(region, startX, startZ, onEncounter, onCollectShard, onEnterPortal).apply {
            if (Build.FINGERPRINT != null) {
                devAutoWalk = getSystemProp("debug.gugu.autowalk") == "1"
            }
        }
    }

    // 하늘색 (linear) — Skybox·IndirectLight·화면 지우기 색이 모두 linear 색공간을 쓴다
    val skyLinear = remember(region.table) {
        val c = hexColor(region.theme.sky)
        fun lin(x: Float) = if (x <= 0.04045f) x / 12.92f else ((x + 0.055f) / 1.055f).pow(2.4f)
        floatArrayOf(lin(c.red), lin(c.green), lin(c.blue))
    }

    // 지역별 하늘색 Skybox — Filament가 배경 전체를 채워 지형 plane 밖이 검게 나오던 문제 해결.
    val skyEnvironment = remember(region.table) {
        val r = skyLinear[0]; val g = skyLinear[1]; val b = skyLinear[2]
        createEnvironment(
            engine = engine,
            // 하늘색 균일 앰비언트(간접광) — 커스텀 Skybox로 교체하며 사라진 기본 IBL 보완.
            indirectLight = IndirectLight.Builder()
                .irradiance(1, floatArrayOf(r, g, b))
                .intensity(60_000f)
                .build(engine),
            skybox = Skybox.Builder().color(r, g, b, 1f).build(engine),
        )
    }

    // 3D 월드는 처음 그려지기까지 몇 초가 걸리고 그동안 표면이 검게 보인다 —
    // 첫 프레임 몇 장이 그려질 때까지 지역 하늘색 로딩 화면으로 덮는다
    var ready by remember(region.table) { mutableStateOf(false) }
    val renderedFrames = remember(region.table) { intArrayOf(0) }

    Box(Modifier.fillMaxSize().background(hexColor(region.theme.sky))) {
        // SceneView(4.x) — childNodes 파라미터 폐기 → content 람다(SceneScope)에서 노드 선언.
        // 월드는 명령형 트리(WorldFactory)라 컨테이너 노드 1개에 조립해 씬에 부착한다.
        SceneView(
            modifier = Modifier.fillMaxSize(),
            // SurfaceView 는 첫 프레임이 올라오기 전까지 검은 구멍으로 보여 입장 때 1~2초 검은 화면이 났다.
            // TextureView 는 그 전까지 투명해 뒤의 하늘색 배경이 보인다 (조작부 z-순서 문제도 없다).
            surfaceType = SurfaceType.TextureSurface,
            engine = engine,
            materialLoader = materialLoader,
            environment = skyEnvironment,
            cameraNode = cameraNode,
            mainLightNode = mainLight,
            onFrame = { frameTimeNanos ->
                runtime.step(frameTimeNanos)
                onPosSaved(runtime.posX, runtime.posZ)
                // 첫 몇 프레임은 셰이더 준비로 아직 검게 나온다 — 렌더링이 흐르기 시작한 뒤(약 0.5초 분량) 걷어낸다
                if (!ready && ++renderedFrames[0] >= 30) ready = true
            },
        ) {
            // apply는 Node.() -> Unit 리시버 람다 — this가 컨테이너 노드.
            Node(apply = {
                if (childNodes.isEmpty()) {
                    val factory = WorldFactory(engine, materialLoader)
                    addChildNode(factory.terrain(region))

                    val handles = region.npcs.map { npc ->
                        val defeated = npc.id in defeatedIds
                        val bundle = factory.npc(npc, defeated)
                        addChildNode(bundle.root)
                        WorldRuntime.NpcHandle(npc, bundle.body, bundle.ring, defeated)
                    }

                    val shardNodes = region.starSpots.map { spot ->
                        val shard = factory.starShard()
                        shard.position = Position(spot.x.toFloat(), 0.8f, spot.z.toFloat())
                        addChildNode(shard)
                        shard to (spot.x.toFloat() to spot.z.toFloat())
                    }

                    var portal: Node? = null
                    if (portalTo != null) {
                        portal = factory.portal(portalTo)
                        portal.position = Position(PortalSpec.X, 0f, PortalSpec.Z)
                        addChildNode(portal)
                    }

                    val player = factory.player(playerColorHex, playerHatId)
                    addChildNode(player.root)

                    runtime.configure(player.root, player.body, cameraNode, handles, shardNodes, portal)
                }
            })
        }

        // 하단 조작부 — 조이스틱(좌) + 나침반(우)
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(horizontal = 24.dp).padding(bottom = 24.dp)
                .fillMaxSize(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Joystick { x, z -> runtime.moveX = x; runtime.moveZ = z }
            Box(Modifier.weight(1f))
            CompassChip(region, defeatedIds, portalTo != null, runtime)
        }

        // 로딩 화면 — 첫 프레임이 그려지면 걷어낸다
        AnimatedVisibility(visible = !ready, enter = fadeIn(), exit = fadeOut()) {
            Column(
                Modifier.fillMaxSize().background(hexColor(region.theme.sky))
                    // 로딩 중에는 아래 조이스틱이 가려져 있다 — 보이지 않는 조이스틱이 눌리지 않게 터치를 막는다
                    .pointerInput(Unit) {
                        awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(14.dp))
                Text("${KoreanReading.toward(region.name)} 가는 중…", style = suite(FontWeight.ExtraBold, 16), color = Color.White)
            }
        }
    }
}

private fun getSystemProp(key: String): String? = try {
    val cls = Class.forName("android.os.SystemProperties")
    cls.getMethod("get", String::class.java).invoke(null, key) as? String
} catch (e: Exception) { null }

// 가상 조이스틱 (iOS Joystick.swift 이식)
@Composable
fun Joystick(onMove: (Float, Float) -> Unit) {
    val base = 118.dp
    val knob = 52.dp
    val density = LocalDensity.current
    val radiusPx = with(density) { (base - knob).toPx() / 2 }
    val deadzone = 0.16f

    var offset by remember { mutableStateOf(Pair(0f, 0f)) }

    Box(
        Modifier.size(base).clip(CircleShape).background(Color.Black.copy(alpha = 0.2f))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { offset = 0f to 0f; onMove(0f, 0f) },
                    onDragCancel = { offset = 0f to 0f; onMove(0f, 0f) },
                ) { change, drag ->
                    change.consume()
                    var dx = offset.first + drag.x
                    var dy = offset.second + drag.y
                    val len = hypot(dx, dy)
                    if (len > radiusPx) { dx = dx / len * radiusPx; dy = dy / len * radiusPx }
                    offset = dx to dy
                    val nx = dx / radiusPx
                    val ny = dy / radiusPx
                    if (hypot(nx, ny) < deadzone) onMove(0f, 0f) else onMove(nx, ny)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.offset { IntOffset(offset.first.roundToInt(), offset.second.roundToInt()) }
                .size(knob).clip(CircleShape).background(Color.White.copy(alpha = 0.85f))
        )
    }
}

// 다음 상대 나침반 — 미격파 주민 → 보스 → 포털 순 안내
@Composable
private fun CompassChip(
    region: RegionDef,
    defeatedIds: Set<String>,
    hasPortal: Boolean,
    runtime: WorldRuntime,
) {
    // runtime.posX/posZ는 mutableState — 프레임마다 갱신되어 자동 recompose
    val px = runtime.posX
    val pz = runtime.posZ

    fun dist(n: NpcDef) = hypot(n.pos.x.toFloat() - px, n.pos.z.toFloat() - pz)
    val normals = region.npcs.filter { it.kind == NpcKind.NORMAL && it.id !in defeatedIds }
    val pool = normals.ifEmpty { region.npcs.filter { it.kind == NpcKind.BOSS && it.id !in defeatedIds } }
    val target = pool.minByOrNull { dist(it) }

    val (label, tx, tz, tint) = when {
        target != null -> {
            val boss = target.kind == NpcKind.BOSS
            Quad(if (boss) "보스!" else target.name,
                target.pos.x.toFloat(), target.pos.z.toFloat(),
                if (boss) Color(0xFFFF5A76) else Color.White)
        }
        hasPortal -> Quad("포털", PortalSpec.X, PortalSpec.Z, Color(0xFF7DD3FC))
        else -> return
    }
    // 화면 위 = 월드 -z, 화면 오른쪽 = 월드 +x
    val angle = Math.toDegrees(atan2((tx - px).toDouble(), (-(tz - pz)).toDouble())).toFloat()

    Column(
        Modifier.width(74.dp).clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.3f))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Filled.Navigation, null, tint = tint,
            modifier = Modifier.size(20.dp).rotate(angle))
        Text(label, style = suite(FontWeight.ExtraBold, 10), color = Color.White,
            maxLines = 1, textAlign = TextAlign.Center)
    }
}

private data class Quad(val a: String, val b: Float, val c: Float, val d: Color)
