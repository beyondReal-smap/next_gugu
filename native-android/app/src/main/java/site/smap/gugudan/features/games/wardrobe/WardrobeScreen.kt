package site.smap.gugudan.features.games.wardrobe

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.dp
import site.smap.gugudan.R
import site.smap.gugudan.core.games.Accessory
import site.smap.gugudan.designsystem.rememberMotionSeconds
import site.smap.gugudan.designsystem.rememberReduceMotion
import site.smap.gugudan.features.games.shared.GameArt
import site.smap.gugudan.features.games.shared.GameImages
import site.smap.gugudan.features.games.shared.GamePalette
import site.smap.gugudan.features.games.shared.GameViewport
import site.smap.gugudan.features.games.shared.HudBadgeState
import site.smap.gugudan.features.games.shared.JellyButton
import site.smap.gugudan.features.games.shared.LocalGameImages
import site.smap.gugudan.features.games.shared.Motion
import site.smap.gugudan.features.games.shared.PauseLayout
import site.smap.gugudan.features.games.shared.blockTouches
import site.smap.gugudan.features.games.shared.drawGameImage
import site.smap.gugudan.features.games.shared.drawGameImageFit
import site.smap.gugudan.features.games.shared.drawGameText
import site.smap.gugudan.features.games.shared.drawGugu
import site.smap.gugudan.features.games.shared.drawHudBadge
import site.smap.gugudan.features.games.shared.drawPill
import site.smap.gugudan.features.games.shared.gameTextStyle
import site.smap.gugudan.features.games.shared.measureGameText
import site.smap.gugudan.features.games.shared.multiplyFilter
import site.smap.gugudan.features.games.shared.placed
import site.smap.gugudan.features.games.shared.rememberGameImages
import site.smap.gugudan.features.games.shared.rememberGameInsets
import site.smap.gugudan.features.games.shared.rememberGameTextMeasurer
import site.smap.gugudan.services.Haptics
import site.smap.gugudan.services.rememberStrings
import site.smap.gugudan.store.LocalPlayRewards
import kotlin.math.min

// 구구 꾸미기(옷장) 화면 (Unity 시험판 WardrobeScreen.cs 의 화면 구성 이식, iOS WardrobeView.swift 대응)
// 위 [메뉴로]·모은 별 배지, 제목, 가운데 둥근 받침 위 큰 구구(써 보기), 장신구 카드 3×2(값·"가짐"·"쓰는 중"·빛 테두리),
// 아래 젤리 버튼(사기 / 별 N개 더 모아요 / 쓰기 / 벗기). 규칙·상태는 WardrobeModel, 여기서는 그리기와 누름 연결만.
// 좌표는 게임 화면과 같은 설계 px(1080 폭, GameViewport) — 바탕은 화면 전체, 내용은 안전 영역 안.

/** 하늘빛 바탕 */
private val BACKDROP = Color(0xFFCFE9FF)

private val CARD_SIZE = Size(300f, 250f)
private const val CARD_GAP_X = 330f
/** 윗줄·아랫줄 가운데 높이(안전 영역 아래 끝에서 위로 — 엄지 영역) */
private val ROW_Y = listOf(690f, 410f)
/** 고른 카드 빛 테두리(양쪽 합, 설계 px) — 누르는 칸도 이만큼 넓게 */
private const val CARD_GLOW_PAD = 34f
private const val PREVIEW_SIZE = 440f
/**
 * 미리 보기 칸: 위 끝(안전 영역 위에서)~아래 끝(안전 영역 아래에서). 키 큰 화면에서 가운데가 비지 않게
 * 제목 아래~카드 위 사이를 늘어나는 칸으로 잡고 그 가운데에 둔다(1920 높이에서는 위에서 650)
 */
private const val STAGE_TOP = 230f
private const val STAGE_BOTTOM = 850f
/** 카드 색(무채색 젤리에 곱함) — 장신구 순서 */
private val CARD_COLORS = listOf(
    Color(0xFFFFC2CC), Color(0xFFD9F5C5), Color(0xFFCDEBFF), Color(0xFFD6D3E6), Color(0xFFE3D6FF), Color(0xFFFFF0B3),
)

/** 열 때마다 모델을 새로 만든다 — 쓰고 있는 것이 골라진 채로 */
@Composable
fun WardrobeScreen(onClose: () -> Unit) {
    val rewards = LocalPlayRewards.current
    val strings = rememberStrings()
    val model = remember { WardrobeModel(rewards, strings) }
    val images = rememberGameImages()
    remember(images) { images.preload(IMAGES) }

    BackHandler { onClose() }

    // 움직이는 것(통통·흔들림·잠금·배지)이 있을 때만 프레임을 돌린다 — 첫 프레임은 시각만 잡는다
    val running = model.running
    LaunchedEffect(model, running) {
        if (!running) return@LaunchedEffect
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                if (last >= 0) model.advance(min((now - last) / 1_000_000_000.0, 0.1))
                last = now
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(BACKDROP)) {
        val density = LocalDensity.current
        val size = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        val insets = rememberGameInsets()
        val viewport = remember(size, insets) { GameViewport(size, insets) }
        // 빈 곳(구구 무대·여백)을 누른 탭이 아래 화면(메인 탭)으로 새지 않게 맨 아래 층에서 삼킨다.
        // 부모에 걸면 카드·버튼의 손가락 이동까지 소비돼 클릭이 취소되므로, 형제 층으로 둬 위 요소가 안 맞은 곳만 받는다
        Box(Modifier.fillMaxSize().blockTouches())
        CompositionLocalProvider(LocalGameImages provides images) {
            WardrobeContent(model, viewport, images, onClose)
        }
    }
}

@Composable
private fun WardrobeContent(model: WardrobeModel, viewport: GameViewport, images: GameImages, onClose: () -> Unit) {
    val measurer = rememberGameTextMeasurer()
    val u = viewport.unit
    Box(Modifier.fillMaxSize()) {
        TopRow(model, viewport, images, measurer, onClose)

        val title = stringResource(R.string.wardrobe_title)
        Box(
            Modifier
                .placed(viewport.safeTop(0f, -210f), viewport.px(1000f, 120f))
                .semantics {
                    heading()
                    contentDescription = title
                }
                .drawBehind {
                    drawGameText(measurer, title, center, 84 * u, GamePalette.ink, maxWidth = size.width, minScale = 0.6f)
                },
        )

        Preview(model, viewport, images)

        for (item in Accessory.entries) Card(model, item, viewport, images, measurer)

        ActionButton(model, viewport)
    }
}

// MARK: 위 줄: 돌아가기 · 모은 별

@Composable
private fun TopRow(
    model: WardrobeModel,
    viewport: GameViewport,
    images: GameImages,
    measurer: TextMeasurer,
    onClose: () -> Unit,
) {
    val u = viewport.unit
    val back = stringResource(R.string.wardrobe_back)
    Box(
        Modifier
            .placed(viewport.safeTop(-370f, PauseLayout.HUD_ROW_Y), viewport.px(260f, HudBadgeState.HEIGHT))
            .semantics { contentDescription = back }
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                Haptics.impactLight()
                onClose()
            }
            .drawBehind {
                drawPill(images, Rect(Offset.Zero, size))
                // 글꼴(SUITE)에 화살표 기호가 없을 수 있어 글자만
                drawGameText(measurer, back, center, 50 * u, GamePalette.ink, maxWidth = size.width * 0.85f, minScale = 0.6f)
            },
    )
    val starsLabel = stringResource(R.string.game_result_star_total, model.stars)
    val badgeWidth = 280f
    Box(
        Modifier
            .placed(viewport.safeTop(370f, PauseLayout.HUD_ROW_Y), viewport.px(badgeWidth, HudBadgeState.HEIGHT))
            .semantics { contentDescription = starsLabel }
            .drawBehind {
                model.frame   // 배지 통통
                drawHudBadge(model.starsBadge, images, measurer, R.drawable.game_icon_coin, center, badgeWidth, u)
            },
    )
}

// MARK: 큰 구구(써 보기)

@Composable
private fun Preview(model: WardrobeModel, viewport: GameViewport, images: GameImages) {
    val u = viewport.unit
    val wearing = model.previewWearing
    val label = if (wearing == null) {
        stringResource(R.string.wardrobe_preview_bare)
    } else {
        stringResource(R.string.wardrobe_preview_wearing, accessoryName(wearing))
    }
    val safe = viewport.safeRect
    val center = Offset(safe.center.x, (safe.top + STAGE_TOP * u + safe.bottom - STAGE_BOTTOM * u) / 2)
    val box = (PREVIEW_SIZE + 60) * u
    // 받침은 두고 구구만 홈 게임 카드와 같은 숨쉬기(Motion.cardBreath, 동작 줄이기면 멈춤) — 써 보기 통통과 곱한다
    val breathing = !rememberReduceMotion()
    val time = rememberMotionSeconds(running = breathing)
    Box(
        Modifier
            .placed(center, Size(box, box))
            .semantics { contentDescription = label }
            .drawBehind {
                model.frame   // 통통
                val c = Offset(size.width / 2, size.height / 2)
                drawCircle(Color.White.copy(alpha = 0.6f), radius = box / 2, center = Offset(c.x, c.y - 10 * u))
                val breath = if (breathing) Motion.cardBreath(time.value) else Motion.CardBreath(0.0, 1.0)
                translate(top = (-breath.lift).toFloat().dp.toPx()) {
                    scale((model.previewScale * breath.scale).toFloat(), pivot = c) {
                        drawGugu(images, GameArt.GuguPose.HAPPY, model.previewWearing, c, PREVIEW_SIZE * u)
                    }
                }
            },
    )
}

// MARK: 장신구 카드

@Composable
private fun Card(
    model: WardrobeModel,
    item: Accessory,
    viewport: GameViewport,
    images: GameImages,
    measurer: TextMeasurer,
) {
    val u = viewport.unit
    val i = item.ordinal
    val owned = model.owns(item)
    val isSelected = model.selected == item
    val cannotAfford = model.cannotAfford(item)
    val priceText = when {
        model.isEquipped(item) -> stringResource(R.string.wardrobe_wearing)
        owned -> stringResource(R.string.wardrobe_owned)
        else -> "${item.price}"
    }
    val label = "${accessoryName(item)}, $priceText"
    val center = viewport.safeBottom((i % 3 - 1) * CARD_GAP_X, ROW_Y[i / 3])
    Box(
        Modifier
            .placed(center, viewport.px(CARD_SIZE.width + CARD_GLOW_PAD, CARD_SIZE.height + CARD_GLOW_PAD))
            .semantics {
                contentDescription = label
                selected = isSelected
            }
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { model.select(item) }
            .drawBehind {
                val c = Offset(size.width / 2, size.height / 2)
                val w = CARD_SIZE.width * u
                val h = CARD_SIZE.height * u
                val jelly = images[R.drawable.game_card_jelly]
                // 고른 카드: 같은 젤리 모양을 조금 크게 흰색으로 뒤에 깔아 빛 테두리
                if (isSelected) drawGameImage(jelly, c, size)
                drawGameImage(jelly, c, Size(w, h), colorFilter = multiplyFilter(CARD_COLORS[i]))
                drawGameImageFit(images[GameArt.res(item)], Offset(c.x, c.y - 34 * u), Size(190 * u, 120 * u))
                if (owned) {
                    drawGameText(measurer, priceText, Offset(c.x, c.y + 64 * u), 50 * u, GamePalette.ink, maxWidth = 280 * u, minScale = 0.6f)
                } else {
                    drawGameImageFit(images[R.drawable.game_icon_coin], Offset(c.x - 50 * u, c.y + 66 * u), Size(52 * u, 52 * u))
                    // 값은 코인 오른쪽에 왼쪽 맞춤(140 칸 — 가운데 +50)
                    drawLeading(
                        measurer, priceText, left = c.x - 20 * u, centerY = c.y + 64 * u, px = 50 * u,
                        color = if (cannotAfford) GamePalette.wrong else GamePalette.ink,
                    )
                }
            },
    )
}

/** 왼쪽 끝을 맞춘 한 줄 글자(카드 값) */
private fun DrawScope.drawLeading(measurer: TextMeasurer, text: String, left: Float, centerY: Float, px: Float, color: Color) {
    val w = measureGameText(measurer, text, gameTextStyle(px, color)).size.width
    drawGameText(measurer, text, Offset(left + w / 2f, centerY), px, color)
}

// MARK: 아래 큰 버튼

@Composable
private fun ActionButton(model: WardrobeModel, viewport: GameViewport) {
    val u = viewport.unit
    // 사기·쓰기 초록, 벗기 금색, 고르기 전·별 부족 회색
    val color = when (model.action) {
        is WardrobeModel.Action.Buy, WardrobeModel.Action.Wear -> GamePalette.correct
        WardrobeModel.Action.TakeOff -> GamePalette.gold
        WardrobeModel.Action.None, is WardrobeModel.Action.NeedMore -> GamePalette.quit
    }
    JellyButton(
        model.actionTitle, color, GamePalette.ink, fontSize = 60f, jellyScale = 0.75f, unit = u,
        modifier = Modifier
            .placed(viewport.safeBottom(0f, 140f), viewport.px(860f, 160f))
            .graphicsLayer {
                model.frame   // 별 부족 흔들림
                translationX = model.actionShakeX.toFloat() * u
            },
        onClick = model::pressAction,
    )
}

/** 장신구 표시 이름(읽어 주기용) */
@Composable
private fun accessoryName(item: Accessory): String = stringResource(
    when (item) {
        Accessory.RIBBON -> R.string.accessory_ribbon
        Accessory.FLOWER_CROWN -> R.string.accessory_flower_crown
        Accessory.PARTY_HAT -> R.string.accessory_party_hat
        Accessory.PIRATE_HAT -> R.string.accessory_pirate_hat
        Accessory.WIZARD_HAT -> R.string.accessory_wizard_hat
        Accessory.CROWN -> R.string.golden_crown
    },
)

/** 미리 풀어 둘 그림 */
private val IMAGES: List<Int> = listOf(
    R.drawable.game_card_jelly, R.drawable.game_badge_pill, R.drawable.game_icon_coin, GameArt.GuguPose.HAPPY.res,
) + Accessory.entries.map { GameArt.res(it) }
