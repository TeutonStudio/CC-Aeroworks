package de.teutonstudio.ccaeroworks.network

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Small per-player budget which resets automatically when the server tick changes.
 *
 * It is intentionally independent from packet types so hot network paths can cheaply reject a
 * burst before doing geometry, multiblock or Lua work. Player lifecycle cleanup still removes the
 * retained counter entry eagerly.
 */
internal class PlayerTickBudget(
    private val maxPacketsPerTick: Int,
    private val maxUnitsPerTick: Int
) {
    private data class Entry(val tick: Long, val packets: Int, val units: Int)

    private val entries = ConcurrentHashMap<UUID, Entry>()

    init {
        require(maxPacketsPerTick > 0) { "maxPacketsPerTick must be positive" }
        require(maxUnitsPerTick > 0) { "maxUnitsPerTick must be positive" }
    }

    @Synchronized
    fun tryConsume(playerId: UUID, tick: Long, units: Int): Boolean {
        if (units < 0 || units > maxUnitsPerTick) return false
        val previous = entries[playerId]
        val current = if (previous?.tick == tick) previous else Entry(tick, 0, 0)
        if (current.packets >= maxPacketsPerTick || current.units + units > maxUnitsPerTick) return false
        entries[playerId] = current.copy(packets = current.packets + 1, units = current.units + units)
        return true
    }

    fun clearPlayer(playerId: UUID) {
        entries.remove(playerId)
    }

    fun clear() = entries.clear()

    internal fun size(): Int = entries.size
}
