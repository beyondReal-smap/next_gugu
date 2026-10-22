package site.smap.gugudan.core

import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

// 원격 푸시(FCM) 규칙 — 순수 로직만 둔다 (iOS Core/PushModels.swift 와 같은 규칙·문구).
// 단일 기준: .claude/plans/native-push-app-spec.md
//
// - 알림을 켜기 전에는 토큰을 발급하지 않는다. 원격 항목(공지·보호자 소식·광고) 중 하나라도 켜야 발급한다.
// - 광고 동의는 서버가 기록(ack)한 뒤에만 켜진 것으로 본다 → 그때만 promo 토픽을 구독한다.
// - 보호자 학습 소식은 토픽이 아니라 서버가 기기별로 보낸다.

/** 사용자가 고른 원격 알림 설정 (PUSH_SETTINGS_KEY, 기기 전용 파일 — 백업 제외) */
@Serializable
data class PushSettings(
    /** 공지 알림 — notice 토픽 */
    val serviceOptIn: Boolean = false,
    /** 보호자 학습 소식 — 서버가 주간 리포트 도착을 기기별로 보낸다 */
    val learningOptIn: Boolean = false,
    /** 이벤트·혜택(광고) 수신 동의 — 서버 기록(ack)이 끝난 경우에만 true */
    val marketingOptIn: Boolean = false,
    /** 서버가 기록한 동의 시각(ISO-8601) — 설정 화면 "yyyy.MM.dd 수신 동의" 표시용 */
    val marketingOptInAt: String? = null,
    /** 끄기는 즉시 반영하고 서버 기록은 나중에 — 서버에 거부를 아직 못 알렸으면 true (앱 실행마다 재시도) */
    val pendingMarketingRevoke: Boolean = false,
    /** 설정 알림 섹션의 1회 안내("새 기능: 공지 알림…")를 닫았거나 공지를 켠 적이 있으면 true — 다시 보이지 않는다 */
    val noticeHintDismissed: Boolean = false,
) {
    /** 설정 알림 섹션 맨 위 1회 안내 — 공지 알림이 꺼져 있고 아직 닫지 않았을 때만 */
    val showsNoticeHint: Boolean get() = !serviceOptIn && !noticeHintDismissed

    /**
     * 실제로 적용할 설정 — 보호자 학습 소식은 기록 보관 계정(이메일 연결 + 이용권)에서만 받을 수 있다.
     * 자격이 없어지면(로그아웃 등) 저장값은 그대로 두고 꺼진 것으로 다룬다 — 다시 자격이 생기면 이어서 받는다.
     */
    fun effective(learningEligible: Boolean): PushSettings =
        if (learningEligible || !learningOptIn) this else copy(learningOptIn = false)

    /** 원격 항목 중 하나라도 켜져 있는지 — 하나라도 켜져야 토큰을 발급한다 */
    val anyRemote: Boolean get() = serviceOptIn || learningOptIn || marketingOptIn

    /** 이 설치에서 처음 만든 pushInstallId 라면 광고 동의를 이어받지 않는다 (백업 복원·기기 이전 대비) */
    fun resetForNewInstall(): PushSettings =
        copy(marketingOptIn = false, marketingOptInAt = null, pendingMarketingRevoke = false)

    /** 계정 삭제 — 서버 purge 가 동의 기록을 지웠으므로 광고 동의만 초기화한다. 공지 토글은 유지 */
    fun resetForAccountDeletion(): PushSettings =
        copy(marketingOptIn = false, marketingOptInAt = null, pendingMarketingRevoke = false)

    /** 서버 ack 를 받은 동의 */
    fun marketingGranted(recordedAtIso: String): PushSettings =
        copy(marketingOptIn = true, marketingOptInAt = recordedAtIso, pendingMarketingRevoke = false)

    /** 끄기 — 서버 결과를 기다리지 않고 즉시 끈다. 서버 기록은 대기열로 */
    fun marketingRevokedLocally(): PushSettings =
        copy(marketingOptIn = false, marketingOptInAt = null, pendingMarketingRevoke = true)
}

/** 기기에 묶인 등록 상태 (PUSH_STATE_KEY, 기기 전용 파일 — 백업 제외) */
@Serializable
data class PushRegistrationState(
    /** 마지막으로 구독에 성공한 토픽 — desired 와의 차이만 구독/해제한다 */
    val lastSubscribed: Set<String> = emptySet(),
    /** 토큰을 발급받았는지 — 모두 끄면 deleteToken 으로 지운다 */
    val tokenIssued: Boolean = false,
    /** 서버에 등록된 계정(userId) — 로그아웃·삭제 전에 기존 JWT 로 해제할 대상 */
    val registeredUserId: String? = null,
    /** 마지막 등록 내용의 지문 (토큰·계정·설정의 SHA-256) — 토큰 원문은 저장하지 않는다 */
    val registeredSignature: String? = null,
    /** 마지막 등록 시각 (epoch ms) — 하루 1회 재등록 */
    val lastRegisteredAtMs: Long? = null,
    /** 로그아웃 직전 해제가 실패해 다음 동기화에서 반드시 다시 등록해야 함 */
    val needsReregister: Boolean = false,
    /**
     * pushInstallId 를 새로 만든 설치 — 예전 식별자로 구독해 둔 promo 가 Firebase 에 남아 있을 수 있어
     * 다음에 Firebase 를 쓸 때 한 번 해제한다 (명세 "새 pushInstallId 생성 시 promo 해제 1회")
     */
    val promoCleanupPending: Boolean = false,
) {
    val isRegistered: Boolean get() = registeredUserId != null
}

object PushTopics {
    const val NOTICE = "notice"
    const val NOTICE_ANDROID = "notice-android"
    const val NOTICE_IOS = "notice-ios"
    const val PROMO = "promo"

    /** 지금 구독해야 하는 토픽. 보호자 학습 소식은 토픽이 아니다(서버 기기별 발송) */
    fun desired(settings: PushSettings, platform: String = "android"): Set<String> = buildSet {
        if (settings.serviceOptIn) {
            add(NOTICE)
            add(if (platform == "ios") NOTICE_IOS else NOTICE_ANDROID)
        }
        if (settings.marketingOptIn) add(PROMO)
    }

    data class Diff(val subscribe: Set<String>, val unsubscribe: Set<String>) {
        val isEmpty: Boolean get() = subscribe.isEmpty() && unsubscribe.isEmpty()
    }

    fun diff(desired: Set<String>, lastSubscribed: Set<String>): Diff =
        Diff(subscribe = desired - lastSubscribed, unsubscribe = lastSubscribed - desired)
}

/** 알림 data `link` 의 이동 대상 — 허용 목록 밖은 홈으로 */
enum class PushRoute(val link: String) {
    HOME("home"),
    LEARN("learn"),
    PROFILE("profile"),
    PROFILE_REPORT("profile/report"),
    PAYWALL("paywall"),
    SETTINGS_NOTIFICATIONS("settings/notifications");

    companion object {
        /** 허용 목록에 있으면 그 화면, 모르는 값·없음은 home (호출부가 isKnown 으로 로그를 남긴다) */
        fun parse(link: String?): PushRoute = known(link) ?: HOME

        fun isKnown(link: String?): Boolean = known(link) != null

        private fun known(link: String?): PushRoute? {
            val value = link?.trim() ?: return null
            return entries.firstOrNull { it.link == value }
        }
    }
}

/**
 * 알림 data `kind` — 채널 선택과 표시 여부를 정한다.
 * 콘솔 발송: notice·promo. 서버 발송(api/push_templates.py): weekly_report·premium_granted·premium_revoked·
 * account_notice·marketing_reconfirm·test. 명세 초안의 report·account·reconfirm 도 같은 채널로 받는다.
 */
object PushKind {
    const val NOTICE = "notice"
    const val PROMO = "promo"
    const val WEEKLY_REPORT = "weekly_report"
    const val REPORT = "report"

    /** 종류별 알림 채널 — 주간 리포트만 보호자 소식, 광고만 광고 채널, 나머지(모르는 종류 포함)는 공지 채널(FCM 기본 채널) */
    fun channelFor(kind: String?): String = when (kind) {
        PROMO -> PushChannels.PROMOTION
        WEEKLY_REPORT, REPORT -> PushChannels.GUARDIAN_REPORT
        else -> PushChannels.SERVICE_NOTICE
    }

    /**
     * 받은 메시지를 보여 줄지 — 광고는 로컬 동의가 꺼져 있으면 버린다 (토픽 해제가 늦거나 실패한 경우의 2차 차단).
     */
    fun shouldDisplay(kind: String?, settings: PushSettings): Boolean =
        kind != PROMO || settings.marketingOptIn

    /**
     * 소리·배너 없이 알림 목록에만 둘지 — 광고는 앱을 쓰는 중이면 항상 조용히,
     * 나머지는 세션·미니게임·어드벤처 진행 중일 때 조용히 (아이의 집중을 깨지 않는다).
     */
    fun isQuiet(kind: String?, appInForeground: Boolean, playing: Boolean): Boolean =
        (kind == PROMO && appInForeground) || playing
}

object PushChannels {
    /** 로컬 학습 알림 (기존 그대로) */
    const val LEARNING_REMINDER = "learning_reminder"
    /** 공지·서비스 알림 — FCM 기본 채널 */
    const val SERVICE_NOTICE = "service_notice"
    const val GUARDIAN_REPORT = "guardian_report"
    const val PROMOTION = "promotion"
}

/** 광고 동의 상태 전이 — 서버 등록 응답과 로컬 설정이 다를 때 */
object PushConsent {
    enum class Reconcile {
        /** 둘이 같다 */
        NONE,
        /** 로컬은 껐는데 서버에 동의가 남아 있다 — 서버에 거부를 보낸다 */
        SEND_REVOKE,
        /** 로컬은 켰는데 서버에 기록이 없다 — 기록 없는 동의로는 광고를 받지 않으므로 로컬을 끈다 */
        DROP_LOCAL,
    }

    fun reconcile(local: PushSettings, serverMarketingOptIn: Boolean): Reconcile = when {
        local.marketingOptIn == serverMarketingOptIn -> Reconcile.NONE
        local.marketingOptIn -> Reconcile.DROP_LOCAL
        else -> Reconcile.SEND_REVOKE
    }
}

/** 등록 필요 여부 — 내용이 바뀌었거나, 하루가 지났거나, 로그아웃 정리가 실패했을 때 */
object PushRegistration {
    const val REREGISTER_INTERVAL_MS = 24L * 60 * 60 * 1000

    /** 토큰 원문을 저장하지 않으려고 지문만 남긴다 */
    fun signature(
        token: String,
        userId: String,
        settings: PushSettings,
        localReminderEnabled: Boolean,
    ): String {
        val raw = "$token|$userId|${settings.serviceOptIn}|${settings.learningOptIn}|$localReminderEnabled"
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun needsRegister(state: PushRegistrationState, signature: String, nowMs: Long): Boolean {
        if (state.needsReregister || state.registeredSignature != signature) return true
        val last = state.lastRegisteredAtMs ?: return true
        return nowMs - last >= REREGISTER_INTERVAL_MS || nowMs < last
    }
}

/** 보호자 확인 게이트 — 출생연도 4자리. 값은 저장하지 않는다 */
object GuardianGate {
    const val MIN_AGE = 19
    const val MAX_AGE = 100

    /** 현재 연도 기준 만 19세 이상 ~ 100세 이하 (2026년이면 1926~2007년생) */
    fun isValidBirthYear(input: String, currentYear: Int): Boolean {
        if (input.length != 4 || !input.all { it in '0'..'9' }) return false
        val age = currentYear - input.toInt()
        return age in MIN_AGE..MAX_AGE
    }
}

/** 동의 일시 표시·처리 결과 문구 (iOS 와 글자 단위로 같게) */
object PushCopy {
    const val SENDER = "구구 어드벤처"

    private val shortDate = DateTimeFormatter.ofPattern("yyyy.MM.dd")
    private val longDate = DateTimeFormatter.ofPattern("yyyy년 M월 d일")

    /** 서버 recordedAt(ISO-8601) → 기기 시간대의 날짜. 해석하지 못하면 null */
    fun localDate(iso: String?, zone: ZoneId): LocalDate? {
        if (iso == null) return null
        return try {
            OffsetDateTime.parse(iso).atZoneSameInstant(zone).toLocalDate()
        } catch (e: DateTimeParseException) {
            try {
                Instant.parse(iso).atZone(zone).toLocalDate()
            } catch (e2: DateTimeParseException) {
                null
            }
        }
    }

    fun shortDate(date: LocalDate): String = date.format(shortDate)
    fun longDate(date: LocalDate): String = date.format(longDate)

    /** 설정 화면 광고 토글 부제 (켜짐) */
    fun consentSubtitle(date: LocalDate): String = "${shortDate(date)} 수신 동의"

    const val MARKETING_OFF_SUBTITLE = "새 기능·이벤트 소식을 받아요. 보호자 확인 후 켤 수 있어요."

    const val GRANTED_TITLE = "수신 동의 완료"
    const val REVOKED_TITLE = "수신 거부 완료"
    const val FAILED_MESSAGE = "지금은 처리할 수 없어요. 잠시 후 다시 시도해 주세요."

    fun grantedMessage(date: LocalDate, sender: String = SENDER): String =
        "$sender · ${longDate(date)}\n이벤트·혜택 알림 수신 동의가 처리됐어요."

    fun revokedMessage(date: LocalDate, sender: String = SENDER): String =
        "$sender · ${longDate(date)}\n이벤트·혜택 알림 수신 거부가 처리됐어요."
}
