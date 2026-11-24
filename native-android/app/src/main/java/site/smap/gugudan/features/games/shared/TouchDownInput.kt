package site.smap.gugudan.features.games.shared

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput

// 닿는 순간 반응하는 멀티터치 입력 (Unity 시험판 TapInput.cs·iOS TouchDownCatcher 대응)
// 탭 제스처(clickable·detectTapGestures)는 손가락을 뗄 때 반응하고, 두 손가락 번갈아 치기·아주 짧은 톡 치기를 놓칠 수 있다.
// 포인터 이벤트를 직접 받아 새 손가락이 닿는 사건(down)마다 한 번씩 화면 좌표(px)를 넘긴다 — 프레임 상태를 비교하지 않으므로
// 한 프레임 안에 닿았다 떨어진 짧은 탭도 빠지지 않는다. 게임은 받은 좌표를 큐에 쌓았다가 다음 프레임에 꺼내 처리한다
// (입력 → 시간 → 그리기 순서).

/** 손가락이 닿은 곳(이 요소 좌표, px). 손가락마다 한 번. onDown 이 바뀌면 입력을 다시 건다(엔진 참조처럼 고정된 함수를 넘길 것) */
fun Modifier.onTouchDown(onDown: (Offset) -> Unit): Modifier = pointerInput(onDown) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            for (change in event.changes) {
                if (change.changedToDownIgnoreConsumed()) onDown(change.position)
            }
        }
    }
}

/** 창 뒤 게임이 눌리지 않게 모든 포인터를 받아 삼킨다(쉬는 창·결과 창의 어두운 막). 접근성 동작은 만들지 않는다 */
fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Main).changes.forEach { it.consume() }
        }
    }
}
