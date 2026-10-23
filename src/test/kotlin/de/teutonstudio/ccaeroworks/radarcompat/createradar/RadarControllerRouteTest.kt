package de.teutonstudio.ccaeroworks.radarcompat.createradar

import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RadarControllerRouteTest {
    private val local = BlockPos(1, 2, 3)
    private val remote = BlockPos(4, 5, 6)

    @Test
    fun nativeMonitorEndpointKeepsPriorityOverComputerControllers() {
        val route = selectRadarControllerRoute(local, true, listOf(remote))

        assertEquals(local, route.filtererPos)
        assertTrue(route.nativeMonitorEndpoint)
        assertFalse(route.ambiguous)
    }

    @Test
    fun endpointVisibleToComputerBecomesDirectRadarRoute() {
        val route = selectRadarControllerRoute(local, false, listOf(remote, local))

        assertEquals(local, route.filtererPos)
        assertFalse(route.nativeMonitorEndpoint)
        assertFalse(route.ambiguous)
    }

    @Test
    fun oneControllerInConsoleNetworkCanFeedAnUnlinkedDisplayDesk() {
        val route = selectRadarControllerRoute(null, false, listOf(remote, remote))

        assertEquals(remote, route.filtererPos)
        assertFalse(route.nativeMonitorEndpoint)
        assertFalse(route.ambiguous)
    }

    @Test
    fun multipleControllersRequireAnExplicitFutureSelectionInsteadOfGuessing() {
        val route = selectRadarControllerRoute(null, false, listOf(local, remote))

        assertNull(route.filtererPos)
        assertFalse(route.nativeMonitorEndpoint)
        assertTrue(route.ambiguous)
    }
}
