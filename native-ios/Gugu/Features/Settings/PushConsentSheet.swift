import SwiftUI

// 이벤트·혜택 알림(광고) 켜기 — ① 보호자 확인(출생연도 4자리, 저장 안 함) → ② 수신 동의 → 서버 기록.
// 처리 결과(수신 동의 완료 알림창·실패 문구)는 시트가 닫힌 뒤 설정 화면이 보여 준다.
// 문구는 명세 고정(Android 와 글자 단위로 같다) — PushCopy.

struct PushConsentSheet: View {
    /// 서버에 동의를 기록한다 (PushStore.grantMarketing)
    var grant: () async -> PushStore.ConsentOutcome
    /// 끝 — nil 이면 사용자가 취소했다
    var onFinish: (PushStore.ConsentOutcome?) -> Void

    private enum Step { case gate, consent }

    @State private var step: Step = .gate
    @State private var birthYear = ""
    @State private var gateError: String?
    @State private var working = false
    @FocusState private var yearFocused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            switch step {
            case .gate: gate
            case .consent: consent
            }
            Spacer(minLength: 0)
        }
        .padding(24)
        .frame(maxWidth: 520, alignment: .leading)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Color.gg.bg.ignoresSafeArea())
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        // 서버 기록 중에 끌어내려 닫으면 결과를 보여 줄 곳이 없다
        .interactiveDismissDisabled(working)
    }

    // MARK: - ① 보호자 확인

    private var gate: some View {
        VStack(alignment: .leading, spacing: 14) {
            Image(systemName: "person.badge.shield.checkmark.fill")
                .font(.system(size: 24, weight: .bold)).foregroundStyle(Color.gg.accent)
                .frame(width: 52, height: 52)
                .background(Color.gg.accent.opacity(0.15), in: Circle())
                .accessibilityHidden(true)
            Text(PushCopy.gateTitle)
                .font(.suite(.extrabold, 22)).foregroundStyle(Color.gg.text)
                .accessibilityAddTraits(.isHeader)
            Text(PushCopy.gateBody)
                .font(.suite(.medium, 14)).foregroundStyle(Color.gg.textMuted)
                .fixedSize(horizontal: false, vertical: true)
            TextField("", text: $birthYear, prompt: Text("예: 1985").foregroundStyle(Color.gg.textMuted))
                .textFieldStyle(.plain)
                .keyboardType(.numberPad)
                .focused($yearFocused)
                .font(.suiteNum(.extrabold, 22))
                .foregroundStyle(Color.gg.text)
                .tint(Color.gg.accent)
                .padding(.horizontal, 14).frame(height: 52)
                .background(Color.gg.surface2, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                .onChange(of: birthYear) { _, value in
                    // 숫자 4자리까지만
                    let digits = String(value.filter { $0.isASCII && $0.isNumber }.prefix(4))
                    if digits != value { birthYear = digits }
                    gateError = nil
                }
                .accessibilityLabel("보호자 출생연도 4자리")
            if let gateError {
                Text(gateError)
                    .font(.suite(.bold, 12)).foregroundStyle(Color.gg.warning)
            }
            HStack(spacing: 8) {
                GGButton(variant: .surface, size: .md, action: { onFinish(nil) }) { Text("취소") }
                GGButton(variant: .primary, size: .md, action: confirmGate) { Text("확인") }
            }
        }
        .onAppear { yearFocused = true }
    }

    private func confirmGate() {
        guard GuardianGate.isValidBirthYear(birthYear) else {
            gateError = PushCopy.gateInvalid
            Haptics.warning()
            return
        }
        // 확인에만 쓰고 남기지 않는다
        birthYear = ""
        yearFocused = false
        step = .consent
    }

    // MARK: - ② 수신 동의

    private var consent: some View {
        VStack(alignment: .leading, spacing: 14) {
            Image(systemName: "gift.fill")
                .font(.system(size: 24, weight: .bold)).foregroundStyle(Color.gg.accent)
                .frame(width: 52, height: 52)
                .background(Color.gg.accent.opacity(0.15), in: Circle())
                .accessibilityHidden(true)
            Text(PushCopy.consentTitle)
                .font(.suite(.extrabold, 20)).foregroundStyle(Color.gg.text)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            Text(PushCopy.consentBody)
                .font(.suite(.medium, 14)).foregroundStyle(Color.gg.text)
                .lineSpacing(4)
                .fixedSize(horizontal: false, vertical: true)
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.gg.surface2, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            HStack(spacing: 8) {
                GGButton(variant: .surface, size: .md, action: { onFinish(nil) }) { Text("취소") }
                    .disabled(working)
                GGButton(variant: .primary, size: .md, action: agree) {
                    Text(working ? "처리 중…" : PushCopy.consentAgree)
                }
                .disabled(working)
            }
        }
    }

    private func agree() {
        guard !working else { return }
        working = true
        Task {
            let outcome = await grant()
            working = false
            onFinish(outcome)
        }
    }
}
