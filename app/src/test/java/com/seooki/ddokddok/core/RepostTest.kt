package com.seooki.ddokddok.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepostTest {
    private val now = 1_000_000_000L

    @Test
    fun samePostTimeIsRepost() {
        assertTrue(Repost.detect(postTime = now - 6_000, previousPostTime = now - 6_000, nowEpochMs = now))
    }

    @Test
    fun newPostTimeIsNotRepost() {
        assertFalse(Repost.detect(postTime = now - 200, previousPostTime = now - 6_000, nowEpochMs = now))
        assertFalse(Repost.detect(postTime = now - 200, previousPostTime = null, nowEpochMs = now))
        assertFalse(Repost.detect(postTime = now - 200, previousPostTime = Long.MIN_VALUE, nowEpochMs = now))
    }

    @Test
    fun knownNotificationWithOldPostTimeIsRepost() {
        val old = now - Repost.STALE_AFTER_MS - 1
        // 연결할 때 떠 있던 알림(게시 시각은 모름)이 오래된 시각으로 다시 온 경우
        assertTrue(Repost.detect(postTime = old, previousPostTime = Long.MIN_VALUE, nowEpochMs = now))
        assertTrue(Repost.detect(postTime = old, previousPostTime = old - 5_000, nowEpochMs = now))
    }

    @Test
    fun unseenNotificationDeliveredLateIsNotRepost() {
        // 똑똑이 잠시 멈춰 있다가 늦게 받은 새 알림
        assertFalse(Repost.detect(postTime = now - 15_000, previousPostTime = null, nowEpochMs = now))
        // 처음 보는데 아주 오래된 알림은 시스템이 옛 알림을 다시 보여 주는 것이다.
        assertTrue(Repost.detect(postTime = now - Repost.ANCIENT_AFTER_MS - 1, previousPostTime = null, nowEpochMs = now))
    }

    @Test
    fun clockMovedBackIsNotRepost() {
        assertFalse(Repost.detect(postTime = now + 60_000, previousPostTime = null, nowEpochMs = now))
    }
}
