import SwiftUI
import StoreKit

// 설정 — 프로필 탭 톱니바퀴에서 여는 시트.
// 켜기/끄기는 네이티브 스위치로, 여러 값 중 고르기는 칩으로 보여 준다
// (예전에는 "켜짐/꺼짐" 글자를 눌러야 해서 누를 수 있는 곳인지 알기 어려웠다).

struct SettingsView: View {
    /// 기기 테마 — 테마가 '기기 설정'일 때 시트에 명시적으로 건다. 떠 있는 시트는
    /// preferredColorScheme(nil)로 되돌려도 이전 테마를 유지하기 때문이다(iOS 동작, 실측 확인)
    let deviceScheme: ColorScheme
    @Environment(GameStore.self) private var game
    @Environment(ThemeStore.self) private var theme
    @Environment(PrefsStore.self) private var prefs
    @Environment(ReminderStore.self) private var reminder
    @Environment(PremiumStore.self) private var premium
    @Environment(AuthStore.self) private var auth
    @Environment(SyncStore.self) private var sync
    @Environment(PushStore.self) private var push
    @Environment(Router.self) private var router
    @Environment(\.openURL) private var openURL
    @Environment(\.dismiss) private var dismiss

    @State private var restoring = false
    @State private var restoreNotice: String?
    @State private var confirmReset = false
    /// 설정 시트 위에 페이월을 겹쳐 띄운다 — 루트의 페이월 시트는 이 시트가 떠 있는 동안 표시되지 못한다
    @State private var showPaywall = false
    /// 광고 켜기 — 보호자 확인·동의 시트
    @State private var showMarketingConsent = false
    /// App Store 국가(ISO 3166-1 alpha-3, 예: "KOR") — 광고성 알림 토글 노출을 정한다 (PromoVisibility)
    @State private var storefrontCountry: String?
    /// 동의 시트가 닫힌 뒤 띄울 처리 결과 (시트가 떠 있는 동안 알림창을 겹치면 무시된다)
    @State private var pendingConsentAlert: ConsentAlert?
    @State private var consentAlert: ConsentAlert?
    /// 원격 알림 처리 실패 문구 (오프라인 등)
    @State private var pushNotice: String?

    /// 처리 결과 알림창 — 제목·본문은 명세 고정 문구
    struct ConsentAlert: Identifiable {
        let id = UUID()
        let title: String
        let message: String
    }

    private static let notificationsAnchor = "settings.notifications"

    static let dailyGoalOptions = [10, 20, 30, 50]

    var body: some View {
        NavigationStack {
            ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    learningSection
                    displaySection
                    soundSection
                    reminderSection
                    AccountSettingsCard()
                    premiumSection
                    aboutSection
                    resetSection
                }
                .padding(.horizontal, 20)
                .padding(.top, 8)
                .padding(.bottom, 32)
                .readableWidth()
            }
            .task { storefrontCountry = await Storefront.current?.countryCode }
            // 알림 딥링크(settings/notifications)로 열렸으면 알림 섹션으로 내려간다 — 시트가 다 올라온 뒤에
            .onChange(of: router.settingsFocusNotifications, initial: true) { _, focus in
                guard focus else { return }
                router.settingsFocusNotifications = false
                Task {
                    try? await Task.sleep(for: .milliseconds(350))
                    withAnimation { proxy.scrollTo(Self.notificationsAnchor, anchor: .top) }
                }
            }
            }
            .scrollIndicators(.hidden)
            .background(Color.gg.bg.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text("설정").font(.suite(.extrabold, 17)).foregroundStyle(Color.gg.text)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("완료") { Haptics.impact(.light); dismiss() }
                        .font(.suite(.bold, 16))
                }
            }
            .toolbarBackground(Color.gg.bg, for: .navigationBar)
            .sheet(isPresented: $showPaywall) { PaywallView() }
            .sheet(isPresented: $showMarketingConsent, onDismiss: {
                consentAlert = pendingConsentAlert
                pendingConsentAlert = nil
            }) {
                PushConsentSheet(
                    grant: { await push.grantMarketing(ctx: pushContext) },
                    onFinish: finishMarketingConsent)
            }
            .alert(item: $consentAlert) { alert in
                Alert(title: Text(alert.title), message: Text(alert.message), dismissButton: .default(Text("확인")))
            }
        }
        // 이미 떠 있는 시트에는 루트의 색상 모드 변경이 전파되지 않는다 —
        // 이 화면에서 테마를 바꾸므로 시트에도 직접 걸어 즉시 반영한다('기기 설정'도 nil 대신 실제 기기 테마로)
        .preferredColorScheme(theme.colorScheme ?? deviceScheme)
    }

    // MARK: - 학습

    private var learningSection: some View {
        SettingsGroup(title: String(localized: "settings.group.learning", defaultValue: "학습", comment: "설정 화면 구역 제목 — 학습 관련 설정")) {
            VStack(alignment: .leading, spacing: 12) {
                SettingsLabel(icon: "target", title: String(localized: "하루 목표"),
                              subtitle: String(localized: "하루에 맞힐 정답 수예요. 목표를 채우면 그날 알림은 쉬어요."))
                ChoiceChips(
                    options: Self.dailyGoalOptions,
                    selected: game.state.dailyGoal,
                    label: { String(localized: "\($0)개") }
                ) { goal in
                    game.setDailyGoal(goal)
                    Task { await reminder.reschedule(state: game.state) }
                }
                .padding(.leading, 36)
            }
            .padding(.horizontal, 20).padding(.vertical, 16)

            SettingsDivider()
            VStack(alignment: .leading, spacing: 12) {
                SettingsLabel(icon: "multiply", title: String(localized: "곱하는 수", comment: "설정 — 각 단에서 몇까지 곱할지 고르는 항목 제목"),
                              subtitle: String(localized: "각 단을 몇까지 곱할지 골라요. 9를 고르면 19단도 19 × 9까지 나와요.", comment: "설정 — 곱하는 수 상한 설명"))
                ChoiceChips(
                    options: Problems.maxBOptions,
                    selected: prefs.maxMultiplier,
                    label: { String(localized: "\($0)까지", comment: "설정 — 곱하는 수 상한 선택지 (예: 9까지)") }
                ) { prefs.maxMultiplier = $0 }
                .padding(.leading, 36)
            }
            .padding(.horizontal, 20).padding(.vertical, 16)

            if AppLanguage.current.supportsReadAloud {
                SettingsDivider()
                ToggleRow(icon: "speaker.wave.2.bubble", title: String(localized: "문제 읽어주기"),
                          // 효과음과 같은 ambient 세션이라 무음 스위치를 따른다 — 켰는데 안 들리는 이유를 미리 알린다
                          subtitle: String(localized: "새 문제가 나오면 선생님 목소리로 읽어 줘요. 무음 모드에서는 들리지 않아요."),
                          isOn: Binding(get: { prefs.readAloud }, set: { prefs.readAloud = $0 }))
            }
        }
    }

    // MARK: - 화면

    private var displaySection: some View {
        SettingsGroup(title: String(localized: "화면")) {
            VStack(alignment: .leading, spacing: 12) {
                SettingsLabel(icon: "circle.lefthalf.filled", title: String(localized: "테마"), subtitle: nil)
                ChoiceChips(
                    options: Theme.allCases,
                    selected: theme.theme,
                    label: { ThemeStore.label($0) }
                ) { theme.theme = $0 }
                .padding(.leading, 36)
            }
            .padding(.horizontal, 20).padding(.vertical, 16)
            SettingsDivider()
            // 앱 언어는 iOS 설정 > 구구 어드벤처 > 언어에서 바꾼다 — 바꾸면 시스템이 앱을 다시 시작한다
            LinkRow(icon: "globe", title: String(localized: "언어"), value: AppLanguage.current.nativeName) {
                open(UIApplication.openSettingsURLString)
            }
        }
    }

    // MARK: - 소리와 진동

    private var soundSection: some View {
        SettingsGroup(title: String(localized: "소리와 진동")) {
            ToggleRow(icon: "speaker.wave.2.fill", title: String(localized: "효과음"), subtitle: nil,
                      isOn: Binding(get: { prefs.soundOn }, set: { prefs.soundOn = $0 }))
            SettingsDivider()
            ToggleRow(icon: "iphone.radiowaves.left.and.right", title: String(localized: "진동"), subtitle: String(localized: "버튼을 누르거나 정답을 맞힐 때 톡 울려요."),
                      isOn: Binding(get: { prefs.hapticsOn }, set: { prefs.hapticsOn = $0 }))
        }
    }

    // MARK: - 알림

    private var reminderSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            SettingsGroup(title: String(localized: "알림")) {
                // 기존 사용자 1회 안내 — 공지가 꺼져 있고 아직 닫지 않았을 때만 (공지를 켜거나 닫으면 사라진다)
                if push.isAvailable, push.settings.showsNoticeHint {
                    NoticeHintRow(onDismiss: { push.dismissNoticeHint() })
                    SettingsDivider()
                }
                ToggleRow(icon: "bell.fill", title: String(localized: "학습 알림"),
                          subtitle: String(localized: "하루 한 번, 오늘의 구구단을 알려 드려요. 밤에는 보내지 않아요."),
                          isOn: Binding(get: { reminder.settings.enabled }, set: setReminder))
                if reminder.settings.enabled {
                    SettingsDivider()
                    reminderHourRow
                }
                // 원격 알림 — Firebase 구성 파일이 없는 디버그 빌드에서는 숨긴다
                if push.isAvailable {
                    SettingsDivider()
                    ToggleRow(icon: "megaphone.fill", title: PushCopy.noticeTitle,
                              subtitle: PushCopy.noticeSubtitle,
                              isOn: Binding(get: { push.settings.notice }, set: setNotice))
                    SettingsDivider()
                    ToggleRow(icon: "chart.bar.doc.horizontal", title: PushCopy.guardianTitle,
                              subtitle: guardianEligible ? PushCopy.guardianSubtitle : PushCopy.guardianIneligible,
                              isOn: Binding(get: { push.settings.guardianReportActive(eligible: guardianEligible) },
                                            set: setGuardianReport))
                        .disabled(!guardianEligible)
                    if PromoVisibility.showsToggle(storefrontCountry: storefrontCountry, marketingOn: push.settings.marketing) {
                        SettingsDivider()
                        ToggleRow(icon: "gift.fill", title: PushCopy.marketingTitle,
                                  subtitle: marketingSubtitle,
                                  isOn: Binding(get: { push.settings.marketing }, set: setMarketing))
                    }
                }
            }
            .id(Self.notificationsAnchor)
            // 앱에서는 켰지만 시스템 설정에서 꺼진 경우 — 앱이 대신 켤 수 없어 설정으로 안내한다 (원격 토글도 같다)
            if reminder.deniedBySystem || push.deniedBySystem {
                HStack(spacing: 8) {
                    Text("설정 앱에서 구구 어드벤처 알림이 꺼져 있어요.")
                        .font(.suite(.medium, 12)).foregroundStyle(Color.gg.warning)
                        .fixedSize(horizontal: false, vertical: true)
                    Spacer(minLength: 4)
                    Button("설정 열기") { Haptics.impact(.light); open(UIApplication.openNotificationSettingsURLString) }
                        .font(.suite(.bold, 12)).foregroundStyle(Color.gg.accent)
                        .frame(minHeight: 44)
                }
            }
            if let pushNotice {
                Text(pushNotice)
                    .font(.suite(.medium, 12)).foregroundStyle(Color.gg.warning)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Text(PushCopy.sectionFooter)
                .font(.suite(.medium, 12)).foregroundStyle(Color.gg.textMuted)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    // MARK: - 원격 알림

    private var guardianEligible: Bool { PushStore.guardianEligible(auth: auth, premium: premium) }

    /// 켜짐이면 서버가 기록한 동의 날짜 "{yyyy.MM.dd} 수신 동의"
    private var marketingSubtitle: String {
        if push.settings.marketing, let at = push.settings.marketingAt { return PushCopy.marketingSubtitleOn(at) }
        return PushCopy.marketingSubtitleOff
    }

    private var pushContext: PushStore.Context {
        .current(auth: auth, premium: premium, reminder: reminder)
    }

    private func setNotice(_ on: Bool) {
        pushNotice = nil
        Task { _ = await push.setNotice(on, ctx: pushContext) }
    }

    private func setGuardianReport(_ on: Bool) {
        pushNotice = nil
        Task { _ = await push.setGuardianReport(on, ctx: pushContext) }
    }

    /// 켜기는 보호자 확인 → 동의 → 서버 기록을 거치고, 끄기는 즉시 (결과 알림창)
    private func setMarketing(_ on: Bool) {
        pushNotice = nil
        if on {
            showMarketingConsent = true
            return
        }
        Task {
            let date = await push.revokeMarketing(ctx: pushContext)
            consentAlert = ConsentAlert(title: PushCopy.revokedTitle, message: PushCopy.revokedBody(date))
        }
    }

    private func finishMarketingConsent(_ outcome: PushStore.ConsentOutcome?) {
        switch outcome {
        case let .granted(date):
            pendingConsentAlert = ConsentAlert(title: PushCopy.grantedTitle, message: PushCopy.grantedBody(date))
        case .failed:
            pushNotice = PushCopy.consentFailed
        case .permissionDenied, nil:
            // 권한 거부는 위의 시스템 설정 안내가 맡는다
            break
        }
        showMarketingConsent = false
    }

    /// 알림 시각 — 야간 발송을 막기 위해 8~20시 안에서만 고른다
    private var reminderHourRow: some View {
        let hour = reminder.settings.hour
        return HStack(spacing: 12) {
            Image(systemName: "clock").foregroundStyle(Color.gg.textMuted).frame(width: 24)
            Text("알림 시각").font(.suite(.bold, 15)).foregroundStyle(Color.gg.text)
            Spacer()
            Button { changeReminderHour(by: -1) } label: {
                Image(systemName: "minus").font(.system(size: 13, weight: .bold)).frame(width: 36, height: 36)
                    .background(Color.gg.surface2, in: Circle())
                    .frame(width: 44, height: 44)   // 누르는 영역은 44pt (보이는 원은 36pt 그대로)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(hour <= ReminderPlanner.allowedHours.lowerBound)
            .accessibilityLabel("한 시간 앞당기기")
            Text(ReminderPlanner.hourLabel(hour))
                .font(.suite(.bold, 14)).foregroundStyle(Color.gg.accent).monospacedDigit()
                .frame(minWidth: 64)
            Button { changeReminderHour(by: 1) } label: {
                Image(systemName: "plus").font(.system(size: 13, weight: .bold)).frame(width: 36, height: 36)
                    .background(Color.gg.surface2, in: Circle())
                    .frame(width: 44, height: 44)   // 누르는 영역은 44pt (보이는 원은 36pt 그대로)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(hour >= ReminderPlanner.allowedHours.upperBound)
            .accessibilityLabel("한 시간 늦추기")
        }
        .foregroundStyle(Color.gg.text)
        .padding(.horizontal, 20).padding(.vertical, 10)
    }

    private func setReminder(_ on: Bool) {
        Task {
            if on {
                await reminder.enable(hour: reminder.settings.hour, state: game.state)
            } else {
                await reminder.disable()
            }
        }
    }

    private func changeReminderHour(by delta: Int) {
        Haptics.impact(.light)
        Task { await reminder.setHour(reminder.settings.hour + delta, state: game.state) }
    }

    // MARK: - 이용권

    private var premiumSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            SettingsGroup(title: String(localized: "이용권")) {
                if premium.isPremium {
                    HStack(spacing: 12) {
                        Image(systemName: "sparkles").foregroundStyle(Color.gg.accent).frame(width: 24)
                        Text("평생 이용권").font(.suite(.bold, 15)).foregroundStyle(Color.gg.text)
                        Spacer()
                        Text("이용 중").font(.suite(.extrabold, 12)).foregroundStyle(Color.gg.accent)
                            .padding(.horizontal, 10).padding(.vertical, 4)
                            .background(Color.gg.accent.opacity(0.12), in: Capsule())
                    }
                    .padding(.horizontal, 20).padding(.vertical, 16)
                } else {
                    LinkRow(icon: "sparkles",
                            title: premium.isOneCoinPrice ? String(localized: "평생 이용권 · 500엔 동전 하나")
                                                          : String(localized: "평생 이용권 · 커피 한 잔 값"),
                            value: premium.price) {
                        showPaywall = true
                    }
                }
                SettingsDivider()
                LinkRow(icon: "arrow.clockwise", title: String(localized: "구매 복원"), value: restoring ? String(localized: "확인 중…") : nil,
                        action: restorePurchase)
                SettingsDivider()
                LinkRow(icon: "doc.text", title: String(localized: "이용약관"), value: nil) { open(PremiumConfig.Legal.terms) }
                SettingsDivider()
                LinkRow(icon: "checkmark.shield", title: String(localized: "개인정보처리방침"), value: nil) { open(PremiumConfig.Legal.privacy) }
            }
            if let restoreNotice {
                Text(restoreNotice)
                    .font(.suite(.medium, 12)).foregroundStyle(Color.gg.textMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    /// 이미 보유한 상품은 새 구매 플로우가 뜨지 않으므로, 복원 경로에서도
    /// 서버 구매 등록과 보호자 권한을 함께 시도한다 (PaywallView.onRestore 와 동일).
    private func restorePurchase() {
        guard !restoring else { return }
        restoring = true
        restoreNotice = nil
        Task {
            let ok = await premium.restore()
            restoring = false
            restoreNotice = ok ? String(localized: "구매를 확인했어요. 기록 보관을 켜는 중이에요.") : String(localized: "복원할 구매 내역이 없어요.")
            if ok { await sync.enableGuardianSync(auth: auth) }
        }
    }

    // MARK: - 앱 정보

    private var aboutSection: some View {
        SettingsGroup(title: String(localized: "앱 정보")) {
            HStack(spacing: 12) {
                Image(systemName: "info.circle").foregroundStyle(Color.gg.textMuted).frame(width: 24)
                Text("버전").font(.suite(.bold, 15)).foregroundStyle(Color.gg.text)
                Spacer()
                Text(Self.versionLabel).font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted).monospacedDigit()
            }
            .padding(.horizontal, 20).padding(.vertical, 16)
            .accessibilityElement(children: .combine)
        }
    }

    static var versionLabel: String {
        let info = Bundle.main.infoDictionary
        guard let version = info?["CFBundleShortVersionString"] as? String,
              let build = info?["CFBundleVersion"] as? String else {
            // 버전 키가 없으면 빌드 설정 결함이다 — 디버그에서는 바로 멈춰 드러내고(음성 파일 누락과 같은 정책),
            // 출시 빌드에서는 정보 화면만 "?"로 둔다
            assertionFailure("Info.plist 에 버전 키가 없습니다 — project.yml 의 MARKETING_VERSION/CURRENT_PROJECT_VERSION 확인")
            return "?"
        }
        return "\(version) (\(build))"
    }

    // MARK: - 기록 초기화

    private var resetSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            if !confirmReset {
                GGButton(variant: .ghost, size: .md, action: { confirmReset = true }) {
                    Image(systemName: "arrow.counterclockwise")
                    Text("학습 기록 초기화").foregroundStyle(Color.gg.danger)
                }
            } else {
                Text("레벨·별·업적·기록이 모두 처음으로 돌아가요. 되돌릴 수 없어요.")
                    .font(.suite(.bold, 13)).foregroundStyle(Color.gg.text)
                    .fixedSize(horizontal: false, vertical: true)
                HStack(spacing: 8) {
                    GGButton(variant: .surface, size: .md, action: { confirmReset = false }) { Text("취소") }
                    GGButton(variant: .danger, size: .md, action: {
                        game.resetProgress()
                        confirmReset = false
                    }) { Text("초기화 확인") }
                }
            }
        }
    }

    private func open(_ url: String) {
        if let u = URL(string: url) { openURL(u) }
    }
}

// MARK: - 설정 행 구성 요소

/// 섹션 제목 + 카드
private struct SettingsGroup<Content: View>: View {
    let title: String
    @ViewBuilder var content: () -> Content
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted)
                .accessibilityAddTraits(.isHeader)
            VStack(spacing: 0) { content() }
                .ggCard(padding: 0)
        }
    }
}

private struct SettingsDivider: View {
    var body: some View { Rectangle().fill(Color.gg.border).frame(height: 1).padding(.leading, 56) }
}

private struct SettingsLabel: View {
    let icon: String
    let title: String
    let subtitle: String?
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon).foregroundStyle(Color.gg.textMuted).frame(width: 24)
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.suite(.bold, 15)).foregroundStyle(Color.gg.text)
                if let subtitle {
                    Text(subtitle).font(.suite(.medium, 12)).foregroundStyle(Color.gg.textMuted)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }
}

private struct ToggleRow: View {
    let icon: String
    let title: String
    let subtitle: String?
    @Binding var isOn: Bool
    var body: some View {
        Toggle(isOn: $isOn) {
            SettingsLabel(icon: icon, title: title, subtitle: subtitle)
        }
        .tint(Color.gg.accent)
        .padding(.horizontal, 20).padding(.vertical, 14)
        .onChange(of: isOn) { _, _ in Haptics.selection() }
    }
}

/// 알림 섹션 맨 위 한 줄 안내 + 닫기
private struct NoticeHintRow: View {
    var onDismiss: () -> Void
    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            Image(systemName: "sparkles").foregroundStyle(Color.gg.accent).frame(width: 24)
            Text(PushCopy.noticeHint)
                .font(.suite(.semibold, 13)).foregroundStyle(Color.gg.text)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 4)
            Button {
                Haptics.impact(.light)
                withAnimation { onDismiss() }
            } label: {
                Image(systemName: "xmark").font(.system(size: 12, weight: .bold)).foregroundStyle(Color.gg.textMuted)
                    .frame(width: 28, height: 28)
                    .background(Color.gg.surface2, in: Circle())
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(PushCopy.noticeHintDismiss)
        }
        .padding(.leading, 20).padding(.trailing, 8).padding(.vertical, 6)
    }
}

private struct LinkRow: View {
    let icon: String
    let title: String
    let value: String?
    var action: () -> Void
    var body: some View {
        Button { Haptics.impact(.light); action() } label: {
            HStack(spacing: 12) {
                Image(systemName: icon).foregroundStyle(Color.gg.textMuted).frame(width: 24)
                Text(title).font(.suite(.bold, 15)).foregroundStyle(Color.gg.text)
                Spacer()
                if let value {
                    Text(value).font(.suite(.bold, 14)).foregroundStyle(Color.gg.accent)
                }
                Image(systemName: "chevron.right").font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Color.gg.textMuted)
            }
            .padding(.horizontal, 20).padding(.vertical, 16)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
