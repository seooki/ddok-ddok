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
    fun oldPostTimeIsRepostEvenWithoutHistory() {
        val old = now - Repost.STALE_AFTER_MS - 1
        assertTrue(Repost.detect(postTime = old, previousPostTime = null, nowEpochMs = now))
        assertTrue(Repost.detect(postTime = old, previousPostTime = Long.MIN_VALUE, nowEpochMs = now))
    }

    @Test
    fun clockMovedBackIsNotRepost() {
        assertFalse(Repost.detect(postTime = now + 60_000, previousPostTime = null, nowEpochMs = now))
    }
}
