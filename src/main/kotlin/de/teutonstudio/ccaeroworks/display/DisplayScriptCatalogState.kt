package de.teutonstudio.ccaeroworks.display

import net.minecraft.core.BlockPos
import java.util.concurrent.ConcurrentHashMap

/** Client-readable metadata cache. It contains no client-only classes and is safe in common payload code. */
object DisplayScriptCatalogState {
    private data class Key(val pos: Long, val socket: Int)
    private val catalogs = ConcurrentHashMap<Key, List<DisplayScriptDescriptor>>()

    fun accept(pos: BlockPos, socket: Int, entries: List<DisplayScriptDescriptor>) {
        catalogs[Key(pos.asLong(), socket)] = entries.toList()
    }

    fun get(pos: BlockPos, socket: Int): List<DisplayScriptDescriptor> =
        catalogs[Key(pos.asLong(), socket)].orEmpty()

    fun clear(pos: BlockPos, socket: Int) {
        catalogs.remove(Key(pos.asLong(), socket))
    }
}
