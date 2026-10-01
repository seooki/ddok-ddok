package com.seooki.ddokddok.core

/**
 * 시스템이 이미 있던 알림을 다시 보낸 것인지 가린다.
 *
 * 앱이 알림을 올리거나 고치면 시스템은 그 순간을 게시 시각으로 새로 찍는다. 포그라운드 서비스 표시를 떼거나
 * 알림을 자동으로 묶을 때는 게시 시각을 그대로 둔 채 다시 보내고, 소리도 내지 않는다.
 *
 * 리스너가 연결될 때 떠 있던 알림은 키를 미리 받아 두므로, 이전 게시 시각을 모르더라도 '아는 알림'이다.
 * 아는 알림이 오래된 게시 시각으로 다시 오면 재전송이다. 처음 보는 알림은 똑똑이 잠시 멈춰 있다가 늦게 받은
 * 새 알림일 수 있어서 재전송으로 버리지 않는다(얼마나 늦었는지는 [WakePolicy]가 따로 본다).
 */
object Repost {
    /** 새 알림은 1초 안쪽으로 도착한다. 시스템이 바쁠 때를 생각해 넉넉히 잡는다. */
    const val STALE_AFTER_MS = 10_000L

    /** 처음 보는 알림이 이보다 오래됐으면 시스템이 옛 알림을 다시 보여 주는 것이다(예: 업무 프로필을 켤 때). */
    const val ANCIENT_AFTER_MS = 10 * 60_000L

    /** [previousPostTime]은 같은 키로 전에 받은 게시 시각이다. 처음 보는 알림이면 null이다. */
    fun detect(postTime: Long, previousPostTime: Long?, nowEpochMs: Long): Boolean {
        if (postTime == previousPostTime) return true
        val age = nowEpochMs - postTime
        return if (previousPostTime != null) age > STALE_AFTER_MS else age > ANCIENT_AFTER_MS
    }
}
