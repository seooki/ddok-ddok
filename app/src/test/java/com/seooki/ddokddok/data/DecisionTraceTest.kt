package com.seooki.ddokddok.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DecisionTraceTest {

    @Test
    fun `가장 오래된 것부터 버리고 최근 것만 남긴다`() {
        val trace = DecisionTrace(capacity = 3)
        (1..5).forEach { trace.add("p$it", "D$it", timeMillis = it.toLong()) }
        assertEquals(listOf("p3", "p4", "p5"), trace.snapshot().map { it.packageName })
    }
}
