package com.seooki.ddokddok.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AodWaitLearnerTest {

    @Test
    fun `처음에는 기다린다`() {
        assertTrue(AodWaitLearner().shouldWait())
    }

    @Test
    fun `두 번 연속 꺼지지 않으면 기다리지 않는다`() {
        val learner = AodWaitLearner()
        learner.record(wentOff = false)
        assertTrue(learner.shouldWait())
        learner.record(wentOff = false)
        assertFalse(learner.shouldWait())
    }

    @Test
    fun `한 번이라도 꺼지면 다시 기다린다`() {
        val learner = AodWaitLearner()
        learner.record(wentOff = false)
        learner.record(wentOff = true)
        learner.record(wentOff = false)
        assertTrue(learner.shouldWait())
    }

    @Test
    fun `기다리지 않기로 한 뒤에도 가끔 다시 기다려 본다`() {
        val learner = AodWaitLearner()
        repeat(AodWaitLearner.LEARN_AFTER) { learner.record(wentOff = false) }
        val decisions = List(AodWaitLearner.PROBE_EVERY + 1) { learner.shouldWait() }
        assertEquals(AodWaitLearner.PROBE_EVERY, decisions.count { !it })
        assertTrue(decisions.last())
        // 다시 기다려 보니 꺼졌다면 처음처럼 기다린다.
        learner.record(wentOff = true)
        assertTrue(learner.shouldWait())
    }
}
