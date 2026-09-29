package site.smap.gugudan.features.learn

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.smap.gugudan.core.Hints
import site.smap.gugudan.core.KoreanReading
import site.smap.gugudan.core.Problems
import site.smap.gugudan.core.VoiceLines
import site.smap.gugudan.designsystem.*
import site.smap.gugudan.services.Speech
import site.smap.gugudan.store.LocalGame

// 구구단 표 보기 (iOS TableSheet.swift 이식) — 연습 전에 한 단 전체를 눈과 귀로 익힌다.
// "소리로 외우기"는 전통 구구단 읽기("칠 팔은 오십육")로 한 줄씩 읽고, 읽는 줄을 강조한다.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableSheet(table: Int, onDismiss: () -> Unit, onPractice: () -> Unit) {
    val gg = LocalGG.current
    val game = LocalGame.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val stars = game.state.tableMastery[table]?.stars ?: 0
    val tip = Hints.tableTip(table)
    fun rowKey(b: Int) = VoiceLines.chant(table, b).key
    val chanting = Speech.isSpeaking && Speech.speakingKey?.startsWith("c-${table}x") == true

    DisposableEffect(Unit) { onDispose { Speech.stop() } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = gg.bg) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).navigationBarsPadding().readableWidth(560.dp)) {
            // 헤더
            Column(
                Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)
                    .clearAndSetSemantics { contentDescription = "${table}단, 별 ${stars}개" },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$table", style = suite(FontWeight.ExtraBold, 34), color = gg.text)
                    Text("단", style = suite(FontWeight.Bold, 20), color = gg.textMuted,
                        modifier = Modifier.padding(bottom = 5.dp, start = 2.dp))
                }
                StarsView(stars, size = 16.dp)
            }

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 비법
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(gg.warning.copy(alpha = 0.1f))
                        .border(1.dp, gg.warning.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Filled.Lightbulb, null, tint = gg.warning, modifier = Modifier.size(18.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${table}단 비법 · ${tip.title}", style = suite(FontWeight.ExtraBold, 14), color = gg.text)
                        Text(tip.full, style = suite(FontWeight.Bold, 14), color = gg.textMuted)
                    }
                }

                // 표 — 한 줄을 누르면 그 줄만 다시 듣는다
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (Problems.MIN_B..Problems.MAX_B).forEach { b ->
                        val speaking = Speech.speakingKey == rowKey(b)
                        val bg by animateColorAsState(if (speaking) gg.accent.copy(alpha = 0.12f) else gg.surface, label = "row")
                        val border by animateColorAsState(if (speaking) gg.accent.copy(alpha = 0.5f) else gg.border, label = "rowBorder")
                        val chant = KoreanReading.chant(table, b)
                        PressableCard(
                            modifier = Modifier.clearAndSetSemantics {
                                contentDescription = "$table 곱하기 ${KoreanReading.withTopic(b)} ${table * b}"
                                onClick("소리로 듣기") { Speech.speak(VoiceLines.chant(table, b)); true }
                            },
                            onClick = { Speech.speak(VoiceLines.chant(table, b)) },
                        ) {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg)
                                    .border(1.dp, border, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    buildAnnotatedString {
                                        withStyle(SpanStyle(color = gg.textMuted, fontSize = 22.sp)) { append("$table × $b = ") }
                                        withStyle(SpanStyle(color = if (speaking) gg.accent else gg.text, fontSize = 24.sp,
                                            fontWeight = FontWeight.ExtraBold)) { append("${table * b}") }
                                    },
                                    style = suite(FontWeight.Bold, 22),
                                    modifier = Modifier.weight(1f),
                                )
                                Text(chant, style = suite(FontWeight.Bold, 13),
                                    color = if (speaking) gg.accent else gg.textMuted.copy(alpha = 0.8f))
                            }
                        }
                    }
                }
            }

            // 소리로 외우기 / 연습
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GGButton(
                    variant = GGButtonVariant.SURFACE, size = GGButtonSize.LG,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (chanting) Speech.stop()
                        else Speech.speak((Problems.MIN_B..Problems.MAX_B).map { VoiceLines.chant(table, it) })
                    },
                ) {
                    Icon(if (chanting) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp, null, Modifier.size(20.dp))
                    Text(if (chanting) "멈추기" else "소리로 외우기")
                }
                GGButton(
                    variant = GGButtonVariant.PRIMARY, size = GGButtonSize.LG, modifier = Modifier.weight(1f),
                    onClick = { Speech.stop(); onPractice() },
                ) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(20.dp))
                    Text("${table}단 연습")
                }
            }
        }
    }
}
