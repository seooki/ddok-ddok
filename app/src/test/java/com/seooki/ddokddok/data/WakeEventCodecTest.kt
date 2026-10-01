package com.seooki.ddokddok.data

import com.seooki.ddokddok.core.DisplayKind
import com.seooki.ddokddok.core.SkipReason
import com.seooki.ddokddok.core.WakeMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakeEventCodecTest {

    @Test
    fun `상세 정보까지 그대로 저장하고 읽는다`() {
        val events = listOf(
            WakeEvent(1_000L, "a.b", EventOutcome.Woke(WakeMethod.MENU_KEY, usedFallback = false), WakeDetail(DisplayKind.OFF)),
            WakeEvent(
                2_000L,
                "a.b",
                EventOutcome.Woke(WakeMethod.WAKE_LOCK, usedFallback = true),
                WakeDetail(DisplayKind.DOZE, waitedMs = 2_400, rewoke = true),
            ),
            WakeEvent(3_000L, "c.d", EventOutcome.Skipped(SkipReason.WOKEN_BY_OTHER)),
        )
        events.forEach { assertEquals(it, WakeEventCodec.decode(WakeEventCodec.encode(it))) }
    }

    @Test
    fun `0_1_2에서 저장한 줄도 읽는다`() {
        assertEquals(
            WakeEvent(1_790_816_920_429L, "com.android.shell", EventOutcome.Woke(WakeMethod.MENU_KEY, usedFallback = false)),
            WakeEventCodec.decode("1790816920429\tcom.android.shell\tW\tmenu_key\t0"),
        )
    }

    @Test
    fun `알 수 없는 줄은 건너뛴다`() {
        assertNull(WakeEventCodec.decode(""))
        assertNull(WakeEventCodec.decode("x\ta\tW\tmenu_key\t0"))
        assertNull(WakeEventCodec.decode("1\ta\tS\tNO_SUCH_REASON\t0"))
    }
}
