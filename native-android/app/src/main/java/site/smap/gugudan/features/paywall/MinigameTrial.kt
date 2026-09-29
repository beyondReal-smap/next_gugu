package site.smap.gugudan.features.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import site.smap.gugudan.core.PremiumConfig
import site.smap.gugudan.designsystem.*

// 움직이며 놀기(점프·레인·바구니) 무료 체험 — 시작 전 안내, 진행 표시, 체험 종료 패널 (iOS MinigameTrial.swift 대응).
// 체험 규칙(몇 문제, 언제 끝나는지)은 Core 엔진의 trialLeft 가 맡고, 여기서는 보여 주기만 한다.

object MinigameTrial {
    val questions: Int get() = PremiumConfig.MINIGAME_TRIAL_QUESTIONS

    /** 새 판의 체험 문제 수 — 이용권이 있으면 제한 없음(null) */
    fun limit(isPremium: Boolean): Int? = if (isPremium) null else questions

    /** 홈·학습 탭 카드 배지 — 무료 사용자에게만 */
    fun badge(isPremium: Boolean): String? = if (isPremium) null else "${questions}문제 체험"
}

/** 시작 전 안내 — 체험이 몇 문제인지 먼저 알려 끝이 갑작스럽지 않게 한다 */
@Composable
fun TrialNotice() {
    val gg = LocalGG.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(gg.accent.copy(alpha = 0.08f))
            .padding(12.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.ConfirmationNumber, null, tint = gg.accent, modifier = Modifier.size(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("무료 체험 · ${MinigameTrial.questions}문제", style = suite(FontWeight.ExtraBold, 13), color = gg.text)
            Text("평생 이용권이 있으면 제한 없이 놀 수 있어요.", style = suite(FontWeight.Medium, 12), color = gg.textMuted)
        }
    }
}

/** 진행 중 "체험 2/3" — 지금 문제가 체험의 몇 번째인지. left 는 남은 체험 문제 수(지금 문제 포함) */
@Composable
fun TrialProgressPill(left: Int) {
    val gg = LocalGG.current
    val total = MinigameTrial.questions
    val current = (total - left + 1).coerceIn(1, total)
    Row(
        Modifier.clip(CircleShape).background(gg.accent.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clearAndSetSemantics { contentDescription = "무료 체험 ${total}문제 중 ${current}번째" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.ConfirmationNumber, null, tint = gg.accent, modifier = Modifier.size(11.dp))
        Text("체험 $current/$total", style = suite(FontWeight.ExtraBold, 11), color = gg.accent,
            maxLines = 1, softWrap = false)
    }
}

/**
 * 체험 판이 끝났을 때의 패널 — 결제 화면으로 안내한다.
 * "다시 하기"는 두지 않는다: 체험을 이어 붙여 계속 노는 흐름을 막기 위해서다(대표님 결정).
 */
@Composable
fun TrialEndPanel(correct: Int, onUnlock: () -> Unit, onExit: () -> Unit) {
    val gg = LocalGG.current
    val lead = if (correct > 0) "${correct}문제를 맞혔어요! " else ""
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(gg.accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.AutoAwesome, null, tint = gg.accent, modifier = Modifier.size(26.dp))
        }
        Column(
            Modifier.semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("무료 체험은 여기까지!", style = suite(FontWeight.ExtraBold, 18), color = gg.text)
            Text(
                lead + "평생 이용권이 있으면 제한 없이 계속 놀면서 연습할 수 있어요.",
                style = suite(FontWeight.Medium, 13), color = gg.textMuted, textAlign = TextAlign.Center,
            )
        }
        GGButton(
            variant = GGButtonVariant.PRIMARY, size = GGButtonSize.LG,
            modifier = Modifier.fillMaxWidth(), onClick = onUnlock,
        ) {
            Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
            Text("평생 이용권 보기")
        }
        TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("나가기", style = suite(FontWeight.Bold, 14), color = gg.textMuted)
        }
    }
}
