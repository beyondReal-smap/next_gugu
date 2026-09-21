package site.smap.gugudan.features.adventure

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import site.smap.gugudan.core.adventure.Shop
import site.smap.gugudan.core.adventure.ShopColor
import site.smap.gugudan.core.adventure.ShopHat
import site.smap.gugudan.designsystem.*
import site.smap.gugudan.services.Haptics
import site.smap.gugudan.services.Sound
import site.smap.gugudan.store.LocalAdventure

// 꾸미기 상점 (iOS ShopView 이식) — 구매 즉시 장착(원탭)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(onDismiss: () -> Unit) {
    val gg = LocalGG.current
    val adventure = LocalAdventure.current
    var notice by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = gg.bg,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 헤더
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("꾸미기 상점", style = suite(FontWeight.ExtraBold, 22), color = gg.text, modifier = Modifier.weight(1f))
                Pill(bg = gg.warning.copy(alpha = 0.15f), fg = gg.warning) {
                    Icon(Icons.Filled.Star, null, Modifier.size(14.dp))
                    Text("${adventure.progress.starShards}")
                }
                Spacer(Modifier.width(8.dp))
                PressableCard(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, "닫기", tint = gg.textMuted, modifier = Modifier.size(20.dp))
                }
            }

            // 미리보기 — 캡슐+눈+모자
            Column(
                Modifier.fillMaxWidth().ggCard(16.dp).padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(contentAlignment = Alignment.TopCenter) {
                    Box(
                        Modifier.padding(top = 12.dp).size(width = 76.dp, height = 104.dp)
                            .clip(RoundedCornerShape(50)).background(hexColor(adventure.equippedColorHex)),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Row(Modifier.padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(2) {
                                Box(Modifier.size(13.dp).clip(CircleShape).background(Color(0xFF20242C)),
                                    contentAlignment = Alignment.Center) {
                                    Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White))
                                }
                            }
                        }
                    }
                    adventure.progress.equippedHat?.let { hatId ->
                        Shop.hat(hatId)?.let { hat ->
                            Text(hat.emoji, style = suite(FontWeight.Bold, 34), modifier = Modifier.offset(y = (-14).dp))
                        }
                    }
                }
                Text("내 캐릭터", style = suite(FontWeight.Bold, 12), color = gg.textMuted)
            }

            // 색상 3열
            Text("몸 색깔", style = suite(FontWeight.Bold, 14), color = gg.textMuted)
            Shop.colors.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { c -> ColorCell(c, Modifier.weight(1f)) { tapColor(adventure, c) { notice = it } } }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            // 모자
            Text("모자", style = suite(FontWeight.Bold, 14), color = gg.textMuted)
            HatRow(null) { tapHat(adventure, null) { notice = it } }
            Shop.hats.forEach { h ->
                HatRow(h) { tapHat(adventure, h) { notice = it } }
            }

            notice?.let {
                Text(it, style = suite(FontWeight.Bold, 13), color = gg.danger,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            Text("별 조각은 어드벤처 월드를 탐험하며 모을 수 있어요 ⭐",
                style = suite(FontWeight.Medium, 12), color = gg.textMuted,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun tapColor(adventure: site.smap.gugudan.store.AdventureStore, c: ShopColor, setNotice: (String?) -> Unit) {
    setNotice(null)
    when {
        adventure.progress.equippedColor == c.id -> {}
        adventure.ownsColor(c.id) -> { adventure.equipColor(c.id); Haptics.selection() }
        adventure.buyColor(c.id) -> { Sound.collect(); Haptics.success() }
        else -> { Haptics.warning(); setNotice("별 조각이 부족해요 — ⭐${c.price}개가 필요해요") }
    }
}

private fun tapHat(adventure: site.smap.gugudan.store.AdventureStore, h: ShopHat?, setNotice: (String?) -> Unit) {
    setNotice(null)
    if (h == null) { adventure.equipHat(null); Haptics.selection(); return }
    when {
        adventure.progress.equippedHat == h.id -> {}
        adventure.ownsHat(h.id) -> { adventure.equipHat(h.id); Haptics.selection() }
        adventure.buyHat(h.id) -> { Sound.collect(); Haptics.success() }
        else -> { Haptics.warning(); setNotice("별 조각이 부족해요 — ⭐${h.price}개가 필요해요") }
    }
}

@Composable
private fun ColorCell(c: ShopColor, modifier: Modifier, onTap: () -> Unit) {
    val gg = LocalGG.current
    val adventure = LocalAdventure.current
    val owned = adventure.ownsColor(c.id)
    val equipped = adventure.progress.equippedColor == c.id

    PressableCard(modifier = modifier, onClick = onTap) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (equipped) gg.accent.copy(alpha = 0.1f) else gg.surface)
                .border(1.dp, if (equipped) gg.accent else gg.border, RoundedCornerShape(16.dp))
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(hexColor(c.hex)),
                contentAlignment = Alignment.Center) {
                if (equipped) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Text(c.name, style = suite(FontWeight.Bold, 12), color = gg.text)
            when {
                equipped -> Text("장착중", style = suite(FontWeight.ExtraBold, 10), color = gg.accent)
                owned -> Text("보유", style = suite(FontWeight.ExtraBold, 10), color = gg.textMuted)
                else -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, null, tint = gg.warning, modifier = Modifier.size(8.dp))
                    Text("${c.price}", style = suite(FontWeight.ExtraBold, 10), color = gg.warning)
                }
            }
        }
    }
}

@Composable
private fun HatRow(h: ShopHat?, onTap: () -> Unit) {
    val gg = LocalGG.current
    val adventure = LocalAdventure.current
    val owned = h?.let { adventure.ownsHat(it.id) } ?: true
    val equipped = adventure.progress.equippedHat == h?.id

    PressableCard(onClick = onTap) {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (equipped) gg.accent.copy(alpha = 0.1f) else gg.surface)
                .border(1.dp, if (equipped) gg.accent else gg.border, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (h != null) {
                Text(h.emoji, style = suite(FontWeight.Bold, 26), modifier = Modifier.width(40.dp))
            } else {
                Icon(Icons.Filled.Block, null, tint = gg.textMuted, modifier = Modifier.width(40.dp).size(26.dp))
            }
            Text(h?.name ?: "모자 없음", style = suite(FontWeight.Bold, 15), color = gg.text, modifier = Modifier.weight(1f))
            when {
                equipped -> Box(Modifier.clip(CircleShape).background(gg.accent.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("장착중", style = suite(FontWeight.ExtraBold, 12), color = gg.accent)
                }
                owned -> Box(Modifier.clip(CircleShape).background(gg.surface2)
                    .padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("장착", style = suite(FontWeight.ExtraBold, 12), color = gg.textMuted)
                }
                h != null -> Row(
                    Modifier.clip(CircleShape).background(gg.warning.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Star, null, tint = gg.warning, modifier = Modifier.size(10.dp))
                    Text("${h.price}", style = suite(FontWeight.ExtraBold, 12), color = gg.warning)
                }
            }
        }
    }
}
