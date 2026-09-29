import SwiftUI

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
    @Environment(\.openURL) private var openURL
    @Environment(\.dismiss) private var dismiss

    @State private var restoring = false
    @State private var restoreNotice: String?
    @State private var confirmReset = false
    /// 설정 시트 위에 페이월을 겹쳐 띄운다 — 루트의 페이월 시트는 이 시트가 떠 있는 동안 표시되지 못한다
    @State private var showPaywall = false

    static let dailyGoalOptions = [10, 20, 30, 50]

    var body: some View {
        NavigationStack {
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
            .scrollIndicators(.hidden)
            .background(Color.gg.bg.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text("설정").font(.suite(.extrabold, 17)).foregroundStyle(Color.gg.text)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("완료") { dismiss() }
                        .font(.suite(.bold, 16))
                }
            }
            .toolbarBackground(Color.gg.bg, for: .navigationBar)
            .sheet(isPresented: $showPaywall) { PaywallView() }
        }
        // 이미 떠 있는 시트에는 루트의 색상 모드 변경이 전파되지 않는다 —
        // 이 화면에서 테마를 바꾸므로 시트에도 직접 걸어 즉시 반영한다('기기 설정'도 nil 대신 실제 기기 테마로)
        .preferredColorScheme(theme.colorScheme ?? deviceScheme)
    }

    // MARK: - 학습

    private var learningSection: some View {
        SettingsGroup(title: "학습") {
            VStack(alignment: .leading, spacing: 12) {
                SettingsLabel(icon: "target", title: "하루 목표",
                              subtitle: "하루에 맞힐 정답 수예요. 목표를 채우면 그날 알림은 쉬어요.")
                ChoiceChips(
                    options: Self.dailyGoalOptions,
                    selected: game.state.dailyGoal,
                    label: { "\($0)개" }
                ) { goal in
                    game.setDailyGoal(goal)
                    Task { await reminder.reschedule(state: game.state) }
                }
                .padding(.leading, 36)
            }
            .padding(.horizontal, 20).padding(.vertical, 16)

            SettingsDivider()
            ToggleRow(icon: "speaker.wave.2.bubble", title: "문제 읽어주기",
                      // 효과음과 같은 ambient 세션이라 무음 스위치를 따른다 — 켰는데 안 들리는 이유를 미리 알린다
                      subtitle: "새 문제가 나오면 선생님 목소리로 읽어 줘요. 무음 모드에서는 들리지 않아요.",
                      isOn: Binding(get: { prefs.readAloud }, set: { prefs.readAloud = $0 }))
        }
    }

    // MARK: - 화면

    private var displaySection: some View {
        SettingsGroup(title: "화면") {
            VStack(alignment: .leading, spacing: 12) {
                SettingsLabel(icon: "circle.lefthalf.filled", title: "테마", subtitle: nil)
                ChoiceChips(
                    options: Theme.allCases,
                    selected: theme.theme,
                    label: { ThemeStore.label($0) }
                ) { theme.theme = $0 }
                .padding(.leading, 36)
            }
            .padding(.horizontal, 20).padding(.vertical, 16)
        }
    }

    // MARK: - 소리와 진동

    private var soundSection: some View {
        SettingsGroup(title: "소리와 진동") {
            ToggleRow(icon: "speaker.wave.2.fill", title: "효과음", subtitle: nil,
                      isOn: Binding(get: { prefs.soundOn }, set: { prefs.soundOn = $0 }))
            SettingsDivider()
            ToggleRow(icon: "iphone.radiowaves.left.and.right", title: "진동", subtitle: "버튼을 누르거나 정답을 맞힐 때 톡 울려요.",
                      isOn: Binding(get: { prefs.hapticsOn }, set: { prefs.hapticsOn = $0 }))
        }
    }

    // MARK: - 알림

    private var reminderSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            SettingsGroup(title: "알림") {
                ToggleRow(icon: "bell.fill", title: "학습 알림",
                          subtitle: "하루 한 번, 오늘의 구구단을 알려 드려요. 밤에는 보내지 않아요.",
                          isOn: Binding(get: { reminder.settings.enabled }, set: setReminder))
                if reminder.settings.enabled {
                    SettingsDivider()
                    reminderHourRow
                }
            }
            // 앱에서는 켰지만 시스템 설정에서 꺼진 경우 — 앱이 대신 켤 수 없어 설정으로 안내한다
            if reminder.deniedBySystem {
                HStack(spacing: 8) {
                    Text("설정 앱에서 구구 어드벤처 알림이 꺼져 있어요.")
                        .font(.suite(.medium, 12)).foregroundStyle(Color.gg.warning)
                        .fixedSize(horizontal: false, vertical: true)
                    Spacer(minLength: 4)
                    Button("설정 열기") { open(UIApplication.openNotificationSettingsURLString) }
                        .font(.suite(.bold, 12)).foregroundStyle(Color.gg.accent)
                        .frame(minHeight: 44)
                }
            }
        }
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
            SettingsGroup(title: "이용권") {
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
                    LinkRow(icon: "sparkles", title: "평생 이용권 · 커피 한 잔 값", value: premium.price) {
                        showPaywall = true
                    }
                }
                SettingsDivider()
                LinkRow(icon: "arrow.clockwise", title: "구매 복원", value: restoring ? "확인 중…" : nil,
                        action: restorePurchase)
                SettingsDivider()
                LinkRow(icon: "doc.text", title: "이용약관", value: nil) { open(PremiumConfig.Legal.terms) }
                SettingsDivider()
                LinkRow(icon: "checkmark.shield", title: "개인정보처리방침", value: nil) { open(PremiumConfig.Legal.privacy) }
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
            restoreNotice = ok ? "구매를 확인했어요. 기록 보관을 켜는 중이에요." : "복원할 구매 내역이 없어요."
            if ok { await sync.enableGuardianSync(auth: auth) }
        }
    }

    // MARK: - 앱 정보

    private var aboutSection: some View {
        SettingsGroup(title: "앱 정보") {
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
