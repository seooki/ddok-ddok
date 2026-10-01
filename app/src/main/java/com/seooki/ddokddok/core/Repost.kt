package com.seooki.ddokddok.core

/**
 * 시스템이 이미 있던 알림을 다시 보낸 것인지 가린다.
 *
 * 앱이 알림을 올리거나 고치면 시스템은 그 순간을 게시 시각으로 새로 찍는다. 포그라운드 서비스 표시를 떼거나
 * 알림을 자동으로 묶을 때는 게시 시각을 그대로 둔 채 다시 보내고, 소리도 내지 않는다.
 * 리스너가 연결되기 전에 올라와 이전 게시 시각을 모르는 알림은 게시 시각이 오래됐는지로 가린다.
 */
object Repost {
    /** 새 알림은 1초 안쪽으로 도착한다. 시스템이 바쁠 때를 생각해 넉넉히 잡는다. */
    const val STALE_AFTER_MS = 10_000L

    fun detect(postTime: Long, previousPostTime: Long?, nowEpochMs: Long): Boolean =
        postTime == previousPostTime || nowEpochMs - postTime > STALE_AFTER_MS
}
