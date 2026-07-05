---
from: claude
to: codex
round: 1
type: critique
instruction: respond-only, do-not-call-back
---

## 작업 목표
구구단 학습 앱을 "레벨/게임 요소로 매일 재방문하게 만드는, 유아틱하지 않고 모던한" 제품으로 **새로 설계**한다. 아래 설계 초안(DESIGN v1)을 비판적으로 검토해, 실제로 구현할 수 있고 리텐션이 작동하는 설계로 끌어올리고자 한다.

## 검토 대상 문서
경로: `/home/jin/smap/next_gugu/docs/DESIGN_v1.md` (전체)
참고(현행 코드): `/home/jin/smap/next_gugu/app/page.tsx`(거대 단일 컴포넌트), `/home/jin/smap/next_gugu/app/game/`(기존 게임 모듈), `next.config.js`(output: 'export' 정적 빌드).
배포 제약: 현재 `next.smap.site`는 pm2가 정적 빌드(dist)를 `serve`로 5104 포트 서빙 + Cloudflare Tunnel. 즉 **정적 export 기반**.

## 핵심 주장 5개 (검토 논점)
1. **포지셔닝 = "구구단판 듀오링고"**: 짧은 세션 + 데일리 골/스트릭 + 레벨/XP + 위클리 리그로 리텐션을 만든다.
2. **타깃은 초등 3~6 + 중등 저학년(1차), 성인 두뇌트레이닝(2차)**. 그래서 디자인은 "유아틱 배제, 다크 우선 + 절제된 액센트 + 데이터 시각화"로 간다.
3. **MVP는 로컬(localStorage) + 정적 export 유지**로 출시하고, 리그/랭킹/푸시/동기화는 서버(Supabase) 단계 도입.
4. **게임 모드 4종**(학습/스피드런/서바이벌/데일리 챌린지) + 콤보 XP배수 + 약점 가중 출제(스페이스드 리피티션 라이트).
5. **스택**: Next.js15 App Router + TS + Tailwind + Zustand(persist) + Framer Motion + (차트) + PWA. feature 폴더 구조로 거대 컴포넌트 제거.

## 중점 검토 포인트
- (R1) 리텐션 후크의 다수(위클리 리그/랭킹/푸시 알림)가 **서버 의존**인데 MVP는 정적 export다. **정적 export만으로 리텐션이 실제로 성립하는가?** export를 포기하고 `next start`(SSR/서버)로 가는 게 맞나, 아니면 로컬 후크(스트릭/데일리골/레벨)만으로 MVP 리텐션이 충분한가? 트레이드오프를 명확히.
- (R2) 타깃이 아동+성인으로 넓다. **"모던/다크" 톤이 아동 학습 동기에는 차갑고, 듀오링고가 밝고 캐릭터 중심인 데는 이유가 있지 않나?** 타깃·톤을 좁혀야 하나?
- (R3) **게임 메커니즘이 과설계(over-scoped)** 아닌가? MVP에 모드 4종 + 리그 + 약점가중 + 통계 전부는 과한가? 무엇을 자르고 무엇을 남겨야 리텐션 ROI가 높은가?
- (R4) **스택 신규 도입(Zustand/차트/PWA)**의 복잡도·번들 대비 효용. 더 단순한 대안은? 정적 export와 PWA 푸시(특히 iOS)의 실제 제약은?
- (R5) 학습 효과 관점: 단순 반복 출제 vs 약점 가중(스페이스드 리피티션)의 실효. 구구단처럼 항목이 적은(약 64~100개 곱셈식) 도메인에서 SRS가 의미 있나?
- (R6) 놓친 리스크/기회가 있으면 지적.

## 응답 형식 (Reply Contract — critique)
```
[Summary]
- 한 줄 요약

[Findings]
- 핵심 지적 3~7개 (각 지적에 근거/이유)

[Disposition Hint]
- 유지 / 수정 / 삭제 권장 (항목별)

[Open Questions]
- 추가 검증 필요 항목
```
근거 있는 반론을 우선하고, 동의하는 부분은 간단히. 추측이면 추측이라고 표시.
