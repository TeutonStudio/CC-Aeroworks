package de.teutonstudio.ccaeroworks.radarcompat.display

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RadarDisplaySnapshotTest {
    @Test
    fun roundTripUsesClientReceiptTimeInsteadOfComparingServerAndClientClocks() {
        val original = RadarDisplaySnapshot(
            true, true, BlockPos(17, 69, 19), 20.0, CompoundTag(), null,
            CompoundTag(), 0, 25_000L, RadarLinkStatus.ACTIVE
        )
        val received = RadarDisplaySnapshot.fromTag(original.toTag(), 100L)
        assertEquals(original.radarPos, received.radarPos)
        assertTrue(received.sameContentAs(original))
        assertTrue(RadarDisplaySnapshot.isFresh(received, 120L))
        assertFalse(RadarDisplaySnapshot.isFresh(received, 121L))
        assertFalse(RadarDisplaySnapshot.isFresh(received, 99L))
    }

    @Test
    fun inactiveSnapshotSurvivesSynchronizationWithoutBecomingActive() {
        val original = RadarDisplaySnapshot.disconnected(25_000L, RadarLinkStatus.RADAR_NOT_ASSIGNED)
        val received = RadarDisplaySnapshot.fromTag(original.toTag(), 100L)
        assertEquals(RadarLinkStatus.RADAR_NOT_ASSIGNED, received.status)
        assertFalse(RadarDisplaySnapshot.isFresh(received, 100L))
    }
}
