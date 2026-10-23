package de.teutonstudio.ccaeroworks.computer.wire

import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.computer.ConsoleComputer
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import de.TeutonStudio.TeutonLib.api.computer.channel.SignalBackend
import de.TeutonStudio.TeutonLib.api.computer.channel.SignalBank
import de.TeutonStudio.TeutonLib.api.computer.channel.SignalDefinition
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerPlayer
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.neoforged.fml.ModList
import java.util.UUID

/** Persistent channel definition. Runtime signal state is intentionally not persisted. */
typealias WireChannelDefinition = SignalDefinition

data class WireConnectionView(
    val x: Int,
    val y: Int,
    val z: Int,
    val side: String
)

data class WireChannelView(
    val id: UUID,
    val name: String,
    val value: Int,
    val connections: Int,
    val connected: Boolean,
    val targets: List<WireConnectionView>
)

data class WireChannelBankView(
    val backend: String,
    val enabled: Boolean,
    val channels: List<WireChannelView>
)

private data class WireChannelState(
    var value: Int = 0,
    var pulseEndTick: Long? = null
)

interface WireBackend : SignalBackend {

    /** Read-only DBW topology lookup used by the channel GUI for any logical desk source. */
    fun connectionTargets(sourcePos: BlockPos, channel: String): List<WireConnectionView> = emptyList()

    fun changeConnection(sourcePos: BlockPos, channel: String, target: BlockPos, side: Direction, add: Boolean, player: ServerPlayer) {
        throw IllegalStateException("Drive By Wire is not available")
    }

}

private object NoWireBackend : WireBackend {
    override val name: String = "none"

    override fun setValue(channel: String, value: Int) = Unit

    override fun removeChannel(channel: String) = Unit

    override fun renameChannel(oldName: String, newName: String, value: Int) = Unit

    override fun connectionCount(channel: String): Int = 0

    override fun clearSignals() = Unit
}

private object WireBackends {
    private const val DRIVE_BY_WIRE_BACKEND =
        "de.teutonstudio.ccaeroworks.compat.drivebywire.DriveByWireWireBackend"

    fun create(owner: ConsoleComputer): WireBackend {
        if (!ModList.get().isLoaded("drivebywire")) return NoWireBackend

        return runCatching {
            val backendClass = Class.forName(DRIVE_BY_WIRE_BACKEND)
            val constructor = backendClass.getConstructor(ConsoleComputer::class.java)
            constructor.newInstance(owner) as WireBackend
        }.getOrElse { throwable ->
            CCAeroworks.LOGGER.error("Failed to initialize Drive By Wire backend", throwable)
            NoWireBackend
        }
    }
}

class WireChannelBank(
    private val owner: ConsoleComputer
) {
    private var backend: WireBackend? = null
    private var computerOn: Boolean = false
    private val signals = SignalBank(
        clock = { (owner.level as? ServerLevel)?.gameTime ?: 0L },
        authority = { computerOn && (owner.level as? ServerLevel)?.let(::ownsActiveNetwork) == true },
        backendFactory = ::backend,
        changed = owner::markWireChannelsChanged,
        validName = ::isValidName,
        maxChannels = MAX_CHANNELS,
        maxPulseTicks = MAX_PULSE_TICKS,
        subject = "Wire channel"
    )

    fun channelNames(): List<String> = signals.channelNames()

    fun encodedDefinitions(): String = signals.encodedDefinitions()

    fun loadEncodedDefinitions(encoded: String?) = signals.loadEncodedDefinitions(encoded)

    fun addChannel(rawName: String): WireChannelDefinition = signals.addChannel(rawName)

    fun removeChannel(rawName: String): WireChannelDefinition = signals.removeChannel(rawName)
    fun removeChannel(id: UUID): WireChannelDefinition = signals.removeChannel(id)

    fun renameChannel(rawOldName: String, rawNewName: String): WireChannelDefinition =
        signals.renameChannel(rawOldName, rawNewName)
    fun renameChannel(id: UUID, rawNewName: String): WireChannelDefinition = signals.renameChannel(id, rawNewName)

    fun exists(rawName: String): Boolean = signals.exists(rawName)

    fun value(rawName: String): Int = signals.value(rawName)

    fun setValue(rawName: String, value: Int) = signals.setValue(rawName, value)

    fun pulse(rawName: String, durationTicks: Int, value: Int) = signals.pulse(rawName, durationTicks, value)

    fun reset(rawName: String) = signals.reset(rawName)

    fun resetAll() = signals.resetAll()

    fun connectionTargets(sourcePos: BlockPos, channel: String): List<WireConnectionView> =
        backend().connectionTargets(sourcePos, channel)

    fun changeConnection(sourcePos: BlockPos, channel: String, target: BlockPos, side: Direction, add: Boolean, player: ServerPlayer) =
        backend().changeConnection(sourcePos, channel, target, side, add, player)

    fun snapshot(): WireChannelBankView {
        val activeBackend = backend()
        return WireChannelBankView(
            backend = activeBackend.name,
            enabled = signals.isOutputEnabled(),
            channels = signals.definitions().map { definition ->
                val targets = activeBackend.connectionTargets(owner.blockPos, definition.name)
                WireChannelView(
                    id = definition.id,
                    name = definition.name,
                    value = signals.value(definition.name),
                    connections = targets.size,
                    connected = targets.isNotEmpty(),
                    targets = targets
                )
            }
        )
    }

    fun describeChannels(): Map<String, Any> = linkedMapOf<String, Any>().apply {
        signals.definitions().forEach { definition ->
            put(definition.name, describeChannel(definition.name))
        }
    }

    fun describeChannel(rawName: String): Map<String, Any> {
        val definition = signals.definitions().firstOrNull { it.name == rawName.trim() }
            ?: throw NoSuchElementException("Unknown wire channel '${rawName.trim()}'")
        val activeBackend = backend()
        val targets = activeBackend.connectionTargets(owner.blockPos, definition.name)
        return linkedMapOf(
            "id" to definition.id.toString(),
            "name" to definition.name,
            "value" to signals.value(definition.name),
            "backend" to activeBackend.name,
            "connected" to targets.isNotEmpty(),
            "connections" to targets.size,
            "targets" to targets.map { target ->
                linkedMapOf(
                    "x" to target.x,
                    "y" to target.y,
                    "z" to target.z,
                    "side" to target.side
                )
            },
            "enabled" to signals.isOutputEnabled()
        )
    }

    fun backendName(): String = backend().name

    fun isOutputEnabled(): Boolean = signals.isOutputEnabled()

    fun tick(computerOn: Boolean) {
        this.computerOn = computerOn
        signals.tick()
    }

    fun shutdown() {
        signals.close()
        backend = null
        computerOn = false
    }

    private fun ownsActiveNetwork(level: ServerLevel): Boolean {
        val snapshot = ConsoleMultiblockManager.resolve(level, owner.blockPos)
        return snapshot.state == ConsoleNetworkState.ACTIVE && snapshot.owner === owner
    }

    private fun backend(): WireBackend {
        backend?.let { return it }
        return WireBackends.create(owner).also { backend = it }
    }

    companion object {
        const val MAX_CHANNELS: Int = 32
        const val MAX_PULSE_TICKS: Int = 1200
        private val CHANNEL_NAME = Regex("[a-z][a-z0-9_-]{0,31}")

        fun isValidName(name: String): Boolean = CHANNEL_NAME.matches(name)
    }
}
