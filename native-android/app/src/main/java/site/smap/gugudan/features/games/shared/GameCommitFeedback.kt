package site.smap.gugudan.features.games.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import site.smap.gugudan.core.CommitResult
import site.smap.gugudan.designsystem.AchievementToast
import site.smap.gugudan.designsystem.LevelUpOverlay
import site.smap.gugudan.services.Sound

// 게임 결과 위 학습 기록 알림 (iOS GameCommitFeedback.swift 대응) — 학습 세션 결과와 같은 공용 부품
// (AchievementToast·LevelUpOverlay, designsystem/Feedback.kt)을 쓴다. 결과 창이 뜰 때 기록한 CommitResult 를 받아,
// 새 업적은 위쪽 토스트로, 레벨 업은 잠깐 뒤 가운데 창으로 보여 준다.
// 게임 화면은 상태 표시줄 뒤까지 그리므로 토스트는 안전 영역 위 끝 아래에 둔다.

object GameCommitFeedbackLayout {
    /** 결과 창이 먼저 보이고 나서 레벨 업 창을 띄우기까지(ms) — 학습 세션 결과와 같은 간격 */
    const val LEVEL_UP_DELAY_MS = 700L
}

/** commit: 이번 판의 학습 기록 결과(아직 없거나 답이 없어 기록하지 않았으면 null) — 판마다 새 값이라 다시 하기 뒤에도 다시 알린다 */
@Composable
fun GameCommitFeedback(commit: CommitResult?, viewport: GameViewport) {
    var levelUp by remember { mutableStateOf(false) }
    LaunchedEffect(commit) {
        levelUp = false
        if (commit == null || !commit.leveledUp) return@LaunchedEffect
        delay(GameCommitFeedbackLayout.LEVEL_UP_DELAY_MS)
        Sound.levelUp()
        levelUp = true
    }
    val top = with(LocalDensity.current) { viewport.insets.top.toDp() }
    Box(Modifier.fillMaxSize()) {
        if (commit != null && commit.unlocked.isNotEmpty()) {
            AchievementToast(
                commit.unlocked,
                Modifier.fillMaxWidth().padding(top = top + 12.dp).padding(horizontal = 20.dp),
            )
        }
        LevelUpOverlay(levelUp, commit?.newLevel ?: 0) { levelUp = false }
    }
}
