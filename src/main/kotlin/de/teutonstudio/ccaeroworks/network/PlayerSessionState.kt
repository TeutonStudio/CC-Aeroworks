package de.teutonstudio.ccaeroworks.network

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Bounded server-side state owned by one player per key.
 *
 * Callers create or refresh entries with [put]/[touch], explicitly remove them when the player's
 * lifecycle ends, and use [expire] as a fallback if a lifecycle event or terminal packet is lost.
 */
internal class PlayerSessionState<K, V>(
    private val playerForKey: (K) -> UUID,
    private val ttlTicks: Long
) {
    private data class Entry<V>(val value: V, val lastTouchedTick: Long)

    private val entries = ConcurrentHashMap<K, Entry<V>>()

    fun get(key: K): V? = entries[key]?.value

    fun touch(key: K, tick: Long): V? = entries.computeIfPresent(key) { _, entry ->
        entry.copy(lastTouchedTick = tick)
    }?.value

    fun put(key: K, value: V, tick: Long): V? =
        entries.put(key, Entry(value, tick))?.value

    fun remove(key: K): V? = entries.remove(key)?.value

    fun removePlayer(playerId: UUID) {
        entries.keys.removeIf { playerForKey(it) == playerId }
    }

    fun expire(tick: Long) {
        entries.entries.removeIf { (_, entry) -> tick - entry.lastTouchedTick > ttlTicks }
    }

    fun clear() = entries.clear()

    internal fun size(): Int = entries.size
}
