package site.smap.gugudan.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

// 구구단 학습 앱 — 도메인 타입 (iOS Core/Models.swift 이식)
// 주의: 상태 갱신 시 컬렉션은 항상 새 인스턴스로 재할당한다 (copy() 공유 참조 부작용 방지)

/**
 * 저장값(SerialName)은 기존 사용자 저장본 modesPlayed·bestScores 에 들어 있다 — 값을 지우거나 이름을 바꾸면
 * GameState 디코딩이 실패해 진행도가 통째로 격리·초기화된다. ADVENTURE 는 3D 어드벤처를 없앤 뒤에도 남겨 둔다.
 * STAIRS·MONSTER 는 구구 계단·구구 몬스터 판의 학습 기록(2.9.0) — 학습 탭 모드 목록(Modes.list)에는 넣지 않는다.
 */
@Serializable
enum class GameMode {
    @SerialName("practice") PRACTICE,
    @SerialName("timeAttack") TIME_ATTACK,
    @SerialName("challenge") CHALLENGE,
    @SerialName("survival") SURVIVAL,
    @SerialName("missing") MISSING,
    @SerialName("truefalse") TRUEFALSE,
    @SerialName("adventure") ADVENTURE,
    @SerialName("stairs") STAIRS,
    @SerialName("monster") MONSTER;

    /**
     * 게임(구구 계단·구구 몬스터) 판 — 학습 기록(XP·오늘 목표·틀린 문제)에는 들어가지만 단 마스터리 별은 주지 않는다.
     * 마스터리 별은 문제 수 하한 없이 정확도·속도로 매겨져서, 몇 마리짜리 체험 판만으로도 별 3개가 나오기 때문 (iOS GameMode.isGame)
     */
    val isGame: Boolean get() = this == STAIRS || this == MONSTER
}

/** 화면 테마 — SYSTEM 은 기기 설정(낮/밤)을 따른다. 고른 적 없으면 낮(LIGHT) */
@Serializable
enum class Theme {
    @SerialName("system") SYSTEM,
    @SerialName("light") LIGHT,
    @SerialName("dark") DARK;

    companion object {
        /**
         * 저장된 테마 문자열 → 초기 테마 (계획서 2-10).
         * 없음(null) → LIGHT, "dark" → DARK, "light" → LIGHT, "system" → SYSTEM,
         * 그 밖의 손상 문자열 → LIGHT (호출부가 onUnknown 으로 로그 한 줄 남김).
         */
        fun initial(saved: String?, onUnknown: ((String) -> Unit)? = null): Theme = when (saved) {
            null, "light" -> LIGHT
            "dark" -> DARK
            "system" -> SYSTEM
            else -> {
                onUnknown?.invoke(saved)
                LIGHT
            }
        }
    }
}

data class Problem(val a: Int, val b: Int)   // a=단(2~9), b=곱하는 수(1~9)

/** 제출한 답 — OX 퀴즈는 참/거짓, 나머지는 수 (웹 `given?: number | boolean` 대응) */
@Serializable(with = GivenAnswerSerializer::class)
sealed interface GivenAnswer {
    @JvmInline value class Number(val value: Int) : GivenAnswer
    @JvmInline value class Boolean(val value: kotlin.Boolean) : GivenAnswer

    /** 수/참거짓으로 표현할 수 없는 경우 — 시간 초과로 아무 답도 내지 않은 문항 등.
     *  서버 계약이 str 을 받으므로 "답이 없었다"를 0 으로 꾸미지 않고 그대로 남긴다. */
    @JvmInline value class Text(val value: String) : GivenAnswer

    companion object {
        /** 시간 초과 등으로 제출 자체가 없었음 */
        val NoAnswer = Text("no_answer")
    }
}

/** 수/참거짓을 감싸지 않은 단일 JSON 값으로 넣고 뺀다 (iOS GivenAnswer 와 동일한 형태) */
object GivenAnswerSerializer : KSerializer<GivenAnswer> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("GivenAnswer", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: GivenAnswer) {
        val out = encoder as? JsonEncoder ?: error("GivenAnswer 는 JSON 에서만 쓴다")
        out.encodeJsonElement(
            when (value) {
                is GivenAnswer.Number -> JsonPrimitive(value.value)
                is GivenAnswer.Boolean -> JsonPrimitive(value.value)
                is GivenAnswer.Text -> JsonPrimitive(value.value)
            }
        )
    }

    override fun deserialize(decoder: Decoder): GivenAnswer {
        val input = decoder as? JsonDecoder ?: error("GivenAnswer 는 JSON 에서만 쓴다")
        val primitive = input.decodeJsonElement().jsonPrimitive
        primitive.intOrNull?.let { return GivenAnswer.Number(it) }
        primitive.booleanOrNull?.let { return GivenAnswer.Boolean(it) }
        if (primitive.isString) return GivenAnswer.Text(primitive.content)
        error("GivenAnswer 를 해석하지 못했습니다: $primitive")
    }
}

@Serializable
data class AnswerRecord(
    val a: Int,
    val b: Int,
    val correct: Boolean,
    val ms: Int,   // 응답 시간(ms)
    /** 제출한 답 (서버 동기화용, 구기록은 없음) */
    val given: GivenAnswer? = null,
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

/** 하루 안 한 단의 누적 집계 — 보호자 단별 실력 리포트용 (계획서 2-4) */
@Serializable
data class TableTally(
    val correct: Int = 0,
    val wrong: Int = 0,
    val msSum: Int = 0,
)

// 날짜별 집계 — 보호자 주간 리포트용 링버퍼 항목 (최근 56일)
@Serializable
data class DayLogEntry(
    val date: String,            // YYYY-MM-DD
    val correct: Int = 0,
    val wrong: Int = 0,
    val msSum: Int = 0,
    val misses: Map<String, Int> = emptyMap(),
    /** 단별 합계 (key = 단 번호 2~19). null = 이 필드 도입 전 구버전 저장본의 날 */
    val tables: Map<Int, TableTally>? = null,
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
