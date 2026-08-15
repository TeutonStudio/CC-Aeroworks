package de.teutonstudio.ccaeroworks.computer.wire

import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import net.minecraft.server.level.ServerLevel
import net.neoforged.fml.ModList
import java.util.UUID

/** Persistent channel definition. Runtime signal state is intentionally not persisted. */
data class WireChannelDefinition(
    val id: UUID,
    val name: String
)

data class WireChannelView(
    val id: UUID,
    val name: String,
    val value: Int,
    val connections: Int,
    val connected: Boolean
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

interface WireBackend {
    val name: String

    fun setValue(channel: String, value: Int)

    fun removeChannel(channel: String)

    fun renameChannel(oldName: String, newName: String, value: Int)

    fun connectionCount(channel: String): Int

    fun clearSignals()

    fun close() {
        clearSignals()
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

    fun create(owner: ComputerControlDeskBlockEntity): WireBackend {
        if (!ModList.get().isLoaded("drivebywire")) return NoWireBackend

        return runCatching {
            val backendClass = Class.forName(DRIVE_BY_WIRE_BACKEND)
            val constructor = backendClass.getConstructor(ComputerControlDeskBlockEntity::class.java)
            constructor.newInstance(owner) as WireBackend
        }.getOrElse { throwable ->
            CCAeroworks.LOGGER.error("Failed to initialize Drive By Wire backend", throwable)
            NoWireBackend
        }
    }
}

class WireChannelBank(
    private val owner: ComputerControlDeskBlockEntity
) {
    private val definitions = mutableListOf<WireChannelDefinition>()
    private val states = hashMapOf<UUID, WireChannelState>()
    private var backend: WireBackend? = null
    private var outputEnabled: Boolean = false

    fun channelNames(): List<String> = definitions.map(WireChannelDefinition::name)

    fun encodedDefinitions(): String = definitions.joinToString("\n") {
        "${it.id}|${it.name}"
    }

    fun loadEncodedDefinitions(encoded: String?) {
        definitions.clear()
        states.clear()
        if (encoded.isNullOrBlank()) return

        val seenNames = hashSetOf<String>()
        val seenIds = hashSetOf<UUID>()
        encoded.lineSequence().forEach { line ->
            if (definitions.size >= MAX_CHANNELS) return@forEach
            val split = line.split('|', limit = 2)
            if (split.size != 2) return@forEach
            val id = runCatching { UUID.fromString(split[0]) }.getOrNull() ?: return@forEach
            val name = split[1]
            if (!isValidName(name) || !seenNames.add(name) || !seenIds.add(id)) return@forEach
            val definition = WireChannelDefinition(id, name)
            definitions += definition
            states[id] = WireChannelState()
        }
    }

    fun addChannel(rawName: String): WireChannelDefinition {
        val name = checkedName(rawName)
        if (definitions.any { it.name == name }) {
            throw IllegalArgumentException("Wire channel '$name' already exists")
        }
        if (definitions.size >= MAX_CHANNELS) {
            throw IllegalStateException("A ComputerControlDesk supports at most $MAX_CHANNELS wire channels")
        }

        val definition = WireChannelDefinition(UUID.randomUUID(), name)
        definitions += definition
        states[definition.id] = WireChannelState()
        changed()
        return definition
    }

    fun removeChannel(rawName: String): WireChannelDefinition =
        removeChannel(definition(rawName).id)

    fun removeChannel(id: UUID): WireChannelDefinition {
        val definition = definition(id)
        val state = states[definition.id] ?: WireChannelState()
        state.pulseEndTick = null
        if (state.value != 0) {
            state.value = 0
            backend().setValue(definition.name, 0)
        }
        backend().removeChannel(definition.name)
        definitions.remove(definition)
        states.remove(definition.id)
        changed()
        return definition
    }

    fun renameChannel(rawOldName: String, rawNewName: String): WireChannelDefinition =
        renameChannel(definition(rawOldName).id, rawNewName)

    fun renameChannel(id: UUID, rawNewName: String): WireChannelDefinition {
        val oldDefinition = definition(id)
        val newName = checkedName(rawNewName)
        if (definitions.any { it.name == newName && it.id != oldDefinition.id }) {
            throw IllegalArgumentException("Wire channel '$newName' already exists")
        }
        if (oldDefinition.name == newName) return oldDefinition

        val state = states[oldDefinition.id] ?: WireChannelState()
        backend().renameChannel(oldDefinition.name, newName, state.value)
        val replacement = oldDefinition.copy(name = newName)
        val index = definitions.indexOf(oldDefinition)
        definitions[index] = replacement
        changed()
        return replacement
    }

    fun exists(rawName: String): Boolean {
        val name = rawName.trim()
        return definitions.any { it.name == name }
    }

    fun value(rawName: String): Int {
        val definition = definition(rawName)
        return states[definition.id]?.value ?: 0
    }

    fun setValue(rawName: String, value: Int) {
        requireOutputEnabled()
        require(value in 0..15) { "Wire signal must be between 0 and 15" }
        val definition = definition(rawName)
        val state = states.getOrPut(definition.id, ::WireChannelState)
        state.pulseEndTick = null
        if (state.value == value) return
        state.value = value
        backend().setValue(definition.name, value)
    }

    fun pulse(rawName: String, durationTicks: Int, value: Int) {
        requireOutputEnabled()
        require(durationTicks in 1..MAX_PULSE_TICKS) {
            "Wire pulse duration must be between 1 and $MAX_PULSE_TICKS ticks"
        }
        require(value in 1..15) { "Wire pulse value must be between 1 and 15" }
        val level = owner.level as? ServerLevel
            ?: throw IllegalStateException("ComputerControlDesk is not in a server level")
        val definition = definition(rawName)
        val state = states.getOrPut(definition.id, ::WireChannelState)
        state.value = value
        state.pulseEndTick = level.gameTime + durationTicks
        backend().setValue(definition.name, value)
    }

    fun reset(rawName: String) {
        val definition = definition(rawName)
        val state = states.getOrPut(definition.id, ::WireChannelState)
        state.pulseEndTick = null
        if (state.value == 0) return
        state.value = 0
        backend().setValue(definition.name, 0)
    }

    fun resetAll() {
        resetAllInternal()
    }

    fun snapshot(): WireChannelBankView {
        val activeBackend = backend()
        return WireChannelBankView(
            backend = activeBackend.name,
            enabled = outputEnabled,
            channels = definitions.map { definition ->
                val state = states[definition.id] ?: WireChannelState()
                val connections = activeBackend.connectionCount(definition.name)
                WireChannelView(
                    id = definition.id,
                    name = definition.name,
                    value = state.value,
                    connections = connections,
                    connected = connections > 0
                )
            }
        )
    }

    fun describeChannels(): Map<String, Any> = linkedMapOf<String, Any>().apply {
        definitions.forEach { definition ->
            put(definition.name, describeChannel(definition.name))
        }
    }

    fun describeChannel(rawName: String): Map<String, Any> {
        val definition = definition(rawName)
        val state = states[definition.id] ?: WireChannelState()
        val activeBackend = backend()
        val connections = activeBackend.connectionCount(definition.name)
        return linkedMapOf(
            "id" to definition.id.toString(),
            "name" to definition.name,
            "value" to state.value,
            "backend" to activeBackend.name,
            "connected" to (connections > 0),
            "connections" to connections,
            "enabled" to outputEnabled
        )
    }

    fun backendName(): String = backend().name

    fun isOutputEnabled(): Boolean = outputEnabled

    fun tick(computerOn: Boolean) {
        val level = owner.level as? ServerLevel ?: return
        val enabledNow = computerOn && ownsActiveNetwork(level)
        if (!enabledNow) {
            if (outputEnabled || states.values.any { it.value != 0 || it.pulseEndTick != null }) {
                resetAllInternal()
            }
            outputEnabled = false
            return
        }

        outputEnabled = true
        val now = level.gameTime
        definitions.forEach { definition ->
            val state = states[definition.id] ?: return@forEach
            val pulseEnd = state.pulseEndTick ?: return@forEach
            if (now < pulseEnd) return@forEach
            state.pulseEndTick = null
            if (state.value != 0) {
                state.value = 0
                backend().setValue(definition.name, 0)
            }
        }
    }

    fun shutdown() {
        resetAllInternal()
        backend?.close()
        backend = null
        outputEnabled = false
    }

    private fun resetAllInternal() {
        states.values.forEach { state ->
            state.value = 0
            state.pulseEndTick = null
        }
        backend?.clearSignals()
    }

    private fun ownsActiveNetwork(level: ServerLevel): Boolean {
        val snapshot = ConsoleMultiblockManager.resolve(level, owner.blockPos)
        return snapshot.state == ConsoleNetworkState.ACTIVE && snapshot.owner === owner
    }

    private fun requireOutputEnabled() {
        if (!outputEnabled) {
            throw IllegalStateException("Wire outputs are disabled until the ComputerControlDesk owns an ACTIVE console network")
        }
    }

    private fun definition(rawName: String): WireChannelDefinition {
        val name = rawName.trim()
        return definitions.firstOrNull { it.name == name }
            ?: throw NoSuchElementException("Unknown wire channel '$name'")
    }

    private fun definition(id: UUID): WireChannelDefinition =
        definitions.firstOrNull { it.id == id }
            ?: throw NoSuchElementException("Unknown wire channel '$id'")

    private fun checkedName(rawName: String): String {
        val name = rawName.trim()
        require(isValidName(name)) {
            "Invalid wire channel '$name'. Use lowercase letters, digits, '_' or '-', starting with a letter"
        }
        return name
    }

    private fun changed() {
        owner.markWireChannelsChanged()
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
