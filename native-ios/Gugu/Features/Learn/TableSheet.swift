import SwiftUI

// 구구단 표 보기 — 연습 전에 한 단 전체를 눈과 귀로 익힌다.
// "소리로 외우기"는 전통 구구단 읽기("칠 팔은 오십육")로 한 줄씩 읽고, 읽는 줄을 강조한다.
// 2.7.0 부터 단 시트(PracticeSheet)의 안쪽 단계 '표·외우기'로 쓴다 — 닫기 대신 뒤로 가기.

struct TableSheet: View {
    let table: Int
    var onPractice: () -> Void
    /// 단 시트(어떻게 연습할까요?)로 돌아가기
    var onBack: () -> Void

    @Environment(GameStore.self) private var game

    private var speech: Speech { Speech.shared }
    private var tip: CoachHint { Hints.tableTip(table) }
    private var stars: Int { game.state.tableMastery[table]?.stars ?? 0 }

    private func rowKey(_ b: Int) -> String { VoiceLines.chant(table, b).key }
    private var isChanting: Bool { speech.isSpeaking && speech.speakingKey?.hasPrefix("c-\(table)x") == true }

    var body: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                VStack(spacing: 16) {
                    tipCard
                    rows
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 16)
            }
            .scrollIndicators(.hidden)
            actions
        }
        .readableWidth(560)
        .background(Color.gg.bg.ignoresSafeArea())
        .onDisappear { speech.stop() }
        .safeAreaInset(edge: .bottom) {
            if let message = Speech.shared.errorMessage {
                Text(message).font(.footnote).foregroundStyle(Color.gg.danger).padding()
            }
        }
    }

    private var header: some View {
        HStack(alignment: .center, spacing: 8) {
            Button {
                Haptics.impact(.light)
                speech.stop()
                onBack()
            } label: {
                Image(systemName: "chevron.left").font(.system(size: 17, weight: .bold)).foregroundStyle(Color.gg.textMuted)
                    .frame(width: 36, height: 36)
                    .background(Color.gg.surface2, in: Circle())
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel("뒤로")
            VStack(alignment: .leading, spacing: 4) {
                if [.hi, .vi, .id].contains(AppLanguage.current) {
                    Text("\(table)단").font(.suite(.extrabold, 28)).foregroundStyle(Color.gg.text)
                } else {
                    HStack(alignment: .firstTextBaseline, spacing: 2) {
                        Text("\(table)").font(.suite(.extrabold, 34)).foregroundStyle(Color.gg.text).monospacedDigit()
                        Text("단").font(.suite(.bold, 20)).foregroundStyle(Color.gg.textMuted)
                    }
                }
                StarsView(count: stars, size: 16)
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(table)단, 별 \(stars)개")
            Spacer()
        }
        .padding(.horizontal, 12)
        .padding(.top, 24)
        .padding(.bottom, 12)
    }

    private var tipCard: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "lightbulb.fill").font(.system(size: 16, weight: .bold)).foregroundStyle(Color.gg.warning)
            VStack(alignment: .leading, spacing: 4) {
                Text("\(table)단 비법 · \(tip.title)").font(.suite(.extrabold, 14)).foregroundStyle(Color.gg.text)
                Text(tip.full).font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted).monospacedDigit()
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .background(Color.gg.warning.opacity(0.1), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(Color.gg.warning.opacity(0.25), lineWidth: 1))
        .accessibilityElement(children: .combine)
    }

    private var rows: some View {
        VStack(spacing: 6) {
            ForEach(Problems.minB...Problems.maxB, id: \.self) { b in
                let speaking = speech.speakingKey == rowKey(b)
                let row = HStack(alignment: .firstTextBaseline) {
                    Text("\(table) × \(b) = ")
                        .font(.suite(.bold, 22)).foregroundStyle(Color.gg.textMuted).monospacedDigit()
                    + Text("\(table * b)")
                        .font(.suite(.extrabold, 24)).foregroundStyle(speaking ? Color.gg.accent : Color.gg.text).monospacedDigit()
                    Spacer(minLength: 8)
                    // 읽기 열 — 영어는 식과 같은 내용이라 없다 (SpokenMath.chantText)
                    if let chant = SpokenMath.chantText(table, b) {
                        Text(chant)
                            .font(.suite(.bold, 13))
                            .foregroundStyle(speaking ? Color.gg.accent : Color.gg.textMuted.opacity(0.8))
                    }
                }
                .padding(.horizontal, 16).padding(.vertical, 10)
                .background(speaking ? Color.gg.accent.opacity(0.12) : Color.gg.surface,
                            in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .strokeBorder(speaking ? Color.gg.accent.opacity(0.5) : Color.gg.border, lineWidth: 1))
                .animation(.easeOut(duration: 0.2), value: speaking)
                if AppLanguage.current.supportsReadAloud {
                    Button {
                        Haptics.impact(.light)
                        speech.speak(VoiceLines.chant(table, b))
                    } label: {
                        row
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(SpokenMath.equationLabel(table, b))
                    .accessibilityHint("눌러서 소리로 듣기")
                } else {
                    row
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(SpokenMath.equationLabel(table, b))
                }
            }
        }
    }

    private var actions: some View {
        HStack(spacing: 10) {
            if AppLanguage.current.supportsReadAloud {
                GGButton(variant: .surface, size: .lg, action: toggleChant) {
                    Image(systemName: isChanting ? "stop.fill" : "speaker.wave.2.fill")
                    Text(isChanting ? "멈추기" : "소리로 외우기")
                }
            }
            GGButton(variant: .primary, size: .lg, action: {
                speech.stop()
                onPractice()
            }) {
                Image(systemName: "play.fill")
                Text("\(table)단 연습").lineLimit(2).multilineTextAlignment(.center).minimumScaleFactor(0.8)
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 12)
        .padding(.bottom, 12)
        .background(Color.gg.bg)
    }

    private func toggleChant() {
        if isChanting {
            speech.stop()
        } else {
            speech.speak((Problems.minB...Problems.maxB).map { VoiceLines.chant(table, $0) })
        }
    }
}
