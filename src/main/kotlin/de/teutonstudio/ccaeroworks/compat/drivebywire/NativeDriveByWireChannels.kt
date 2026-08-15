package de.teutonstudio.ccaeroworks.compat.drivebywire

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import java.lang.reflect.Method

/**
 * Optional, read-only bridge to Drive By Wire's MultiChannelWireSource. The interface is resolved
 * reflectively so core CC-Aeroworks remains loadable when DBW is absent.
 */
object NativeDriveByWireChannels {
    private data class Access(val type: Class<*>, val getChannels: Method)

    private val access: Access? by lazy {
        runCatching {
            val type = Class.forName("edn.stratodonut.drivebywire.wire.MultiChannelWireSource")
            Access(type, type.getMethod("wire\$getChannels"))
        }.getOrNull()
    }

    fun channels(level: Level, sourcePos: BlockPos): List<String> {
        val resolved = access ?: return emptyList()
        val source = level.getBlockState(sourcePos).block
        if (!resolved.type.isInstance(source)) return emptyList()
        val raw = runCatching { resolved.getChannels.invoke(source) }.getOrNull() as? Iterable<*> ?: return emptyList()
        return raw
            .mapNotNull { it?.toString()?.trim() }
            .filter { it.isNotEmpty() && it != "world" }
            .distinct()
    }
}
