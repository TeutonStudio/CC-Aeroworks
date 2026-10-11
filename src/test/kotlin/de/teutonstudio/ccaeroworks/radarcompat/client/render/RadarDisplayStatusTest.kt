package de.teutonstudio.ccaeroworks.radarcompat.client.render

import de.teutonstudio.ccaeroworks.radarcompat.display.RadarDisplaySnapshot
import de.teutonstudio.ccaeroworks.radarcompat.display.RadarLinkStatus
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import org.junit.jupiter.api.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RadarDisplayStatusTest {
    private fun active() = RadarDisplaySnapshot(
        true, true, BlockPos(17, 69, 19), 20.0, CompoundTag(), null,
        CompoundTag(), 0, 25_000L, RadarLinkStatus.ACTIVE, 100L
    )

    @Test
    fun validEmptyRadarDrawsNativePictureWithoutAnErrorLabel() {
        assertNull(radarDisplayMessageKey(active(), 120L, true))
    }

    @Test
    fun missingRadarAssignmentIsNotMisreportedAsAnAgeProblem() {
        val snapshot = RadarDisplaySnapshot.disconnected(1L, RadarLinkStatus.RADAR_NOT_ASSIGNED)
        assertEquals("radar_status.cc_aeroworks.radar_not_assigned", radarDisplayMessageKey(snapshot, 100L, false))
    }

    @Test
    fun expiredAndClientUnloadedRadarsHaveDifferentExplanations() {
        assertEquals("radar_status.cc_aeroworks.stale", radarDisplayMessageKey(active(), 121L, true))
        assertEquals("radar_status.cc_aeroworks.radar_not_loaded", radarDisplayMessageKey(active(), 110L, false))
        assertEquals("radar_status.cc_aeroworks.waiting", radarDisplayMessageKey(null, 0L, false))
    }

    @Test
    fun everyInactiveNetworkStatusIsPreserved() {
        RadarLinkStatus.entries.filter { it != RadarLinkStatus.ACTIVE }.forEach { status ->
            val snapshot = RadarDisplaySnapshot.disconnected(0L, status)
            assertEquals("radar_status.cc_aeroworks.${status.name.lowercase(Locale.ROOT)}", radarDisplayMessageKey(snapshot, 0L, false))
        }
    }
}
