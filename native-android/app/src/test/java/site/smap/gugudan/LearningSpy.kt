package site.smap.gugudan

import site.smap.gugudan.core.Commit
import site.smap.gugudan.core.CommitResult
import site.smap.gugudan.core.GameState
import site.smap.gugudan.core.SessionResult

/**
 * 게임 엔진 테스트용 학습 기록 — GameStore.commitSession 과 같은 계산(Commit.applySession)을 메모리 상태에 적용한다
 * (iOS GameLearningTests.swift 의 LearningSpy 대응). GameStore 는 Persistence(Context) 가 필요해 상태만 따로 둔다.
 * 엔진에는 `spy::commit` 으로 넘긴다.
 */
class LearningSpy {
    var state: GameState = Commit.defaultState
        private set
    val results = mutableListOf<SessionResult>()
    val commits = mutableListOf<CommitResult>()

    fun commit(result: SessionResult): CommitResult {
        results += result
        val (next, done) = Commit.applySession(state, result)
        state = next
        commits += done
        return done
    }
}
