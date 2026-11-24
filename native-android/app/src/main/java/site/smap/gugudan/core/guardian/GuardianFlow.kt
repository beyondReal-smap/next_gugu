package site.smap.gugudan.core.guardian

import site.smap.gugudan.core.GameMode
import site.smap.gugudan.core.PushRoute

// 보호자 흐름 상태 기계, 위험 동작, 알림 이동 대상 (계획서 2-2, 2-3, 2-9 — iOS Core/Guardian/GuardianFlow.swift 대응)

/** 설정 화면으로 열 때 바로 포커스할 하위 영역 */
enum class SettingsFocus { NOTIFICATIONS }

/** 보호자 문을 통과한 뒤 열릴 목적지 */
sealed interface GuardianDestination {
    data object Report : GuardianDestination
    data class Settings(val focus: SettingsFocus? = null) : GuardianDestination
    data object Paywall : GuardianDestination
}

/** 보호자 흐름 단계 — 화면은 사건(GuardianEvent)만 보내고 다음 상태는 GuardianFlow.reduce 가 정한다 */
sealed interface GuardianStep {
    data object Idle : GuardianStep
    /** 아이에게 보이는 "어른이랑 같이 열어요" 카드 */
    data class KidLocked(val mode: GameMode) : GuardianStep
    /** 3초 꾹 누르기 문 카드 (진행률은 화면 지역 상태) */
    data class Holding(val destination: GuardianDestination) : GuardianStep
    /** 보호자 화면 또는 결제 화면이 열려 있는 상태 */
    data class Open(val destination: GuardianDestination) : GuardianStep
}

/** 보호자 흐름 사건 */
sealed interface GuardianEvent {
    /** 탭 머리 보호자 열쇠 누름 */
    data object TapKey : GuardianEvent
    /** 잠긴 모드 타일 누름 */
    data class TapLocked(val mode: GameMode) : GuardianEvent
    /** [보호자 부르기] (잠김 카드·게임 체험 끝 창) */
    data object CallGuardian : GuardianEvent
    /** 3초 꾹 누르기를 끝까지 완료 */
    data object HoldCompleted : GuardianEvent
    /** 화면 읽기 사용자 대안 — 출생연도 확인 통과 */
    data object BirthYearPassed : GuardianEvent
    /** 알림 딥링크 이동 */
    data class Route(val destination: GuardianDestination) : GuardianEvent
    /** 닫기·다음에 할래요·아이 화면으로·뒤로 가기 */
    data object Dismiss : GuardianEvent
    /** 백그라운드에서 앱으로 돌아옴 (awaySeconds = 떠나 있던 초) */
    data class Resumed(val awaySeconds: Int) : GuardianEvent
}

object GuardianFlow {
    const val HOLD_SECONDS = 3.0
    const val RELOCK_AFTER_AWAY_SECONDS = 300

    fun reduce(step: GuardianStep, event: GuardianEvent): GuardianStep = when (step) {
        GuardianStep.Idle -> when (event) {
            GuardianEvent.TapKey -> GuardianStep.Holding(GuardianDestination.Report)
            is GuardianEvent.TapLocked -> GuardianStep.KidLocked(event.mode)
            GuardianEvent.CallGuardian -> GuardianStep.Holding(GuardianDestination.Paywall)
            is GuardianEvent.Route -> GuardianStep.Holding(event.destination)
            GuardianEvent.HoldCompleted,
            GuardianEvent.BirthYearPassed,
            GuardianEvent.Dismiss,
            is GuardianEvent.Resumed,
            -> step
        }

        is GuardianStep.KidLocked -> when (event) {
            GuardianEvent.CallGuardian -> GuardianStep.Holding(GuardianDestination.Paywall)
            GuardianEvent.Dismiss -> GuardianStep.Idle
            is GuardianEvent.Resumed ->
                if (event.awaySeconds >= RELOCK_AFTER_AWAY_SECONDS) GuardianStep.Idle else step
            GuardianEvent.TapKey,
            is GuardianEvent.TapLocked,
            GuardianEvent.HoldCompleted,
            GuardianEvent.BirthYearPassed,
            is GuardianEvent.Route,
            -> step
        }

        is GuardianStep.Holding -> when (event) {
            GuardianEvent.HoldCompleted,
            GuardianEvent.BirthYearPassed,
            -> GuardianStep.Open(step.destination)
            GuardianEvent.Dismiss -> GuardianStep.Idle
            is GuardianEvent.Resumed ->
                if (event.awaySeconds >= RELOCK_AFTER_AWAY_SECONDS) GuardianStep.Idle else step
            GuardianEvent.TapKey,
            is GuardianEvent.TapLocked,
            GuardianEvent.CallGuardian,
            is GuardianEvent.Route,
            -> step
        }

        is GuardianStep.Open -> when (event) {
            is GuardianEvent.Route -> GuardianStep.Open(event.destination)
            GuardianEvent.Dismiss -> GuardianStep.Idle
            is GuardianEvent.Resumed ->
                if (event.awaySeconds >= RELOCK_AFTER_AWAY_SECONDS) GuardianStep.Idle else step
            GuardianEvent.TapKey,
            is GuardianEvent.TapLocked,
            GuardianEvent.CallGuardian,
            GuardianEvent.HoldCompleted,
            GuardianEvent.BirthYearPassed,
            -> step
        }
    }
}

/** 보호자 구역 안에서도 실행 직전 출생연도 확인을 한 번 더 거치는 위험 동작 (계획서 2-3) */
enum class GuardedAction { PURCHASE, RESET_PROGRESS, DELETE_ACCOUNT }

/** 재디자인 V1 3탭 식별자 (U6 에서 Router.AppTab 으로 연결 — core → store 역의존을 피하려고 core 에 둠) */
enum class NavTab { HOME, MAP, TREASURE }

/** 알림 이동 문자열(PushRoute) → 앱 안 목적지 순수 대응표 (계획서 2-9) */
sealed interface NavTarget {
    data class Tab(val tab: NavTab) : NavTarget
    data class Guardian(val destination: GuardianDestination) : NavTarget

    companion object {
        fun from(route: PushRoute, isPremium: Boolean): NavTarget = when (route) {
            PushRoute.HOME -> Tab(NavTab.HOME)
            PushRoute.LEARN -> Tab(NavTab.MAP)
            PushRoute.PROFILE, PushRoute.PROFILE_REPORT -> Guardian(GuardianDestination.Report)
            PushRoute.SETTINGS_NOTIFICATIONS -> Guardian(GuardianDestination.Settings(SettingsFocus.NOTIFICATIONS))
            PushRoute.PAYWALL -> if (isPremium) Tab(NavTab.HOME) else Guardian(GuardianDestination.Paywall)
        }
    }
}
