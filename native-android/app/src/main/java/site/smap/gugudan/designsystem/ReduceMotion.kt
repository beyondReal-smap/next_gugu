package site.smap.gugudan.designsystem

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext

// 동작 줄이기(접근성)와 반복 장식 움직임용 시계 — iOS accessibilityReduceMotion 대응.
// 판단 방식은 홈 보물상자(DailyMissionsCard)와 같다: 시스템 "애니메이션 삭제" = 애니메이터 길이 배율 0.

/** 시스템 "애니메이션 삭제"(접근성) — 애니메이터 길이 배율 0. 설정한 적 없는 기기는 키가 없고 기본 배율은 1 */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/**
 * 화면 프레임마다 흐르는 시간(초) — 숨쉬기처럼 끝없이 반복하는 장식 움직임용. running 이 false 면 멈춘 자리에 머문다.
 * 읽는 곳이 그리기 단계(graphicsLayer·drawBehind)면 다시 구성 없이 다시 그리기만 한다
 */
@Composable
fun rememberMotionSeconds(running: Boolean): State<Double> {
    val seconds = remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        // 멈췄던 동안은 흘리지 않고 이어서 — 첫 프레임은 시각만 잡는다
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                if (last >= 0) seconds.doubleValue += (now - last) / 1_000_000_000.0
                last = now
            }
        }
    }
    return seconds
}
