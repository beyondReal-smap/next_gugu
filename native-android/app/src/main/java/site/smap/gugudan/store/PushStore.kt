package site.smap.gugudan.store

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import site.smap.gugudan.core.PushConsent
import site.smap.gugudan.core.PushCopy
import site.smap.gugudan.core.PushRegistration
import site.smap.gugudan.core.PushRegistrationState
import site.smap.gugudan.core.PushSettings
import site.smap.gugudan.core.PushTopics
import site.smap.gugudan.services.LearningApiException
import site.smap.gugudan.services.PushApi
import site.smap.gugudan.services.PushDeviceRequest
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

// 원격 푸시 수명주기 (iOS Store/PushStore.swift 대응). 단일 기준: .claude/plans/native-push-app-spec.md
//
// - 시작·포그라운드 복귀·설정 변경: 원격 항목이 하나라도 켜져 있고 권한이 있으면 토큰 확보 → 서버 등록(내용이 바뀌었거나
//   하루가 지났을 때만) → 광고 거부 대기열 처리 → 토픽 차이 동기화(해제 먼저, 구독 나중). 실패는 다음 동기화에서 다시 시도한다.
// - 원격 항목을 모두 끄면 토픽 해제 → 서버 해제 → 토큰 삭제.
// - 로그아웃은 세션을 지우기 전에 기존 JWT 로 서버 해제(동기화 잠금 대기까지 포함해 5초 제한), 실패하면 다음 동기화에서
//   새 계정으로 다시 등록한다.
// - 알림 거부·Firebase·서버 실패는 앱 기능에 영향을 주지 않는다 — 기록만 남긴다.
// - 바깥 세계(저장소·Firebase·서버·권한·계정)는 PushDependencies.kt 의 인터페이스로 받는다 — JVM 단위 테스트가 가짜로 바꾼다.
// - 상태(settings·state)는 메인 스레드에서만 바뀐다(scope = lifecycleScope). 잠금은 동시에 돌지 않게 하는 순서용이다.

class PushStore(
    private val storage: PushStorage,
    private val messaging: PushMessaging,
    private val server: PushServer,
    private val permission: PushPermission,
    private val account: PushAccount,
    private val scope: CoroutineScope,
    /** 보호자 학습 소식 자격 — 이메일 연결 + 이용권(기록 보관 계정) */
    private val learningEligible: () -> Boolean,
    /** 로컬 학습 알림 켜짐 여부 — 서버에는 참고용으로만 보낸다 */
    private val localReminderEnabled: () -> Boolean,
    private val log: PushLogger = AndroidPushLogger,
    /** 로그아웃 전 해제 대기 한도 — 잠금 대기 + 서버 응답을 합친 시간 */
    private val signOutTimeoutMs: Long = SIGN_OUT_UNREGISTER_TIMEOUT_MS,
    /** 토픽 구독·해제 호출 하나의 대기 한도 */
    private val topicTimeoutMs: Long = TOPIC_TIMEOUT_MS,
) {
    /** 이 설치의 푸시 전용 식별자 — 학습 installId 와 달리 계정이 바뀌어도 유지한다 */
    val installId: String

    var settings: PushSettings by mutableStateOf(PushSettings())
        private set

    /** 원격 알림을 켜 두었지만 시스템에서 알림이 꺼진 상태 — 설정 화면이 시스템 설정으로 안내한다 */
    var deniedBySystem: Boolean by mutableStateOf(false)
        private set

    private var state = PushRegistrationState()
    private val mutex = Mutex()

    init {
        val stored = storage.loadInstallId()?.takeIf(::isUuid)
        val loaded = (storage.loadSettings() ?: PushSettings()).let {
            // 안내 필드가 생기기 전에 공지를 켜 둔 설치 — 이미 켠 적이 있으니 안내를 다시 띄우지 않는다
            if (it.serviceOptIn && !it.noticeHintDismissed) it.copy(noticeHintDismissed = true) else it
        }
        if (stored == null) {
            // 이 설치에서 새로 만든 식별자 — 광고 동의는 이어받지 않는다 (백업 복원·기기 이전 대비).
            // 예전 식별자로 구독해 둔 promo 가 Firebase 에 남아 있을 수 있어 다음에 Firebase 를 쓸 때 한 번 해제한다
            installId = UUID.randomUUID().toString()
            storage.saveInstallId(installId)
            settings = loaded.resetForNewInstall()
            state = PushRegistrationState(promoCleanupPending = true)
            storage.saveSettings(settings)
            storage.saveState(state)
        } else {
            installId = stored
            settings = loaded
            state = storage.loadState() ?: PushRegistrationState()
        }
    }

    sealed interface MarketingResult {
        data class Recorded(val date: LocalDate, val sender: String) : MarketingResult
        data object Failed : MarketingResult
    }

    // MARK: 설정 토글

    fun setService(on: Boolean) {
        // 공지를 켜면 설정 알림 섹션의 1회 안내도 더 보이지 않는다
        updateSettings(settings.copy(serviceOptIn = on, noticeHintDismissed = settings.noticeHintDismissed || on))
        requestSync()
    }

    fun setLearning(on: Boolean) {
        updateSettings(settings.copy(learningOptIn = on))
        requestSync()
    }

    /** 설정 알림 섹션의 1회 안내를 닫는다 — 기기에 저장돼 다시 보이지 않는다 */
    fun dismissNoticeHint() {
        if (!settings.noticeHintDismissed) updateSettings(settings.copy(noticeHintDismissed = true))
    }

    /** 권한 요청이 거절됐을 때 — 토글은 꺼진 채 시스템 설정 안내를 띄운다 */
    fun markDeniedBySystem() { deniedBySystem = true }

    /**
     * 광고 수신 동의 — 보호자 확인·동의 시트를 거친 뒤 부른다. 서버가 기록(ack)한 뒤에만 켜고 promo 를 구독한다.
     * 실패하면 토글은 꺼진 채로 둔다.
     */
    suspend fun grantMarketing(): MarketingResult {
        val result = mutex.withLock {
            try {
                if (!permission.isAuthorized()) {
                    deniedBySystem = true
                    return@withLock MarketingResult.Failed
                }
                val token = issueToken()
                // 동의는 서버의 기기 행에 기록된다 — 이 계정으로 등록돼 있어야 한다
                val forceRegister = state.registeredUserId != account.userId
                if (!register(token, force = forceRegister)) return@withLock MarketingResult.Failed
                // 보내지 못한 거부가 남아 있으면 먼저 기록한다 — 늦게 도착한 철회가 새 동의를 덮지 않게 (iOS 와 같음)
                sendPendingRevoke()
                if (settings.pendingMarketingRevoke) {
                    log.warn("대기 중인 광고 거부를 먼저 기록하지 못해 동의를 보류")
                    return@withLock MarketingResult.Failed
                }
                val jwt = account.accessToken() ?: return@withLock MarketingResult.Failed
                val response = try {
                    server.recordConsent(jwt, installId, granted = true)
                } catch (e: LearningApiException.Http) {
                    if (e.status != 404) throw e
                    // 서버에서 기기 행이 사라졌다(정리·다른 계정 귀속) — 다시 등록하고 한 번만 재시도
                    if (!register(token, force = true)) return@withLock MarketingResult.Failed
                    server.recordConsent(jwt, installId, granted = true)
                }
                updateSettings(settings.marketingGranted(response.recordedAt))
                log.info("광고 수신 동의 기록 완료")
                try {
                    syncTopics()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 동의는 이미 기록됐다 — promo 구독만 다음 동기화에서 다시 시도한다
                    log.warn("promo 구독 실패 — 다음 동기화에서 재시도: ${e.javaClass.simpleName}")
                }
                MarketingResult.Recorded(
                    PushCopy.localDate(response.recordedAt, ZoneId.systemDefault()) ?: LocalDate.now(),
                    response.sender,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("광고 수신 동의 실패: ${e.javaClass.simpleName} ${e.message}")
                MarketingResult.Failed
            }
        }
        // 다른 항목이 모두 꺼진 채 실패했다면 방금 만든 토큰·등록을 정리한다
        if (result is MarketingResult.Failed) requestSync()
        return result
    }

    /**
     * 광고 수신 거부 — 즉시 끄고 promo 를 해제한다. 서버 기록이 실패하면 대기열에 두고 앱 실행마다 재시도한다.
     * 진행 중인 동기화(토큰·서버 등록이 막혀 오래 걸릴 수 있다)를 기다리지 않는다 — 잠금이 비어 있으면 곧바로
     * promo 해제·서버 기록, 다른 동기화가 돌고 있으면 promo 만 바로 해제하고 서버 기록은 대기열로 넘긴다.
     * 반환하는 날짜는 서버 기록 시각(못 보냈으면 기기 날짜) — 처리 결과 알림창에 쓴다.
     */
    suspend fun revokeMarketing(): MarketingResult.Recorded {
        // 이 순간부터 받은 광고는 수신 단계에서 버린다 (PushKind.shouldDisplay)
        updateSettings(settings.marketingRevokedLocally())
        val recorded = if (mutex.tryLock()) {
            try {
                try {
                    // 해제만 — 다른 토픽 구독(최대 10초씩)이 결과창을 붙잡지 않게 구독은 이어지는 동기화가 한다
                    syncTopics(unsubscribeOnly = true)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.warn("promo 해제 실패 — 다음 동기화에서 재시도: ${e.javaClass.simpleName}")
                }
                sendPendingRevoke()
            } finally {
                mutex.unlock()
            }
        } else {
            unsubscribePromoNow()
            null
        }
        requestSync()
        return recorded ?: MarketingResult.Recorded(LocalDate.now(), PushCopy.SENDER)
    }

    // MARK: 계정 변화

    /**
     * 로그아웃 직전 — 세션을 지우기 **전에** 기존 JWT 로 서버 등록을 해제한다.
     * 5초 제한은 동기화 잠금 대기까지 포함한다 (진행 중인 동기화가 막혀 있어도 로그아웃을 붙잡지 않는다).
     * 실패·초과하면 다음 동기화에서 새 계정으로 다시 등록하도록 표시만 한다 (등록이 새 계정으로 옮겨진다).
     */
    suspend fun unregisterBeforeSignOut() {
        if (!state.isRegistered) return
        val handled = withTimeoutOrNull(signOutTimeoutMs) {
            mutex.withLock {
                if (!state.isRegistered) return@withLock
                // 연결이 막혀도 로그아웃을 붙잡지 않도록 요청은 따로 띄우고 기다리기만 제한한다
                // (차단 I/O 는 취소해도 바로 멈추지 않는다)
                val call = scope.async {
                    val jwt = account.accessToken() ?: throw IllegalStateException("액세스 토큰 없음")
                    server.unregister(jwt, installId, timeoutMs = signOutTimeoutMs.toInt())
                }
                val done = try {
                    call.await()
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.warn("로그아웃 전 기기 해제 실패: ${e.javaClass.simpleName} ${e.message}")
                    false
                }
                state = if (done) {
                    log.info("로그아웃 전 기기 해제 완료")
                    state.copy(registeredUserId = null, registeredSignature = null, needsReregister = false)
                } else {
                    state.copy(needsReregister = true)
                }
                storage.saveState(state)
            }
        }
        if (handled == null) {
            // 잠금 대기 또는 서버 응답이 5초를 넘겼다 — 표시만 남기고 로그아웃을 계속한다
            log.warn("로그아웃 전 기기 해제 시간 초과 — 새 계정으로 다시 등록")
            state = state.copy(needsReregister = true)
            storage.saveState(state)
        }
    }

    /**
     * 계정 삭제 직후 — 서버 purge 가 기기 행·동의 기록을 지웠다. 광고 동의를 초기화하고(promo 는 다음 동기화에서 해제)
     * 새 익명 계정으로 다시 등록하게 한다. 공지·보호자 소식 토글은 유지한다.
     */
    fun onAccountDeleted() {
        updateSettings(settings.resetForAccountDeletion())
        state = state.copy(registeredUserId = null, registeredSignature = null, needsReregister = false)
        storage.saveState(state)
    }

    // MARK: 동기화

    /** 시작·복귀·설정 변경·계정 변경 때 부른다 — 겹쳐 불려도 차례로 한 번씩 돈다 */
    fun requestSync() {
        scope.launch { sync() }
    }

    suspend fun sync() {
        mutex.withLock {
            try {
                runSync()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Firebase(Play 서비스 없음 등)·서버 실패 — 앱 기능과 무관하다. 다음 동기화에서 다시 시도한다
                log.warn("푸시 동기화 실패: ${e.javaClass.simpleName} ${e.message}")
            }
        }
    }

    private suspend fun runSync() {
        val effective = effectiveSettings()
        if (!effective.anyRemote) {
            teardown()
            return
        }
        if (!permission.isAuthorized()) {
            // 토큰·남길 토픽은 그대로 두고(다시 켜면 이어서 받는다) 거부 기록 전송과 철회된 토픽(promo) 해제만 한다
            deniedBySystem = true
            sendPendingRevoke()
            if (state.tokenIssued) syncTopics(unsubscribeOnly = true)
            return
        }
        deniedBySystem = false
        val token = issueToken()
        try {
            register(token, force = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 서버 등록이 실패해도 공지 토픽은 서버와 무관하다 — 토픽 동기화는 계속한다
            log.warn("기기 등록 실패 — 다음 동기화에서 재시도: ${e.javaClass.simpleName} ${e.message}")
        }
        sendPendingRevoke()
        syncTopics()
    }

    /** 원격 항목을 모두 껐을 때 — 토픽 해제 → 서버 해제 → 토큰 삭제. 한 번도 켠 적 없으면 Firebase 를 건드리지 않는다 */
    private suspend fun teardown() {
        if (!state.tokenIssued && state.lastSubscribed.isEmpty() && !state.isRegistered &&
            !settings.pendingMarketingRevoke
        ) return
        sendPendingRevoke()
        syncTopics()
        if (state.isRegistered) {
            val jwt = account.accessToken() ?: return
            try {
                server.unregister(jwt, installId)
            } catch (e: LearningApiException.Http) {
                // 404 = 다른 계정에 귀속된 행 — 해제할 권한이 없다. 토큰을 지우면 서버 발송이 실패하며 정리된다
                if (e.status != 404) throw e
            }
            state = state.copy(registeredUserId = null, registeredSignature = null, needsReregister = false)
            storage.saveState(state)
            log.info("원격 알림 모두 꺼짐 — 서버 기기 해제")
        }
        if (state.tokenIssued && state.lastSubscribed.isEmpty()) {
            messaging.deleteToken()
            state = state.copy(tokenIssued = false)
            storage.saveState(state)
            log.info("원격 알림 모두 꺼짐 — 토큰 삭제")
        }
    }

    private suspend fun issueToken(): String {
        val token = messaging.token()
        if (!state.tokenIssued) {
            state = state.copy(tokenIssued = true)
            storage.saveState(state)
        }
        return token
    }

    /**
     * 서버 등록. 내용(토큰·계정·설정)이 그대로이고 하루가 안 지났으면 건너뛴다.
     * 계정이 없으면(인증 미설정·재로그인 대기) false — 다음 동기화에서 다시 시도한다. 서버 오류는 던진다.
     */
    private suspend fun register(token: String, force: Boolean): Boolean {
        val jwt = account.accessToken() ?: return false
        val userId = account.userId ?: return false
        val effective = effectiveSettings()
        val reminder = localReminderEnabled()
        val signature = PushRegistration.signature(token, userId, effective, reminder)
        val now = System.currentTimeMillis()
        if (!force && !PushRegistration.needsRegister(state, signature, now)) return true

        val response = server.register(
            jwt,
            PushDeviceRequest(
                pushInstallId = installId,
                fcmToken = token,
                platform = "android",
                appVersion = PushApi.appVersion,
                locale = PushApi.normalizedLocale(Locale.getDefault().toLanguageTag()),
                timeZone = PushApi.normalizedTimeZone(ZoneId.systemDefault().id),
                serviceOptIn = effective.serviceOptIn,
                learningOptIn = effective.learningOptIn,
                localReminderEnabled = reminder,
            ),
        )
        state = state.copy(
            registeredUserId = userId,
            registeredSignature = signature,
            lastRegisteredAtMs = now,
            needsReregister = false,
        )
        storage.saveState(state)
        log.info("기기 등록 완료 (서버 광고 동의 ${response.marketingOptIn})")

        // 서버 기준 광고 동의와 맞춘다 — 서버 ON·기기 OFF 면 조용히 켜지 않고 서버에 철회를 보낸다
        when (PushConsent.reconcile(settings, response.marketingOptIn)) {
            PushConsent.Reconcile.NONE -> Unit
            PushConsent.Reconcile.DROP_LOCAL -> {
                log.info("서버에 광고 동의 기록 없음 — 로컬 동의를 끈다")
                updateSettings(settings.copy(marketingOptIn = false, marketingOptInAt = null))
            }
            PushConsent.Reconcile.SEND_REVOKE -> {
                if (!settings.pendingMarketingRevoke) updateSettings(settings.copy(pendingMarketingRevoke = true))
            }
        }
        return true
    }

    /**
     * 대기 중인 광고 거부를 서버에 기록한다. 기록됐으면 그 결과(서버 시각), 보낼 것이 없거나 실패하면 null.
     * 서버에 기기 행이 없으면(404) 거부할 동의도 없으므로 대기열을 비운다.
     */
    private suspend fun sendPendingRevoke(): MarketingResult.Recorded? {
        if (!settings.pendingMarketingRevoke) return null
        if (!state.isRegistered) {
            updateSettings(settings.copy(pendingMarketingRevoke = false))
            return null
        }
        val jwt = account.accessToken() ?: return null
        return try {
            val response = server.recordConsent(jwt, installId, granted = false)
            updateSettings(settings.copy(pendingMarketingRevoke = false))
            log.info("광고 수신 거부 기록 완료")
            MarketingResult.Recorded(
                PushCopy.localDate(response.recordedAt, ZoneId.systemDefault()) ?: LocalDate.now(),
                response.sender,
            )
        } catch (e: LearningApiException.Http) {
            if (e.status == 404) {
                updateSettings(settings.copy(pendingMarketingRevoke = false))
            } else {
                log.warn("광고 수신 거부 기록 실패 — 대기열 유지: ${e.status}")
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("광고 수신 거부 기록 실패 — 대기열 유지: ${e.javaClass.simpleName}")
            null
        }
    }

    /**
     * desired − lastSubscribed 차이만 맞춘다. **해제 먼저, 구독 나중** — 구독이 막혀도 받지 말아야 할 토픽(광고)은 먼저 끊는다.
     * 호출마다 10초 제한. 성공한 것만 기록 — 실패분은 다음 동기화에서 재시도. unsubscribeOnly 면 해제만 한다.
     */
    private suspend fun syncTopics(unsubscribeOnly: Boolean = false) {
        val desired = PushTopics.desired(effectiveSettings())
        if (state.promoCleanupPending && PushTopics.PROMO in desired) {
            // 새로 동의해 다시 구독할 참이다 — 따로 해제할 필요가 없다
            state = state.copy(promoCleanupPending = false)
            storage.saveState(state)
        }
        // 새 식별자 설치면 Firebase 에 남았을지 모르는 promo 를 구독된 것으로 보고 한 번 해제한다
        val known = if (state.promoCleanupPending) state.lastSubscribed + PushTopics.PROMO else state.lastSubscribed
        val diff = PushTopics.diff(desired, known)
        if (diff.isEmpty) return
        var subscribed = state.lastSubscribed
        var cleanupPending = state.promoCleanupPending
        var firstError: Exception? = null
        for (topic in diff.unsubscribe) {
            val error = topicCall(topic) { messaging.unsubscribe(it) }
            if (error == null) {
                subscribed = subscribed - topic
                if (topic == PushTopics.PROMO) cleanupPending = false
            } else if (firstError == null) {
                firstError = error
            }
        }
        if (!unsubscribeOnly) {
            for (topic in diff.subscribe) {
                val error = topicCall(topic) { messaging.subscribe(it) }
                if (error == null) subscribed = subscribed + topic else if (firstError == null) firstError = error
            }
        }
        state = state.copy(lastSubscribed = subscribed, promoCleanupPending = cleanupPending)
        storage.saveState(state)
        log.info("토픽 동기화 — 구독 ${subscribed.sorted()}")
        firstError?.let { throw it }
    }

    /** 잠금 밖에서 promo 만 바로 해제한다. 구독 기록은 잠금 안의 다음 동기화가 맞춘다 (해제는 여러 번 해도 결과가 같다) */
    private suspend fun unsubscribePromoNow() {
        if (PushTopics.PROMO !in state.lastSubscribed) return
        topicCall(PushTopics.PROMO) { messaging.unsubscribe(it) }?.let {
            log.warn("promo 즉시 해제 실패 — 다음 동기화에서 재시도: ${it.javaClass.simpleName}")
        }
    }

    /**
     * 토픽 호출 하나 — 제한 시간 안에 끝나지 않으면 실패로 본다(동기화 전체를 취소하지 않는다). 실패 원인, 성공이면 null.
     * withTimeout 대신 withTimeoutOrNull 을 쓴다 — 시간 초과 예외는 CancellationException 이라 위로 던지면 동기화가 통째로 멈춘다.
     */
    private suspend fun topicCall(topic: String, call: suspend (String) -> Unit): Exception? = try {
        val finished = withTimeoutOrNull(topicTimeoutMs) { call(topic); true }
        if (finished == null) TopicTimeoutException(topic) else null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        e
    }

    private fun effectiveSettings(): PushSettings = settings.effective(learningEligible())

    private fun updateSettings(next: PushSettings) {
        settings = next
        storage.saveSettings(next)
    }

    private fun isUuid(value: String): Boolean =
        runCatching { UUID.fromString(value) }.isSuccess && value.length == 36

    private class TopicTimeoutException(topic: String) : Exception("토픽 응답 시간 초과: $topic")

    private companion object {
        const val SIGN_OUT_UNREGISTER_TIMEOUT_MS = 5_000L
        const val TOPIC_TIMEOUT_MS = 10_000L
    }
}
