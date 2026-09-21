package site.smap.gugudan.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 구구단 학습 앱 — 도메인 타입 (iOS Core/Models.swift 이식)
// 주의: 상태 갱신 시 컬렉션은 항상 새 인스턴스로 재할당한다 (copy() 공유 참조 부작용 방지)

@Serializable
enum class GameMode {
    @SerialName("practice") PRACTICE,
    @SerialName("timeAttack") TIME_ATTACK,
    @SerialName("challenge") CHALLENGE,
    @SerialName("survival") SURVIVAL,
    @SerialName("missing") MISSING,
    @SerialName("truefalse") TRUEFALSE,
    @SerialName("adventure") ADVENTURE,
}

@Serializable
enum class Theme {
    @SerialName("light") LIGHT,
    @SerialName("dark") DARK,
}

data class Problem(val a: Int, val b: Int)   // a=단(2~9), b=곱하는 수(1~9)

@Serializable
data class AnswerRecord(
    val a: Int,
    val b: Int,
    val correct: Boolean,
    val ms: Int,   // 응답 시간(ms)
)

data class SessionResult(
    val mode: GameMode,
    val table: Int?,          // 단일 단 집중이면 단 번호, 혼합이면 null
    val answers: List<AnswerRecord>,
    val maxCombo: Int,
    val durationMs: Int,
    val partial: Boolean = false,   // 중도 이탈 부분 커밋 — 한 판 완료 통계와 구분
    val xpScale: Double? = null,    // 재대결 등 XP 체감 (null/1 = 기본)
)

// 날짜별 집계 — 보호자 주간 리포트용 링버퍼 항목 (최근 56일)
@Serializable
data class DayLogEntry(
    val date: String,            // YYYY-MM-DD
    val correct: Int = 0,
    val wrong: Int = 0,
    val msSum: Int = 0,
    val misses: Map<String, Int> = emptyMap(),
)

@Serializable
data class TableMastery(
    val stars: Int = 0,          // 0~3
    val bestAccuracy: Double = 0.0,
    val bestAvgMs: Int = 0,      // 최고(최저) 평균 응답시간
    val plays: Int = 0,
)

@Serializable
data class GameState(
    val version: Int = 1,
    val totalXp: Int = 0,
    // 리텐션
    val streak: Int = 0,
    val lastPlayedDate: String = "",   // YYYY-MM-DD
    val dailyGoal: Int = 20,
    val dailyDate: String = "",
    val dailyCorrect: Int = 0,
    // 진행
    val tableMastery: Map<Int, TableMastery> = emptyMap(),
    val unlockedAchievements: List<String> = emptyList(),
    // 통계
    val totalCorrect: Int = 0,
    val totalWrong: Int = 0,
    val maxCombo: Int = 0,
    val recentAccuracy: List<Int> = emptyList(),   // 최근 세션 정확도 (cap 20)
    val recentAvgMs: List<Int> = emptyList(),
    // 출제 보조: 오답 가중 풀 (key="axb")
    val wrongPool: Map<String, Int> = emptyMap(),
    // 모드 기록
    val bestScores: Map<GameMode, Int> = emptyMap(),
    val modesPlayed: List<GameMode> = emptyList(),
    // 설정
    val onboarded: Boolean = false,
    // 주간 리포트용 날짜별 링버퍼. 구버전 저장본에는 키가 없어 기본값(빈 목록)으로 디코딩된다.
    val dayLog: List<DayLogEntry> = emptyList(),
)

data class CommitResult(
    val xpEarned: Int,
    val leveledUp: Boolean,
    val newLevel: Int,
    val unlocked: List<String>,
    val table: Int?,
    val newStars: Int,
    val improvedStars: Boolean,
    val goalReached: Boolean,
    val score: Int?,
    val isNewBest: Boolean,
    val partial: Boolean,       // 중도 이탈 부분 커밋
)
