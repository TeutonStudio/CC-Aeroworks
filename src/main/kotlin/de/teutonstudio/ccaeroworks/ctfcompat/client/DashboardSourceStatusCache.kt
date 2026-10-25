package de.teutonstudio.ccaeroworks.ctfcompat.client

import de.teutonstudio.ccaeroworks.ctfcompat.network.DashboardSourceStatusPayload
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.core.BlockPos

/** Polls controller availability while the source selector is being rendered. */
internal class DashboardSourceStatusCache(
    private val nanoTime: () -> Long,
    private val request: (BlockPos, Int) -> Unit
) {
    private data class Key(val pos: BlockPos, val socket: Int)
    private data class TimedStatus(val payload: DashboardSourceStatusPayload, val receivedAt: Long)

    private val statuses = ConcurrentHashMap<Key, TimedStatus>()
    private val requests = ConcurrentHashMap<Key, Long>()

    fun accept(payload: DashboardSourceStatusPayload) {
        val key = Key(payload.pos.immutable(), payload.socket)
        statuses[key] = TimedStatus(payload, nanoTime())
        requests.remove(key)
    }

    fun ensureRequested(pos: BlockPos, socket: Int) {
        val key = Key(pos.immutable(), socket)
        val now = nanoTime()
        // Missing timestamps must not participate in nanoTime subtraction: subtracting
        // Long.MIN_VALUE can overflow and suppress the very first request forever.
        val lastStatus = statuses[key]?.receivedAt
        if (lastStatus != null && now - lastStatus < STATUS_TTL) return
        val lastRequest = requests[key]
        if (lastRequest != null && now - lastRequest < REQUEST_TTL) return
        requests[key] = now
        request(pos, socket)
    }

    fun status(pos: BlockPos, socket: Int): DashboardSourceStatusPayload? =
        statuses[Key(pos, socket)]?.payload

    private companion object {
        const val REQUEST_TTL = 1_000_000_000L
        const val STATUS_TTL = 5_000_000_000L
    }
}
