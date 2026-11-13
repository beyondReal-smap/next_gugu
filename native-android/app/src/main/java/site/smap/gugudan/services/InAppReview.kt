package site.smap.gugudan.services

import android.app.Activity
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory

// Play In-App Review — 앱 안에서 평가 카드를 띄운다 (iOS SwiftUI requestReview 대응).
// 실제로 카드가 보일지는 Play 할당량이 정하고, 결과(보였는지·별점)는 앱에 알려 주지 않는다.
// 실패는 사용자에게 보일 것이 없어 로그만 남긴다 — 평가 요청은 부가 기능이라 다른 흐름을 막지 않는다.

object InAppReview {
    private const val TAG = "InAppReview"

    fun request(activity: Activity) {
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (!request.isSuccessful) {
                Log.w(TAG, "평가 요청 준비 실패", request.exception)
                return@addOnCompleteListener
            }
            if (activity.isFinishing || activity.isDestroyed) return@addOnCompleteListener
            manager.launchReviewFlow(activity, request.result).addOnCompleteListener { flow ->
                if (!flow.isSuccessful) Log.w(TAG, "평가 카드 실행 실패", flow.exception)
            }
        }
    }
}
