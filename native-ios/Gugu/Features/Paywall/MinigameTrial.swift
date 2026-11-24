import SwiftUI

// 게임(구구 계단·구구 몬스터) 무료 체험 — 시작 전 안내, 진행 표시, 체험 종료 패널.
// 체험 규칙(몇 문제, 언제 끝나는지)은 게임 엔진의 trialLeft 가 맡고, 여기서는 보여 주기만 한다.

enum MinigameTrial {
    static var questions: Int { PremiumConfig.minigameTrialQuestions }

    /// 새 판의 체험 문제 수 — 이용권이 있으면 제한 없음(nil)
    static func limit(isPremium: Bool) -> Int? { isPremium ? nil : questions }

    /// 홈·학습 탭 카드 배지 — 무료 사용자에게만
    static func badge(isPremium: Bool) -> String? { isPremium ? nil : String(localized: "\(questions)문제 체험") }
}

/// 시작 전 안내 — 체험이 몇 문제인지 먼저 알려 끝이 갑작스럽지 않게 한다
struct TrialNotice: View {
    /// 체험 범위 — 게임 둘 다 문제 수 단위
    private var title: String { String(localized: "무료 체험 · \(MinigameTrial.questions)문제") }

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "ticket.fill")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Color.gg.accent)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.suite(.extrabold, 13)).foregroundStyle(Color.gg.text)
                Text("평생 이용권이 있으면 제한 없이 놀 수 있어요.")
                    .font(.suite(.medium, 12)).foregroundStyle(Color.gg.textMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(12)
        .background(Color.gg.accent.opacity(0.08), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

/// 진행 중 "체험 2/3" — 지금 문제가 체험의 몇 번째인지
struct TrialProgressPill: View {
    /// 남은 체험 문제 수(지금 문제 포함)
    let left: Int

    var body: some View {
        let total = MinigameTrial.questions
        let current = min(total, max(1, total - left + 1))
        HStack(spacing: 4) {
            Image(systemName: "ticket.fill").font(.system(size: 9, weight: .bold))
            Text("체험 \(current)/\(total)").font(.suite(.extrabold, 11))
        }
        .foregroundStyle(Color.gg.accent)
        .padding(.horizontal, 8)
        .padding(.vertical, 3)
        .background(Color.gg.accent.opacity(0.12), in: Capsule())
        .fixedSize()   // 좁은 줄에서도 줄거나 접히지 않는다
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("무료 체험 \(total)문제 중 \(current)번째")
    }
}

/// 체험 문제 수를 다 채워 판이 끝났을 때의 패널 — 결제 화면으로 안내한다.
/// "다시 하기"는 두지 않는다: 체험을 이어 붙여 계속 노는 흐름을 막기 위해서다(대표님 결정).
/// 체험 수 전에 잡히거나 시간이 다 된 판은 이 패널이 아니라 일반 결과 창([다시 하기])을 쓴다(2026-11-24 대표님 결정).
struct TrialEndPanel: View {
    /// 이번 판에서 맞힌 문제 수
    let correct: Int
    /// 맞힌 수 앞 문장 — 구구 몬스터는 문제가 아니라 물리친 몬스터 수로 말한다. nil 이면 "N문제를 맞혔어요!"
    var lead: String? = nil
    /// 학습 기록으로 받은 XP(구구 계단·구구 몬스터) — nil 이면 줄을 그리지 않음
    var xpEarned: Int? = nil
    /// 오늘의 미션 줄(GameResultContent.missionNote) — nil 이면 그리지 않음
    var missionNote: String? = nil
    let onUnlock: () -> Void
    let onExit: () -> Void

    var body: some View {
        VStack(spacing: 14) {
            ZStack {
                Circle().fill(Color.gg.accent.opacity(0.12)).frame(width: 56, height: 56)
                Image(systemName: "sparkles")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundStyle(Color.gg.accent)
            }
            .accessibilityHidden(true)

            VStack(spacing: 6) {
                Text("무료 체험은 여기까지!")
                    .font(.suite(.extrabold, 18)).foregroundStyle(Color.gg.text)
                Text(message)
                    .font(.suite(.medium, 13)).foregroundStyle(Color.gg.textMuted)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .accessibilityElement(children: .combine)

            if let missionNote {
                Label(missionNote, systemImage: "checkmark.seal.fill")
                    .font(.suite(.extrabold, 13)).foregroundStyle(Color.gg.success)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }

            if let xpEarned {
                Text(verbatim: "+\(xpEarned) XP")
                    .font(.suite(.extrabold, 12)).foregroundStyle(Color.gg.accent).monospacedDigit()
                    .padding(.horizontal, 10).padding(.vertical, 4)
                    .background(Color.gg.accent.opacity(0.12), in: Capsule())
            }

            GGButton(variant: .primary, size: .lg, action: onUnlock) {
                Image(systemName: "sparkles")
                Text("평생 이용권 보기")
            }
            Button(action: { Haptics.impact(.light); onExit() }) {
                Text("나가기")
                    .font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted)
                    .frame(maxWidth: .infinity, minHeight: 44)
            }
        }
        .frame(maxWidth: .infinity)
    }

    private var message: String {
        let lead = correct > 0 ? (self.lead ?? String(localized: "\(correct)문제를 맞혔어요! ")) : ""
        return lead + String(localized: "평생 이용권이 있으면 제한 없이 계속 놀면서 연습할 수 있어요.")
    }
}
