package de.teutonstudio.ccaeroworks.computer

/**
 * Tracks mount locations acquired by one peripheral binding.
 *
 * The binding owns synchronization. [drain] clears the registry before callers begin unmounting,
 * so cleanup remains idempotent even if one unmount operation throws.
 */
internal class PeripheralMountRegistry {
    private val locations = linkedSetOf<String>()

    fun add(location: String) {
        locations += location
    }

    fun remove(location: String) {
        locations -= location
    }

    fun drain(): List<String> = locations.toList().also { locations.clear() }

    fun isEmpty(): Boolean = locations.isEmpty()

    internal fun size(): Int = locations.size
}
