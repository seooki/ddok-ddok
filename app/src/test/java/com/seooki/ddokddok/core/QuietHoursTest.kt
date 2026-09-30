package com.seooki.ddokddok.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursTest {

    private fun at(hour: Int, minute: Int = 0) = hour * 60 + minute

    @Test
    fun `자정을 넘는 구간`() {
        val night = QuietHours(enabled = true, startMinute = at(23), endMinute = at(7))
        assertTrue(night.isActiveAt(at(23)))
        assertTrue(night.isActiveAt(at(0)))
        assertTrue(night.isActiveAt(at(6, 59)))
        assertFalse(night.isActiveAt(at(7)))
        assertFalse(night.isActiveAt(at(22, 59)))
    }

    @Test
    fun `하루 안의 구간`() {
        val meeting = QuietHours(enabled = true, startMinute = at(9), endMinute = at(18))
        assertTrue(meeting.isActiveAt(at(9)))
        assertTrue(meeting.isActiveAt(at(17, 59)))
        assertFalse(meeting.isActiveAt(at(18)))
        assertFalse(meeting.isActiveAt(at(8, 59)))
    }

    @Test
    fun `꺼져 있거나 길이가 0이면 항상 비활성`() {
        assertFalse(QuietHours(enabled = false, startMinute = at(0), endMinute = at(23, 59)).isActiveAt(at(12)))
        assertFalse(QuietHours(enabled = true, startMinute = at(8), endMinute = at(8)).isActiveAt(at(8)))
    }
}
