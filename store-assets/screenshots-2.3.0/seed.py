"""스토어 스크린샷용 데모 진행 상태 — 2주쯤 꾸준히 쓴 아이. 모든 수치가 서로 모순되지 않게 만든다.

사용: python3 seed.py ios|android  → 표준출력으로 {key: json문자열} 출력
"""
import datetime
import json
import sys

today = datetime.date(2026, 9, 30)
iso = lambda d: d.isoformat()

mastery = {  # 단: (별, 최고 정확도, 최고 평균ms, 플레이 수)
    2: (3, 1.0, 2100, 6), 3: (3, 0.95, 2600, 5), 4: (2, 0.9, 3100, 4),
    5: (3, 1.0, 2400, 4), 6: (1, 0.8, 3900, 3), 7: (1, 0.7, 4300, 2),
}
day_log = []
for i, (c, w, ms) in enumerate([(22, 3, 3600), (25, 2, 3400), (18, 4, 3500), (30, 2, 3100),
                                (24, 1, 3000), (27, 3, 2900), (14, 1, 2800)]):
    d = today - datetime.timedelta(days=6 - i)
    misses = {"7x8": 1, "6x7": 1} if w >= 2 else {"8x7": 1}
    day_log.append({"date": iso(d), "correct": c, "wrong": w, "msSum": ms * (c + w), "misses": misses})

progress = {
    "version": 1, "totalXp": 780, "streak": 7, "lastPlayedDate": iso(today),
    "dailyGoal": 20, "dailyDate": iso(today), "dailyCorrect": 14,
    "tableMastery": {str(t): {"stars": s, "bestAccuracy": a, "bestAvgMs": ms, "plays": p}
                     for t, (s, a, ms, p) in mastery.items()},
    "unlockedAchievements": ["first_correct", "correct_100", "combo_10", "streak_3", "streak_7",
                             "first_master", "stars_12", "level_5", "challenge_15"],
    "totalCorrect": 412, "totalWrong": 38, "maxCombo": 18,
    "recentAccuracy": [80, 85, 90, 88, 92, 95, 90, 93],
    "recentAvgMs": [3900, 3600, 3400, 3300, 3100, 2900, 3000, 2800],
    "wrongPool": {"7x8": 2, "6x7": 1, "8x7": 1},
    "modesPlayed": ["practice", "timeAttack", "challenge", "survival", "missing", "truefalse"],
    "onboarded": True,
    "dayLog": day_log,
}
adventure = {
    "version": 1, "defeatedNpcs": ["r2-n2", "r2-n3"],
    "battlesWon": 3, "battlesLost": 1,
    "achievements": ["adv_first_win", "adv_speed_win", "adv_counter_win"],
    "starShards": 12, "ownedColors": [], "ownedHats": ["sprout"],
    "equippedColor": "indigo", "equippedHat": "sprout",
}

platform = sys.argv[1]
if platform == "ios":
    # Swift JSONEncoder: String 원시값 enum 키 딕셔너리는 [키, 값, ...] 배열로 인코딩된다
    progress["bestScores"] = ["challenge", 17, "survival", 14]
else:
    progress["bestScores"] = {"challenge": 17, "survival": 14}
print(json.dumps({"gugu.progress.v1": json.dumps(progress, ensure_ascii=False),
                  "gugu.adventure.v1": json.dumps(adventure, ensure_ascii=False)}, ensure_ascii=False))
