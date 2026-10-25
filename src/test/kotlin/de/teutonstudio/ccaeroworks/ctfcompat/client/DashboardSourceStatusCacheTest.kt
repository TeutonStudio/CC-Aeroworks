package de.teutonstudio.ccaeroworks.ctfcompat.client

import de.teutonstudio.ccaeroworks.ctfcompat.control.DashboardSourceStatus
import de.teutonstudio.ccaeroworks.ctfcompat.network.DashboardSourceStatusPayload
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.minecraft.core.BlockPos

class DashboardSourceStatusCacheTest {
    private val pos = BlockPos(20481032, 129, 20505602)
    private var now = 123_456_789_000L
    private val requests = mutableListOf<Pair<BlockPos, Int>>()
    private val cache = DashboardSourceStatusCache({ now }) { pos, socket ->
        requests += pos to socket
    }

    @Test
    fun `opening selector requests controller status immediately`() {
        assertNull(cache.status(pos, 0))
        cache.ensureRequested(pos, 0)
        assertEquals(listOf(pos to 0), requests)
    }

    @Test
    fun `rendering retries unanswered requests after one second`() {
        cache.ensureRequested(pos, 0)
        repeat(3) {
            now += 250_000_000L
            cache.ensureRequested(pos, 0)
        }
        assertEquals(1, requests.size)

        now += 250_000_000L
        cache.ensureRequested(pos, 0)
        assertEquals(listOf(pos to 0, pos to 0), requests)
    }

    @Test
    fun `received controller status is reused then refreshed after five seconds`() {
        cache.ensureRequested(pos, 0)
        val payload = DashboardSourceStatusPayload(
            pos, 0, DashboardSourceStatus.AVAILABLE, UUID.randomUUID(), pos.below()
        )
        cache.accept(payload)
        assertEquals(payload, cache.status(pos, 0))

        now += 4_999_999_999L
        cache.ensureRequested(pos, 0)
        assertEquals(1, requests.size)

        now += 1L
        cache.ensureRequested(pos, 0)
        assertEquals(2, requests.size)

        now += 999_999_999L
        cache.ensureRequested(pos, 0)
        assertEquals(2, requests.size)

        now += 1L
        cache.ensureRequested(pos, 0)
        assertEquals(3, requests.size)
    }

    @Test
    fun `status polling is independent for each desk and socket`() {
        cache.accept(DashboardSourceStatusPayload(pos, 0, DashboardSourceStatus.UNAVAILABLE, null, null))
        cache.ensureRequested(pos, 0)
        cache.ensureRequested(pos, 1)
        cache.ensureRequested(pos.east(), 0)
        assertEquals(listOf(pos to 1, pos.east() to 0), requests)
    }

    @Test
    fun `negative monotonic timestamps do not delay initial discovery`() {
        now = -123_456_789_000L
        cache.ensureRequested(pos, 0)
        assertEquals(listOf(pos to 0), requests)
    }

    @Test
    fun `request timeout survives monotonic clock wraparound`() {
        now = Long.MAX_VALUE - 500_000_000L
        cache.ensureRequested(pos, 0)
        now += 999_999_999L
        cache.ensureRequested(pos, 0)
        assertEquals(1, requests.size)

        now += 1L
        cache.ensureRequested(pos, 0)
        assertEquals(2, requests.size)
    }
}
