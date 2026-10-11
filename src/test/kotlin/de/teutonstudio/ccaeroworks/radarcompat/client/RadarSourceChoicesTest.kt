package de.teutonstudio.ccaeroworks.radarcompat.client

import de.teutonstudio.ccaeroworks.radarcompat.display.RadarLinkStatus
import de.teutonstudio.ccaeroworks.radarcompat.display.RadarSourceDescriptor
import de.teutonstudio.ccaeroworks.radarcompat.display.RadarSourceKey
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RadarSourceChoicesTest {
    private val dimension = ResourceLocation.parse("minecraft:overworld")
    private fun source(pos: BlockPos, status: RadarLinkStatus) = RadarSourceDescriptor(
        RadarSourceKey(dimension, pos), 1, "desk", pos, null, status
    )

    @Test
    fun localAndRemoteInputsStayDistinctAndDuplicatesAreRemoved() {
        val local = source(BlockPos(1, 2, 3), RadarLinkStatus.NOT_LINKED)
        val remote = source(BlockPos(4, 5, 6), RadarLinkStatus.ACTIVE)
        val choices = radarSourceChoices(local.ingressPos, listOf(local, remote, remote), remote.ingressPos)
        assertEquals(2, choices.size)
        assertNull(choices[0].ingressPos)
        assertEquals(RadarLinkStatus.NOT_LINKED, choices[0].descriptor?.status)
        assertEquals(remote, choices[1].descriptor)
    }

    @Test
    fun explicitOwnIngressKeepsItsStatus() {
        val local = source(BlockPos(1, 2, 3), RadarLinkStatus.ACTIVE)
        val choices = radarSourceChoices(local.ingressPos, listOf(local), local.ingressPos)
        assertEquals(local, choices.last().descriptor)
        assertEquals(local.ingressPos, choices.last().ingressPos)
    }

    @Test
    fun missingStoredSourceRemainsVisibleInsteadOfFallingBackToLocal() {
        val missing = BlockPos(20481032, 129, 20481032)
        val choices = radarSourceChoices(BlockPos.ZERO, emptyList(), missing)
        assertEquals(listOf(null, missing), choices.map { it.ingressPos })
        assertNull(choices.last().descriptor)
        assertEquals("20481032,129,20481032", radarSourceKey(missing))
    }
}
