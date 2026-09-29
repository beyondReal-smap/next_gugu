package site.smap.gugudan.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import site.smap.gugudan.core.WeeklyReport
import site.smap.gugudan.designsystem.*

// 이번 주 학습 리포트 (iOS WeeklyReportCard.swift 이식) — 보호자가 한눈에 보는 최근 7일 요약

@Composable
fun WeeklyReportCard(report: WeeklyReport, onReview: (() -> Unit)?) {
    val gg = LocalGG.current
    val maxSolved = maxOf(1, report.days.maxOfOrNull { it.solved } ?: 1)

    Column(
        Modifier.fillMaxWidth().ggCard().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 헤더 + 지난주 대비
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).semantics { heading() },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.BarChart, null, tint = gg.accent, modifier = Modifier.size(16.dp))
                Text("이번 주 학습", style = suite(FontWeight.ExtraBold, 15), color = gg.text)
            }
            if (report.correct > 0 || report.prevCorrect > 0) {
                val d = report.correctDelta
                Text(
                    when {
                        d > 0 -> "지난주보다 정답 +$d"
                        d < 0 -> "지난주보다 정답 −${-d}"
                        else -> "지난주와 같아요"
                    },
                    style = suite(FontWeight.Bold, 12),
                    color = if (d > 0) gg.success else gg.textMuted,
                )
            }
        }

        // 7일 막대 — 아래는 정답, 위는 오답. 문제를 안 푼 날은 짧은 회색 막대
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            report.days.forEachIndexed { i, day ->
                val isToday = i == report.days.lastIndex
                val weekday = WeeklyReport.weekdayLabel(day.date)
                Column(
                    Modifier.weight(1f).clearAndSetSemantics {
                        contentDescription = "${weekday}요일${if (isToday) ", 오늘" else ""}: 정답 ${day.correct}개, 오답 ${day.wrong}개"
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.BottomCenter) {
                        if (day.solved == 0) {
                            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(gg.surface2))
                        } else {
                            val h = 72f * day.solved / maxSolved
                            val wrongH = h * day.wrong / day.solved
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp))) {
                                Box(Modifier.fillMaxWidth().height(wrongH.dp).background(gg.danger.copy(alpha = 0.55f)))
                                Box(Modifier.fillMaxWidth().height((h - wrongH).dp)
                                    .background(if (isToday) gg.accent else gg.accent.copy(alpha = 0.65f)))
                            }
                        }
                    }
                    Text(weekday, style = suite(if (isToday) FontWeight.ExtraBold else FontWeight.Bold, 11),
                        color = if (isToday) gg.accent else gg.textMuted)
                }
            }
        }

        // 요약 수치
        val minutes = Math.round(report.studyMs / 60_000.0).toInt()
        val minutesLabel = if (report.studyMs > 0 && minutes == 0) "1분 미만" else "${minutes}분"
        Row {
            SummaryStat(Modifier.weight(1f), "${report.activeDays}일", "학습한 날")
            SummaryStat(Modifier.weight(1f), "${report.correct}개", "정답")
            SummaryStat(Modifier.weight(1f), report.accuracy?.let { "$it%" } ?: "—", "정확도")
            SummaryStat(Modifier.weight(1f), minutesLabel, "푼 시간")
        }

        // 자주 틀린 문제
        if (report.topMisses.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("자주 틀린 문제", style = suite(FontWeight.Bold, 12), color = gg.textMuted, modifier = Modifier.weight(1f))
                    if (onReview != null) {
                        PressableCard(onClick = onReview) {
                            Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) {
                                Text("복습하기", style = suite(FontWeight.ExtraBold, 12), color = gg.accent)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    report.topMisses.forEach { miss ->
                        Row(
                            Modifier.clip(RoundedCornerShape(10.dp)).background(gg.danger.copy(alpha = 0.1f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .clearAndSetSemantics {
                                    contentDescription = "${miss.key.replace("x", " 곱하기 ")}, ${miss.count}번 틀림"
                                },
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(miss.key.replace("x", " × "), style = suite(FontWeight.ExtraBold, 14), color = gg.danger)
                            Text("${miss.count}번", style = suite(FontWeight.Bold, 11), color = gg.danger.copy(alpha = 0.8f))
                        }
                    }
                }
            }
        }

        // 보호자 한마디
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(gg.accent.copy(alpha = 0.08f))
                .padding(12.dp)
                .clearAndSetSemantics { contentDescription = "보호자 한마디. ${report.parentTip}" },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.FormatQuote, null, tint = gg.accent, modifier = Modifier.size(16.dp))
            Text(report.parentTip, style = suite(FontWeight.Medium, 13), color = gg.text)
        }
    }
}

@Composable
private fun SummaryStat(modifier: Modifier, value: String, label: String) {
    val gg = LocalGG.current
    Column(
        modifier.clearAndSetSemantics { contentDescription = "$label $value" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = suite(FontWeight.ExtraBold, 17), color = gg.text, maxLines = 1)
        Text(label, style = suite(FontWeight.Bold, 11), color = gg.textMuted)
    }
}
