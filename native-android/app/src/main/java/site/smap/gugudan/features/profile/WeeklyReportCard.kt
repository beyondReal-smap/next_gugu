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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import site.smap.gugudan.R
import site.smap.gugudan.core.Problems
import site.smap.gugudan.core.SpokenMath
import site.smap.gugudan.core.WeeklyReport
import site.smap.gugudan.designsystem.*
import site.smap.gugudan.services.rememberStrings

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
                Text(stringResource(R.string.this_week), style = suite(FontWeight.ExtraBold, 15), color = gg.text)
            }
            if (report.correct > 0 || report.prevCorrect > 0) {
                val d = report.correctDelta
                Text(
                    when {
                        d > 0 -> pluralStringResource(R.plurals.correct_answers_vs_last_week, d, d)
                        d < 0 -> pluralStringResource(R.plurals.correct_answers_vs_last_week_2, -d, -d)
                        else -> stringResource(R.string.same_as_last_week)
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
                // 낭독은 요일 전체 이름("화요일" / "Tuesday" / "火曜日") — iOS 와 같은 문장
                val dayLabel = stringResource(
                    if (isToday) R.string.today_correct_wrong else R.string.correct_wrong,
                    WeeklyReport.weekdayName(day.date), day.correct, day.wrong,
                )
                Column(
                    Modifier.weight(1f).clearAndSetSemantics {
                        contentDescription = dayLabel
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
        val minutesLabel = if (report.studyMs > 0 && minutes == 0) stringResource(R.string.under_1_min)
            else pluralStringResource(R.plurals.min, minutes, minutes)
        Row {
            SummaryStat(Modifier.weight(1f), pluralStringResource(R.plurals.days, report.activeDays, report.activeDays),
                stringResource(R.string.days_practiced))
            SummaryStat(Modifier.weight(1f), pluralStringResource(R.plurals.answers, report.correct, report.correct),
                stringResource(R.string.correct))
            SummaryStat(Modifier.weight(1f), report.accuracy?.let { "$it%" } ?: "—", stringResource(R.string.accuracy))
            SummaryStat(Modifier.weight(1f), minutesLabel, stringResource(R.string.time_spent))
        }

        // 자주 틀린 문제
        if (report.topMisses.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.often_missed), style = suite(FontWeight.Bold, 12), color = gg.textMuted, modifier = Modifier.weight(1f))
                    if (onReview != null) {
                        PressableCard(onClick = onReview) {
                            Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.review), style = suite(FontWeight.ExtraBold, 12), color = gg.accent)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    report.topMisses.forEach { miss ->
                        // 곱셈식은 앱 언어의 낭독 라벨 (iOS 와 같다 — 형식이 어긋난 키는 그대로 읽는다)
                        val fact = Problems.parseKey(miss.key)?.let { SpokenMath.factLabel(it.a, it.b) } ?: miss.key
                        val missLabel = stringResource(R.string.times_missed, fact, miss.count)
                        Row(
                            Modifier.clip(RoundedCornerShape(10.dp)).background(gg.danger.copy(alpha = 0.1f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .clearAndSetSemantics {
                                    contentDescription = missLabel
                                },
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(miss.key.replace("x", " × "), style = suite(FontWeight.ExtraBold, 14), color = gg.danger)
                            Text(pluralStringResource(R.plurals.misses, miss.count, miss.count), style = suite(FontWeight.Bold, 11), color = gg.danger.copy(alpha = 0.8f))
                        }
                    }
                }
            }
        }

        // 보호자 한마디
        val parentTip = report.parentTip(rememberStrings())
        val tipLabel = stringResource(R.string.tip_for_parents, parentTip)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(gg.accent.copy(alpha = 0.08f))
                .padding(12.dp)
                .clearAndSetSemantics { contentDescription = tipLabel },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.FormatQuote, null, tint = gg.accent, modifier = Modifier.size(16.dp))
            Text(parentTip, style = suite(FontWeight.Medium, 13), color = gg.text)
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
