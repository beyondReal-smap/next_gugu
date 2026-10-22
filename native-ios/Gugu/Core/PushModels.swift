import Foundation

// 원격 푸시(FCM) 순수 규칙 — 설정 모델·토픽 계산·딥링크 해석·보호자 확인·동의 문구 날짜.
// 네트워크·Firebase 를 모른다. 명세: .claude/plans/native-push-app-spec.md (Android 와 같은 규칙·문구)

enum PushPlatform: String {
    case ios, android
}

// MARK: - 설정 (Persistence gugu.push.v1)

/// 사용자가 고른 원격 알림 항목. 학습 알림(로컬)은 ReminderSettings 가 따로 맡는다.
struct PushSettings: Codable, Equatable {
    /// 공지 알림 — notice 토픽. 기존 사용자도 꺼진 채 시작한다(업데이트만으로 토큰을 만들지 않는다)
    var notice: Bool = false
    /// 보호자 학습 소식 — 서버가 기기별로 보낸다(토픽 아님). 기록 보관 계정에서만 실제로 쓰인다
    var guardianReport: Bool = false
    /// 이벤트·혜택 알림(광고) — 서버가 동의 기록을 확인(ack)한 뒤에만 true 가 된다
    var marketing: Bool = false
    /// 광고 수신 동의 일시(서버 기록 기준) — 설정 화면 "{yyyy.MM.dd} 수신 동의"
    var marketingAt: Date?
    /// 광고 수신 거부를 서버에 아직 알리지 못했다 — 앱 실행마다 다시 보낸다
    var pendingMarketingRevoke: Bool = false
    /// 기존 사용자 1회 안내("새 기능: 공지 알림…")를 닫았거나 공지를 켠 적이 있다 — 다시 보이지 않는다
    var noticeHintDismissed: Bool = false

    /// 설정 알림 섹션 맨 위 1회 안내 — 공지가 꺼져 있고 아직 닫지 않았을 때만
    var showsNoticeHint: Bool { !notice && !noticeHintDismissed }

    /// 보호자 학습 소식이 실제로 켜졌는지 — 토글 값이 남아 있어도 기록 보관 계정이 아니면 끈 것으로 본다
    func guardianReportActive(eligible: Bool) -> Bool { guardianReport && eligible }

    /// 원격 항목 중 하나라도 켜졌는지 — 하나도 없으면 토큰을 만들지 않고, 있던 것도 지운다
    func anyRemote(guardianEligible: Bool) -> Bool {
        notice || guardianReportActive(eligible: guardianEligible) || marketing
    }

    /// 광고 동의를 처음 상태로 — 새 설치 식별자·계정 전환·계정 삭제
    mutating func resetMarketing() {
        marketing = false
        marketingAt = nil
        pendingMarketingRevoke = false
    }

    /// 광고 수신 거부 — 즉시 끄고, 서버 기록은 대기열에 둔다
    mutating func beginMarketingRevoke() {
        marketing = false
        marketingAt = nil
        pendingMarketingRevoke = true
    }

    /// 서버가 광고 동의를 기록했다(ack) — 이때 처음으로 켠다
    mutating func acceptMarketingAck(recordedAt: Date) {
        marketing = true
        marketingAt = recordedAt
        pendingMarketingRevoke = false
    }
}

extension PushSettings {
    /// 필드가 늘기 전 저장본도 읽는다 — 못 읽으면 Persistence 가 격리하고 기본값(모두 꺼짐)으로 시작해 사용자 설정을 잃는다
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        notice = try c.decodeIfPresent(Bool.self, forKey: .notice) ?? false
        guardianReport = try c.decodeIfPresent(Bool.self, forKey: .guardianReport) ?? false
        marketing = try c.decodeIfPresent(Bool.self, forKey: .marketing) ?? false
        marketingAt = try c.decodeIfPresent(Date.self, forKey: .marketingAt)
        pendingMarketingRevoke = try c.decodeIfPresent(Bool.self, forKey: .pendingMarketingRevoke) ?? false
        // 안내가 생기기 전에 이미 공지를 켠 사용자는 기능을 안다 — 나중에 꺼도 안내를 띄우지 않는다
        noticeHintDismissed = try c.decodeIfPresent(Bool.self, forKey: .noticeHintDismissed) ?? notice
    }
}

// MARK: - 설치 기록 (키체인 gugu.push.install.v1, ThisDeviceOnly)

/// 이 기기 설치의 푸시 신원과 등록 상태. 기기 백업으로 새 기기에 옮겨지지 않는다 —
/// 복원된 기기에서는 이 기록이 없으므로 새 식별자를 만들고 광고 동의를 초기화한다.
struct PushInstall: Codable, Equatable {
    /// 푸시 전용 설치 식별자 — 학습 installId(계정이 바뀌면 다시 만든다)와 따로 둔다
    var pushInstallId: String
    /// 지금 FCM 토큰으로 구독을 마친 토픽
    var subscribedTopics: [String] = []
    /// 구독을 맞춘 토큰의 지문 — 토큰이 바뀌면 구독도 새로 해야 한다
    var topicsTokenHash: String?
    /// 마지막 서버 등록 시각 — 하루 한 번 다시 등록한다. nil 이면 서버에 이 기기 행이 없다고 본다
    var registeredAt: Date?
    /// 마지막 등록 때 보낸 값의 지문 — 계정·토큰·항목이 바뀌면 다시 등록한다
    var registeredFingerprint: String?
    /// 다음 기회에 반드시 다시 등록 — 로그아웃 전 해제 실패, 계정 전환 등
    var needsReregister: Bool = false
    /// 새 식별자를 만든 설치 — 이 기기에 남은 FCM 토큰의 promo 구독을 한 번 풀어야 한다
    /// (예전 기록을 잃었으면 구독 목록도 잃어 차이 계산으로는 풀리지 않는다)
    var promoReleasePending: Bool = false

    static func fresh() -> PushInstall {
        PushInstall(pushInstallId: UUID().uuidString.lowercased())
    }
}

extension PushInstall {
    /// 필드가 늘기 전 키체인 기록도 읽는다 — 못 읽으면 새 식별자를 만들고 광고 동의를 초기화하게 된다
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        pushInstallId = try c.decode(String.self, forKey: .pushInstallId)
        subscribedTopics = try c.decodeIfPresent([String].self, forKey: .subscribedTopics) ?? []
        topicsTokenHash = try c.decodeIfPresent(String.self, forKey: .topicsTokenHash)
        registeredAt = try c.decodeIfPresent(Date.self, forKey: .registeredAt)
        registeredFingerprint = try c.decodeIfPresent(String.self, forKey: .registeredFingerprint)
        needsReregister = try c.decodeIfPresent(Bool.self, forKey: .needsReregister) ?? false
        promoReleasePending = try c.decodeIfPresent(Bool.self, forKey: .promoReleasePending) ?? false
    }
}

// MARK: - 토픽

enum PushTopics {
    static let notice = "notice"
    static let promo = "promo"

    static func platformNotice(_ platform: PushPlatform) -> String { "notice-\(platform.rawValue)" }

    /// 공지 토픽 — 한국어는 기존 이름(notice, notice-ios) 그대로, 다른 언어는 언어를 붙인다(notice-en, notice-en-ios).
    /// 공지는 서버가 언어별 토픽으로 따로 보낸다. 앱별 언어를 바꾸면 앱이 다시 시작되며 토픽도 바뀐다.
    static func noticeTopics(platform: PushPlatform, language: AppLanguage) -> Set<String> {
        guard language != .ko else { return [notice, platformNotice(platform)] }
        let base = "\(notice)-\(language.rawValue)"
        return [base, "\(base)-\(platform.rawValue)"]
    }

    /// 지금 설정에서 구독해야 할 토픽. 광고는 서버 ack 로 marketing 이 true 가 된 뒤에만 들어간다.
    /// 보호자 학습 소식은 서버가 기기별로 보내므로 토픽이 아니다.
    static func desired(_ settings: PushSettings, platform: PushPlatform,
                        language: AppLanguage = .current) -> Set<String> {
        var topics = Set<String>()
        if settings.notice {
            topics.formUnion(noticeTopics(platform: platform, language: language))
        }
        if settings.marketing && !settings.pendingMarketingRevoke {
            topics.insert(promo)
        }
        return topics
    }

    /// 바꿀 것만 — 구독할 것 / 해제할 것 (정렬해 결정적으로)
    static func diff(desired: Set<String>, subscribed: Set<String>) -> (subscribe: [String], unsubscribe: [String]) {
        (desired.subtracting(subscribed).sorted(), subscribed.subtracting(desired).sorted())
    }
}

// MARK: - 딥링크

/// 알림 data `link` 허용 목록 — 이 밖의 값은 홈으로 보낸다
enum PushRoute: String, CaseIterable, Equatable {
    case home = "home"
    case learn = "learn"
    case profile = "profile"
    case profileReport = "profile/report"
    case paywall = "paywall"
    case settingsNotifications = "settings/notifications"

    /// 모르는 값·빈 값은 홈 + 로그 (외부 입력이라 그대로 믿지 않는다)
    static func parse(_ link: String?) -> PushRoute {
        let trimmed = link?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if let route = PushRoute(rawValue: trimmed) { return route }
        print("[Push] 알 수 없는 딥링크 — 홈으로:", trimmed.isEmpty ? "(없음)" : String(trimmed.prefix(64)))
        return .home
    }
}

/// 알림 data `kind`. 콘솔 발송은 명세 값(notice·promo·report·account·reconfirm)을,
/// 서버 템플릿 발송은 템플릿 키(weekly_report 등)를 쓰므로 둘 다 받는다.
enum PushKind: Equatable {
    case notice, promo, report, account, reconfirm, unknown

    static func parse(_ raw: String?) -> PushKind {
        switch raw?.trimmingCharacters(in: .whitespacesAndNewlines) {
        case "notice", "test": return .notice
        case "promo": return .promo
        case "report", "weekly_report": return .report
        case "account", "account_notice", "premium_granted", "premium_revoked": return .account
        case "reconfirm", "marketing_reconfirm": return .reconfirm
        default: return .unknown
        }
    }
}

/// 앱이 떠 있을 때 원격 알림을 어떻게 보일지
enum PushForegroundStyle: Equatable {
    /// 배너 + 알림 목록 + 소리
    case full
    /// 알림 목록에만 (배너·소리 없음)
    case listOnly

    /// 광고는 앱 사용 중엔 목록만. 공지·보호자·계정은 배너로 알리되,
    /// 세션·미니게임·어드벤처를 하는 중이면 흐름을 끊지 않게 목록에만 둔다.
    static func decide(kind: PushKind, immersive: Bool) -> PushForegroundStyle {
        if kind == .promo { return .listOnly }
        return immersive ? .listOnly : .full
    }
}

// MARK: - 광고성 알림 노출 범위

enum PromoVisibility {
    /// 광고 동의는 한국 App Store(스토어프런트 KOR)에서만 받는다 — 해외는 아동 대상 광고 동의 규제
    /// (미국 COPPA·EU GDPR 제8조 등)가 달라 토글 자체를 보이지 않는다. 스토어 국가를 모르면 숨긴다.
    /// 이미 켜 둔 사람(국가를 옮긴 경우)에게는 끌 수 있도록 계속 보인다 — 동의 철회 수단은 항상 있어야 한다.
    static func showsToggle(storefrontCountry: String?, marketingOn: Bool) -> Bool {
        marketingOn || storefrontCountry == "KOR"
    }
}

// MARK: - 보호자 확인 (광고 동의 게이트)

enum GuardianGate {
    static let minAge = 19
    static let maxAge = 100

    /// 보호자 출생연도 4자리 — 올해 기준 만 19세 이상 ~ 100세 이하(2026년이면 1926~2007년생).
    /// 값은 확인에만 쓰고 저장하지 않는다.
    static func isValidBirthYear(_ text: String, now: Date = Date(), calendar: Calendar = .current) -> Bool {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count == 4, trimmed.allSatisfy({ $0.isASCII && $0.isNumber }), let year = Int(trimmed) else {
            return false
        }
        let current = calendar.component(.year, from: now)
        return (current - maxAge)...(current - minAge) ~= year
    }
}

// MARK: - 동의 문구 날짜

enum PushDateText {
    /// 설정 부제 "{yyyy.MM.dd} 수신 동의" (en·ja 는 그 언어의 숫자 날짜)
    static func short(_ date: Date, timeZone: TimeZone = .current) -> String {
        formatter(korean: "yyyy.MM.dd", template: "yMMdd", timeZone).string(from: date)
    }

    /// 처리 결과 알림창 "구구 어드벤처 · {yyyy년 M월 d일}" (en·ja 는 그 언어의 긴 날짜)
    static func long(_ date: Date, timeZone: TimeZone = .current) -> String {
        formatter(korean: "yyyy년 M월 d일", template: "yMMMMd", timeZone).string(from: date)  // i18n:skip
    }

    /// 한국어는 Android 와 글자 단위로 같은 고정 형식, 그 밖의 언어는 템플릿으로 그 언어 순서를 따른다
    private static func formatter(korean: String, template: String, _ timeZone: TimeZone) -> DateFormatter {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.timeZone = timeZone
        if AppLanguage.current == .ko {
            f.locale = Locale(identifier: "ko_KR")
            f.dateFormat = korean
        } else {
            f.locale = AppLanguage.formatLocale
            f.setLocalizedDateFormatFromTemplate(template)
        }
        return f
    }
}

// MARK: - 고정 문구 (Android 와 글자 단위로 같다)

enum PushCopy {
    static let sender = String(localized: "구구 어드벤처", comment: "앱 이름 — 알림·동의 문구의 보내는 곳")

    static let noticeTitle = String(localized: "공지 알림", comment: "알림 설정 문구")
    static let noticeSubtitle = String(localized: "업데이트·점검 같은 중요한 소식을 알려 드려요.", comment: "알림 설정 문구")
    static let guardianTitle = String(localized: "보호자 학습 소식", comment: "알림 설정 문구")
    static let guardianSubtitle = String(localized: "매주 학습 리포트가 준비되면 알려 드려요.", comment: "알림 설정 문구")
    static let guardianIneligible = String(localized: "이메일을 연결하고 기록 보관을 켜면 받을 수 있어요.", comment: "알림 설정 문구")
    static let marketingTitle = String(localized: "이벤트·혜택 알림 (광고)", comment: "알림 설정 문구")
    static let marketingSubtitleOff = String(localized: "새 기능·이벤트 소식을 받아요. 보호자 확인 후 켤 수 있어요.", comment: "알림 설정 문구")
    static func marketingSubtitleOn(_ date: Date) -> String {
        String(localized: "\(PushDateText.short(date)) 수신 동의", comment: "광고 알림 동의 날짜 — %@ 는 날짜")
    }
    static let sectionFooter = String(localized: "알림을 꺼도 모든 기능을 그대로 쓸 수 있어요.", comment: "알림 설정 문구")
    /// 기존 사용자 1회 안내 (알림 섹션 맨 위)
    static let noticeHint = String(localized: "새 기능: 공지 알림을 켜면 업데이트·점검 소식을 받을 수 있어요.", comment: "알림 설정 문구")
    static let noticeHintDismiss = String(localized: "안내 닫기", comment: "알림 설정 문구")

    static let gateTitle = String(localized: "보호자 확인", comment: "알림 설정 문구")
    static let gateBody = String(localized: "보호자의 출생연도 4자리를 입력해 주세요.", comment: "알림 설정 문구")
    static let gateInvalid = String(localized: "출생연도를 다시 확인해 주세요.", comment: "알림 설정 문구")

    static let consentTitle = String(localized: "이벤트·혜택 알림 수신 동의 (선택)", comment: "알림 설정 문구")
    static let consentBody = String(localized: "구구 어드벤처의 새 기능, 이벤트, 할인 소식을 앱 알림으로 보내 드려요.\n· 보내는 곳: 구구 어드벤처\n· 받는 시간: 오전 8시 ~ 오후 9시\n· 언제든 설정 > 알림에서 끌 수 있어요.\n동의하지 않아도 앱을 그대로 쓸 수 있어요.", comment: "알림 설정 문구")
    static let consentAgree = String(localized: "동의하고 켜기", comment: "알림 설정 문구")

    static let grantedTitle = String(localized: "수신 동의 완료", comment: "알림 설정 문구")
    static func grantedBody(_ date: Date) -> String {
        String(localized: "\(sender) · \(PushDateText.long(date))\n이벤트·혜택 알림 수신 동의가 처리됐어요.", comment: "광고 알림 동의 처리 결과 — 첫 %@ 앱 이름, 둘째 %@ 날짜")
    }
    static let revokedTitle = String(localized: "수신 거부 완료", comment: "알림 설정 문구")
    static func revokedBody(_ date: Date) -> String {
        String(localized: "\(sender) · \(PushDateText.long(date))\n이벤트·혜택 알림 수신 거부가 처리됐어요.", comment: "광고 알림 거부 처리 결과 — 첫 %@ 앱 이름, 둘째 %@ 날짜")
    }
    static let consentFailed = String(localized: "지금은 처리할 수 없어요. 잠시 후 다시 시도해 주세요.", comment: "알림 설정 문구")

    /// 결과 화면 학습 알림 권유 카드에 덧붙이는 한 줄
    static let reminderOfferNoticeLine = String(localized: "중요한 공지도 함께 알려 드려요.", comment: "알림 설정 문구")
}
