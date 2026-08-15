package de.teutonstudio.ccaeroworks.compat.drivebywire

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.computer.channel.ControlChannelSemantics
import net.neoforged.fml.ModList
import java.lang.reflect.Method

data class NativeDriveByWireChannel(
    val id: String,
    val socket: Int,
    val channelId: String,
    /** Aeroworks physical output direction: -1, 0 or +1. */
    val sign: Int
)

/**
 * Read-only bridge to Aeroworks' own Drive By Wire channel resolver.
 *
 * ConsoleWireChannels is the authority for modular ControlDesk DBW IDs. We deliberately do not
 * rebuild its `socket/channelId/sign` encoding and do not ask the DBW Block interface, because
 * ControlDesk channels depend on the currently mounted modules in ConsoleBlockEntity.
 */
object NativeDriveByWireChannels {
    private const val DRIVE_BY_WIRE_MOD_ID = "drivebywire"
    private const val CONSOLE_WIRE_CHANNELS = "com.mred231.aeroworks.compat.drivebywire.ConsoleWireChannels"
    private const val WIRE_CHANNEL = "$CONSOLE_WIRE_CHANNELS\$WireChannel"

    private data class Access(
        val channelsFor: Method,
        val parse: Method,
        val socket: Method,
        val channelId: Method,
        val sign: Method
    )

    private val access: Access? by lazy {
        if (!ModList.get().isLoaded(DRIVE_BY_WIRE_MOD_ID)) return@lazy null
        runCatching {
            val resolver = Class.forName(CONSOLE_WIRE_CHANNELS)
            val wireChannel = Class.forName(WIRE_CHANNEL)
            Access(
                channelsFor = resolver.getMethod("channelsFor", ConsoleBlockEntity::class.java),
                parse = resolver.getMethod("parse", String::class.java),
                socket = wireChannel.getMethod("socket"),
                channelId = wireChannel.getMethod("channelId"),
                sign = wireChannel.getMethod("sign")
            )
        }.getOrNull()
    }

    fun parse(id: String): NativeDriveByWireChannel? {
        val resolved = access ?: return null
        val parsed = runCatching { resolved.parse.invoke(null, id) }.getOrNull() ?: return null
        val socket = (runCatching { resolved.socket.invoke(parsed) }.getOrNull() as? Number)?.toInt()
            ?: return null
        val channelId = runCatching { resolved.channelId.invoke(parsed) }.getOrNull()?.toString()
            ?.takeIf(String::isNotBlank)
            ?: return null
        val sign = (runCatching { resolved.sign.invoke(parsed) }.getOrNull() as? Number)?.toInt()
            ?: return null
        return NativeDriveByWireChannel(id, socket, channelId, sign)
    }

    fun channels(desk: ConsoleBlockEntity): List<NativeDriveByWireChannel> {
        val resolved = access ?: return emptyList()
        val raw = runCatching { resolved.channelsFor.invoke(null, desk) }.getOrNull() as? Iterable<*> ?: return emptyList()
        return raw.mapNotNull { rawId ->
            rawId?.toString()?.takeIf(String::isNotBlank)?.let(::parse)
        }.distinctBy(NativeDriveByWireChannel::id)
    }

    /**
     * Filter an already resolved Aeroworks channel list without recursively invoking channelsFor.
     * Used from the ConsoleWireChannels return hook so display-pointer x/y never reach DBW cycling.
     */
    fun filterExposedIds(desk: ConsoleBlockEntity, ids: List<String>): List<String> = ids.filter { id ->
        val parsed = parse(id) ?: return@filter false
        if (parsed.socket !in 0 until desk.socketCount()) return@filter false
        val module = desk.module(parsed.socket) ?: return@filter false
        ControlChannelSemantics.isDriveByWireExposed(module, parsed.channelId)
    }
}
