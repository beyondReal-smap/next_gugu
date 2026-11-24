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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import site.smap.gugudan.R
import site.smap.gugudan.core.PremiumConfig
import site.smap.gugudan.core.Strings
import site.smap.gugudan.designsystem.*

// 게임(구구 계단·구구 몬스터) 무료 체험 — 시작 전 안내, 진행 표시, 체험 종료 패널 (iOS MinigameTrial.swift 대응).
// 체험 규칙(몇 문제, 언제 끝나는지)은 게임 엔진의 trialLeft 가 맡고, 여기서는 보여 주기만 한다.
// 여러 줄 문구는 낱말 단위 줄바꿈(phraseBreak) — 한국어가 "몬스터/를" 처럼 낱말 중간에서 끊기지 않게.

object MinigameTrial {
    val questions: Int get() = PremiumConfig.MINIGAME_TRIAL_QUESTIONS

    /** 새 판의 체험 문제 수 — 이용권이 있으면 제한 없음(null) */
    fun limit(isPremium: Boolean): Int? = if (isPremium) null else questions

    /** 홈·학습 탭 카드 배지 "N문제 체험" — 무료 사용자에게만 (앱 언어로 그릴 때마다 읽는다) */
    fun badge(strings: Strings, isPremium: Boolean): String? =
        if (isPremium) null else strings.plural(R.plurals.free_problems, questions, questions)
}

/** 시작 전 안내 — 체험이 몇 문제인지 먼저 알려 끝이 갑작스럽지 않게 한다 */
@Composable
fun TrialNotice() {
    val gg = LocalGG.current
    val title = pluralStringResource(R.plurals.free_trial_problems, MinigameTrial.questions, MinigameTrial.questions)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(gg.accent.copy(alpha = 0.08f))
            .padding(12.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.ConfirmationNumber, null, tint = gg.accent, modifier = Modifier.size(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = suite(FontWeight.ExtraBold, 13).phraseBreak(), color = gg.text)
            Text(stringResource(R.string.with_the_lifetime_pass_you_can_play_with), style = suite(FontWeight.Medium, 12).phraseBreak(), color = gg.textMuted)
        }
    }
}

/** 진행 중 "체험 2/3" — 지금 문제가 체험의 몇 번째인지. left 는 남은 체험 문제 수(지금 문제 포함) */
@Composable
fun TrialProgressPill(left: Int) {
    val gg = LocalGG.current
    val total = MinigameTrial.questions
    val current = (total - left + 1).coerceIn(1, total)
    val description = stringResource(R.string.free_trial_problem_of, total, current)
    Row(
        Modifier.clip(CircleShape).background(gg.accent.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.ConfirmationNumber, null, tint = gg.accent, modifier = Modifier.size(11.dp))
        Text(stringResource(R.string.trial, current, total), style = suite(FontWeight.ExtraBold, 11), color = gg.accent,
            maxLines = 1, softWrap = false)
    }
}

/**
 * 체험 문제 수를 다 채워 판이 끝났을 때의 패널 — 결제 화면으로 안내한다.
 * "다시 하기"는 두지 않는다: 체험을 이어 붙여 계속 노는 흐름을 막기 위해서다(대표님 결정).
 * 체험 수 전에 잡히거나 시간이 다 된 판은 이 패널이 아니라 일반 결과 창([다시 하기])을 쓴다(2026-11-24 대표님 결정).
 */
@Composable
fun TrialEndPanel(
    correct: Int,
    onUnlock: () -> Unit,
    onExit: () -> Unit,
    /** 앞 문장 — 체험 단위가 다른 게임(구구 몬스터 = 물리친 몬스터)이 바꿔 넣는다. correct 가 0 이면 쓰지 않는다 */
    lead: @Composable (Int) -> String = { pluralStringResource(R.plurals.you_got_problems_right, it, it) },
    /** 학습 기록으로 받은 XP(구구 계단·구구 몬스터) — null 이면 줄을 그리지 않음 */
    xpEarned: Int? = null,
    /** 오늘의 미션 한 줄(게임 결과 창과 같은 문구) — 빈 문자열이면 그리지 않음 */
    missionNote: String = "",
) {
    val gg = LocalGG.current
    val leadText = if (correct > 0) lead(correct) else ""
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
            Text(stringResource(R.string.that_s_the_end_of_the_free_trial), style = suite(FontWeight.ExtraBold, 18).phraseBreak(),
                color = gg.text, textAlign = TextAlign.Center)
            Text(
                leadText + stringResource(R.string.with_the_lifetime_pass_you_can_keep),
                style = suite(FontWeight.Medium, 13).phraseBreak(), color = gg.textMuted, textAlign = TextAlign.Center,
            )
            if (missionNote.isNotEmpty()) {
                Text(missionNote, style = suite(FontWeight.ExtraBold, 13).phraseBreak(), color = gg.success,
                    textAlign = TextAlign.Center)
            }
        }
        if (xpEarned != null) {
            // 숫자와 XP 단위는 번역하지 않는다(iOS Text(verbatim:) 과 같음)
            Text(
                "+$xpEarned XP", style = suite(FontWeight.ExtraBold, 12), color = gg.accent, maxLines = 1,
                modifier = Modifier.clip(CircleShape).background(gg.accent.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        GGButton(
            variant = GGButtonVariant.PRIMARY, size = GGButtonSize.LG,
            modifier = Modifier.fillMaxWidth(), onClick = onUnlock,
        ) {
            Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
            Text(stringResource(R.string.see_lifetime_pass))
        }
        TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.exit), style = suite(FontWeight.Bold, 14), color = gg.textMuted)
        }
    }
}
