package tv.own.owntv.features.epg

import org.junit.Assert.assertEquals
import org.junit.Test

class GuideLookTest {

    // --- The Full guide starts its timeline at the half hour the time falls in ---

    private val halfHour = 30 * 60_000L

    @Test
    fun midSlotGoesBackToTheHalfHour() = assertEquals(10 * halfHour, fullGuideViewStart(10 * halfHour + 17 * 60_000L))

    @Test
    fun onTheHalfHourStaysPut() = assertEquals(10 * halfHour, fullGuideViewStart(10 * halfHour))

    @Test
    fun lastMillisecondOfASlot() = assertEquals(10 * halfHour, fullGuideViewStart(11 * halfHour - 1))

    @Test
    fun beforeTheEpochStillFloors() = assertEquals(-halfHour, fullGuideViewStart(-1))
}
