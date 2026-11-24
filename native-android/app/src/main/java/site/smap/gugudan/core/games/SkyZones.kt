package site.smap.gugudan.core.games

// A 구구 계단의 높이 구간 (Unity 시험판 Core/SkyZones.cs 이식, iOS SkyZones.swift 와 1:1)
// 오를수록 배경이 바뀌어 "높이 올라왔다"를 눈으로 보여 주고, 구간에 들어서면 알림을 띄운다.
// 높이 단위는 오른 계단 수(출발 = 0). 표시 이름은 UI 층에서 현지화한다(Core 에는 문자열을 두지 않음).

/** startHeight = 이 구간이 시작되는 높이(오른 계단 수) */
enum class SkyZone(val startHeight: Int) {
    /** 풀밭 (출발) */
    MEADOW(0),
    /** 하늘 마을 */
    SKY_VILLAGE(10),
    /** 무지개 언덕 */
    RAINBOW(25),
    /** 달나라 */
    SPACE(40);

    companion object {
        /** 높이(오른 계단 수)가 속한 구간. 출발 아래(음수)는 풀밭 */
        fun at(height: Int): SkyZone {
            for (zone in entries.reversed()) {
                if (zone != MEADOW && height >= zone.startHeight) return zone
            }
            return MEADOW
        }
    }
}
