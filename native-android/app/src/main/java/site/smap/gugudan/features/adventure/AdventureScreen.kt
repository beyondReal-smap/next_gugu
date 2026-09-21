package site.smap.gugudan.features.adventure

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import site.smap.gugudan.core.adventure.AdvAchievements
import site.smap.gugudan.core.adventure.AdvProgress
import site.smap.gugudan.core.adventure.Battle
import site.smap.gugudan.core.adventure.NpcDef
import site.smap.gugudan.core.adventure.NpcKind
import site.smap.gugudan.core.adventure.RegionDef
import site.smap.gugudan.core.adventure.World
import site.smap.gugudan.designsystem.*
import site.smap.gugudan.features.adventure.battle.BattleScreen
import site.smap.gugudan.features.adventure.world.WorldScene
import site.smap.gugudan.features.home.GaugeBar
import site.smap.gugudan.services.Haptics
import site.smap.gugudan.services.Sound
import site.smap.gugudan.store.LocalAdventure
import site.smap.gugudan.store.LocalGame

// 어드벤처 오케스트레이션 (iOS AdventureView 이식) — map → world → battle 상태 머신

private sealed interface Stage {
    data object MapStage : Stage
    data class WorldStage(val table: Int) : Stage
    data class BattleStage(val table: Int, val npc: NpcDef, val token: Int) : Stage
}

@Composable
fun AdventureScreen(onExit: () -> Unit) {
    val adventure = LocalAdventure.current
    val game = LocalGame.current

    var stage by remember { mutableStateOf<Stage>(Stage.MapStage) }
    var encounter by remember { mutableStateOf<NpcDef?>(null) }
    var posX by remember { mutableStateOf(World.regions.first().spawn.x.toFloat()) }
    var posZ by remember { mutableStateOf(World.regions.first().spawn.z.toFloat()) }
    var bossOwnedBeforeBattle by remember { mutableStateOf(false) }
    var celebration by remember { mutableStateOf<RegionDef?>(null) }
    var celebrationConfetti by remember { mutableStateOf(0) }

    fun enterWorld(region: RegionDef) {
        posX = region.spawn.x.toFloat()
        posZ = region.spawn.z.toFloat()
        // QA 훅 — N번째 NPC 근처 스폰 (배틀 검증용): setprop debug.gugu.adv_spawn 1~4
        site.smap.gugudan.services.DevProps.get("debug.gugu.adv_spawn")?.toIntOrNull()?.let { idx ->
            region.npcs.getOrNull(idx - 1)?.let { n ->
                posX = n.pos.x.toFloat()
                posZ = n.pos.z.toFloat() + 2.2f
            }
        }
        encounter = null
        stage = Stage.WorldStage(region.table)
    }

    when (val s = stage) {
        is Stage.MapStage -> RegionMapView(onSelect = ::enterWorld, onExit = onExit)

        is Stage.WorldStage -> {
            val region = World.region(s.table) ?: return
            val stats = AdvProgress.regionStats(adventure.progress, region)
            val normalsCleared = region.npcs.filter { it.kind == NpcKind.NORMAL }
                .all { AdvProgress.isNpcDefeated(adventure.progress, it.id) }
            val nextRegion = if (stats.bossDefeated) World.region(region.table + 1) else null

            Box(Modifier.fillMaxSize()) {
                key(region.table) {
                    WorldScene(
                        region = region,
                        defeatedIds = adventure.progress.defeatedNpcs.toSet(),
                        startX = posX, startZ = posZ,
                        playerColorHex = adventure.equippedColorHex,
                        playerHatId = adventure.progress.equippedHat,
                        portalTo = nextRegion,
                        onEncounter = { encounter = it },
                        onCollectShard = {
                            adventure.collectShard()
                            game.grantXp(2)   // 별 조각 = 소량 XP (통계 미오염)
                            Sound.collect()
                            Haptics.impactLight()
                        },
                        onEnterPortal = {
                            if (nextRegion != null) {
                                Haptics.success(); Sound.collect()
                                enterWorld(nextRegion)
                            }
                        },
                        onPosSaved = { x, z -> posX = x; posZ = z },
                    )
                }

                WorldHud(region, stats, adventure.progress.starShards) {
                    encounter = null
                    stage = Stage.MapStage
                }

                key(region.table) { RegionBanner(region) }

                ConfettiView(celebrationConfetti)
                celebration?.let { cleared ->
                    ClearCelebration(cleared, World.region(cleared.table + 1))
                }

                AnimatedVisibility(
                    visible = encounter != null,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 168.dp),
                    enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
                ) {
                    encounter?.let { npc ->
                        EncounterPrompt(
                            npc = npc,
                            defeated = AdvProgress.isNpcDefeated(adventure.progress, npc.id),
                            bossLocked = npc.kind == NpcKind.BOSS && !normalsCleared,
                            onBattle = {
                                encounter = null
                                bossOwnedBeforeBattle = AdvProgress.isNpcDefeated(adventure.progress, npc.id)
                                stage = Stage.BattleStage(region.table, npc, 0)
                            },
                        )
                    }
                }
            }
        }

        is Stage.BattleStage -> {
            key("${s.npc.id}-${s.token}") {
                BattleScreen(
                    npc = s.npc,
                    onWorld = {
                        // 보스 첫 격파 → 클리어 연출
                        if (s.npc.kind == NpcKind.BOSS && !bossOwnedBeforeBattle &&
                            AdvProgress.isNpcDefeated(adventure.progress, s.npc.id)) {
                            celebration = World.region(s.table)
                            celebrationConfetti += 1
                        }
                        stage = Stage.WorldStage(s.table)
                    },
                    onRetry = { stage = Stage.BattleStage(s.table, s.npc, s.token + 1) },
                    onFlee = { stage = Stage.WorldStage(s.table) },
                )
            }
        }
    }

    // 클리어 연출 자동 해제
    LaunchedEffect(celebration) {
        if (celebration != null) { delay(3400); celebration = null }
    }
}

// MARK: 지역 선택 맵 (iOS RegionMap 이식)

@Composable
private fun RegionMapView(onSelect: (RegionDef) -> Unit, onExit: () -> Unit) {
    val gg = LocalGG.current
    val adventure = LocalAdventure.current
    val game = LocalGame.current
    var showShop by remember { mutableStateOf(false) }
    val (defeated, total) = AdvProgress.totalStats(adventure.progress)

    Column(
        Modifier.fillMaxSize().background(gg.bg).statusBarsPadding().padding(top = 12.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("어드벤처", style = suite(FontWeight.ExtraBold, 24), color = gg.text, modifier = Modifier.weight(1f))
                PressableCard(onClick = onExit) {
                    Icon(Icons.Filled.Close, "닫기", tint = gg.textMuted, modifier = Modifier.size(24.dp))
                }
            }
            Text("지역을 탐험하고 주민들과 구구단 대결을 펼쳐요",
                style = suite(FontWeight.Normal, 14), color = gg.textMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    GaugeBar(if (total > 0) defeated.toDouble() / total else 0.0)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Shield, null, tint = gg.textMuted, modifier = Modifier.size(12.dp))
                    Text("$defeated/$total", style = suite(FontWeight.ExtraBold, 12), color = gg.textMuted)
                }
            }

            // 꾸미기 상점 입구
            PressableCard(onClick = { showShop = true }) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(gg.warning.copy(alpha = 0.1f))
                        .border(1.dp, gg.warning.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Star, null, tint = gg.warning, modifier = Modifier.size(14.dp))
                    Text("${adventure.progress.starShards}", style = suite(FontWeight.ExtraBold, 15), color = gg.text)
                    Text("꾸미기 상점", style = suite(FontWeight.Bold, 14), color = gg.text, modifier = Modifier.weight(1f))
                    Text("색상 · 모자", style = suite(FontWeight.Bold, 12), color = gg.textMuted)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = gg.textMuted, modifier = Modifier.size(16.dp))
                }
            }

            // 어드벤처 업적 칩
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AdvAchievements.all.forEach { a ->
                    val on = a.id in adventure.progress.achievements
                    Row(
                        Modifier.clip(CircleShape)
                            .background(if (on) gg.warning.copy(alpha = 0.15f) else gg.surface2)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(achievementIcon(a.icon), null,
                            tint = (if (on) gg.warning else gg.textMuted).copy(alpha = if (on) 1f else 0.6f),
                            modifier = Modifier.size(10.dp))
                        Text(a.name, style = suite(FontWeight.ExtraBold, 10),
                            color = (if (on) gg.warning else gg.textMuted).copy(alpha = if (on) 1f else 0.6f))
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            World.regions.forEach { region ->
                val unlocked = AdvProgress.isRegionUnlocked(
                    adventure.progress,
                    region.table,
                    tableStars = game.state.tableMastery[region.table]?.stars ?: 0,
                )
                val stats = AdvProgress.regionStats(adventure.progress, region)
                PressableCard(enabled = unlocked, onClick = { onSelect(region) }) {
                    Row(
                        Modifier.fillMaxWidth().ggCard(16.dp).padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 지역 스와치
                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(hexColor(region.theme.sky))) {
                            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(20.dp)
                                .background(hexColor(region.theme.ground)))
                            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                                .size(16.dp).clip(CircleShape).background(hexColor(region.theme.accent)))
                            if (!unlocked) {
                                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                                    contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Lock, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(region.name, style = suite(FontWeight.ExtraBold, 16),
                                    color = gg.text.copy(alpha = if (unlocked) 1f else 0.55f), maxLines = 1)
                                Box(Modifier.clip(CircleShape).background(gg.accent.copy(alpha = 0.12f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)) {
                                    Text("${region.table}단", style = suite(FontWeight.ExtraBold, 10), color = gg.accent)
                                }
                                if (stats.bossDefeated) {
                                    Icon(Icons.Filled.EmojiEvents, null, tint = gg.warning, modifier = Modifier.size(14.dp))
                                }
                            }
                            Text(
                                if (unlocked) "주민 격파 ${stats.defeated}/${stats.total}" +
                                    (if (stats.bossDefeated) " · 클리어!" else "")
                                else "이전 지역 보스를 이기면 열려요",
                                style = suite(FontWeight.Normal, 13),
                                color = gg.textMuted.copy(alpha = if (unlocked) 1f else 0.55f),
                            )
                        }
                        if (unlocked) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = gg.textMuted)
                        }
                    }
                }
            }
        }
    }

    if (showShop) {
        ShopScreen(onDismiss = { showShop = false })
    }
}

// MARK: 월드 HUD

@Composable
private fun WorldHud(
    region: RegionDef, stats: AdvProgress.RegionStats, shards: Int, onBack: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PressableCard(onClick = onBack) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "지역 선택", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Box(Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text("${region.name} · ${region.table}단", style = suite(FontWeight.ExtraBold, 14), color = Color.White)
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, null, tint = Color(0xFFFFD34D), modifier = Modifier.size(12.dp))
            Text("$shards", style = suite(FontWeight.ExtraBold, 13), color = Color.White)
        }
        Row(Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Shield, null, tint = Color.White, modifier = Modifier.size(14.dp))
            Text("${stats.defeated}/${stats.total}", style = suite(FontWeight.Bold, 14), color = Color.White)
        }
    }
}

// MARK: 입장 배너 / 클리어 연출

@Composable
private fun RegionBanner(region: RegionDef) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
        delay(1600)
        visible = false
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visible, enter = scaleIn(initialScale = 0.9f) + fadeIn(), exit = fadeOut()) {
            Column(
                Modifier.clip(RoundedCornerShape(24.dp)).background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 32.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(region.name, style = suite(FontWeight.Black, 34), color = Color.White)
                Text("${region.table}단의 세계", style = suite(FontWeight.Bold, 15), color = Color.White.copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
private fun ClearCelebration(region: RegionDef, next: RegionDef?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.clip(RoundedCornerShape(28.dp)).background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("🎉", style = suite(FontWeight.Bold, 44))
            Text("${region.name} 클리어!", style = suite(FontWeight.Black, 30), color = Color.White)
            Text(
                if (next != null) "북쪽 끝에 ${next.name} 포털이 열렸어요!" else "모든 지역을 정복했어요! 👑",
                style = suite(FontWeight.Bold, 14), color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// MARK: 조우 프롬프트 (iOS EncounterPrompt 이식)

@Composable
private fun EncounterPrompt(npc: NpcDef, defeated: Boolean, bossLocked: Boolean, onBattle: () -> Unit) {
    val gg = LocalGG.current
    val isBoss = npc.kind == NpcKind.BOSS

    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)).background(gg.surface.copy(alpha = 0.96f))
            .border(1.dp, gg.border, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(hexColor(npc.color)))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(2) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF20242C)),
                            contentAlignment = Alignment.Center) {
                            Box(Modifier.size(3.dp).clip(CircleShape).background(Color.White))
                        }
                    }
                }
                if (isBoss) {
                    Icon(Icons.Filled.EmojiEvents, null, tint = gg.warning,
                        modifier = Modifier.size(16.dp).offset(y = (-30).dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(npc.name, style = suite(FontWeight.ExtraBold, 15), color = gg.text, maxLines = 1)
                    if (isBoss) {
                        Box(Modifier.clip(CircleShape).background(gg.danger.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text("보스", style = suite(FontWeight.ExtraBold, 10), color = gg.danger)
                        }
                    }
                    Box(Modifier.clip(CircleShape).background(gg.accent.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("${npc.table}단", style = suite(FontWeight.ExtraBold, 10), color = gg.accent)
                    }
                    if (!isBoss) {
                        Box(Modifier.clip(CircleShape).background(gg.surface2)
                            .padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text(Battle.styleName(npc.battle), style = suite(FontWeight.ExtraBold, 10), color = gg.textMuted)
                        }
                    }
                }
                Text(
                    when {
                        bossLocked -> "부하들을 모두 이기면 도전할 수 있어요"
                        defeated -> "이미 격파한 상대 — 연습 대결로 XP를 벌 수 있어요"
                        else -> "“${npc.greeting}”"
                    },
                    style = suite(FontWeight.Normal, 13), color = gg.textMuted, maxLines = 1,
                )
            }
        }
        GGButton(
            variant = if (isBoss) GGButtonVariant.DANGER else GGButtonVariant.PRIMARY,
            size = GGButtonSize.MD, enabled = !bossLocked, onClick = onBattle,
        ) {
            Icon(if (bossLocked) Icons.Filled.Lock else Icons.Filled.Shield, null, Modifier.size(16.dp))
            Text(if (bossLocked) "잠겨 있어요" else if (defeated) "다시 대결하기" else "대결하기")
        }
    }
}
