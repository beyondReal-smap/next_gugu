package site.smap.gugudan.core.adventure

import kotlinx.serialization.Serializable

// 어드벤처 모드 — 도메인 타입 (AdventureModels.swift 이식, UI 무의존)

enum class NpcKind { NORMAL, BOSS }

// 배틀 방식 — HP: 턴제 체력전 / SPEED: 레이스 / COUNTER: 시간제한 반격전
enum class BattleStyle { HP, SPEED, COUNTER }

data class Vec2(val x: Double, val z: Double)          // [x, z] 평면 좌표
data class DecoItem(val x: Double, val z: Double, val scale: Double)

data class NpcDef(
    val id: String,          // 예: 'r2-n1', 'r2-boss'
    val name: String,
    val kind: NpcKind,
    val battle: BattleStyle,
    val table: Int,
    val pos: Vec2,
    val hp: Int,
    val attack: Int,
    val color: String,       // #hex
    val greeting: String,
)

data class RegionTheme(val ground: String, val accent: String, val sky: String)

enum class DecoKind { TREE, ROCK, CRYSTAL }

enum class LandmarkKind { FLOWERBED, OASIS, SEA, ANCIENT_TREE, SNOWMAN, POND, VOLCANO, PILLARS }

data class Landmark(val kind: LandmarkKind, val pos: Vec2)

data class RegionDef(
    val table: Int,
    val name: String,
    val deco: DecoKind,
    val decoItems: List<DecoItem>,
    val spawn: Vec2,
    val landmark: Landmark,
    val theme: RegionTheme,
    val npcs: List<NpcDef>,
    val starSpots: List<Vec2>,   // 별 조각 배치 (방문마다 리스폰)
)

// kotlinx.serialization은 누락 키를 기본값으로 채움 → 구버전 저장소 자동 호환
@Serializable
data class AdventureProgress(
    val version: Int = 1,
    val defeatedNpcs: List<String> = emptyList(),
    val battlesWon: Int = 0,
    val battlesLost: Int = 0,
    val achievements: List<String> = emptyList(),
    val starShards: Int = 0,                       // 별 조각 잔액
    // 꾸미기 상점
    val ownedColors: List<String> = emptyList(),
    val ownedHats: List<String> = emptyList(),
    val equippedColor: String = "indigo",
    val equippedHat: String? = null,
)
