package de.teutonstudio.ccaeroworks.computer

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import dan200.computercraft.api.ComputerCraftAPI
import dan200.computercraft.core.computer.ComputerSide
import dan200.computercraft.impl.BundledRedstone
import dan200.computercraft.shared.ModRegistry
import dan200.computercraft.shared.computer.core.ComputerFamily
import dan200.computercraft.shared.computer.core.ServerComputer
import dan200.computercraft.shared.computer.core.ServerContext
import dan200.computercraft.shared.computer.core.TerminalSize
import dan200.computercraft.shared.computer.inventory.ComputerMenuWithoutInventory
import dan200.computercraft.shared.network.container.ComputerContainerData
import dan200.computercraft.shared.platform.ComponentAccess
import dan200.computercraft.shared.platform.PlatformHelper
import dan200.computercraft.shared.util.DirectionUtil
import dan200.computercraft.shared.util.NonNegativeId
import dan200.computercraft.shared.util.RedstoneUtil
import dan200.computercraft.shared.util.StorageCapacity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskService
import de.teutonstudio.ccaeroworks.compat.aeroworks.DeskInputSnapshot
import de.teutonstudio.ccaeroworks.compat.aeroworks.DeskIdentityAccess
import de.teutonstudio.ccaeroworks.computer.control.ControlOverrideManager
import de.teutonstudio.ccaeroworks.computer.wire.WireChannelBank
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import de.teutonstudio.ccaeroworks.registry.CCDataComponents
import de.TeutonStudio.TeutonLib.api.computer.hosted.HostedComputer
import de.TeutonStudio.TeutonLib.api.computer.hosted.HostedComputerPersistenceKeys
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.MenuProvider
import net.minecraft.world.Nameable
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import java.util.UUID

/** Computer installed in an ordinary Aeroworks console. Never a second block entity. */
class ConsoleComputer(val host: ConsoleBlockEntity, private val installed: ItemStack) : MenuProvider, Nameable {
    val level get() = host.level
    val blockPos get() = host.blockPos
    val blockState get() = host.blockState
    val isRemoved get() = host.isRemoved
    fun setChanged() = host.setChanged()
    fun sendData() = host.sendData()
    val groups = de.teutonstudio.ccaeroworks.computer.channel.ChannelGroupBank(this)

    val deskId: UUID
        get() = (host as DeskIdentityAccess).ccaeroworks_getDeskId()

    var radarDestinationDeskId: String? = null
        private set

    internal val wireBank = WireChannelBank(this)

    private var lastNetworkRevision: Long = Long.MIN_VALUE
    private var lastInputs: Map<String, Map<Int, DeskInputSnapshot>> = emptyMap()

    private val peripheralAccess: ComponentAccess<dan200.computercraft.api.peripheral.IPeripheral> =
        PlatformHelper.get().createPeripheralAccess(host) {}

    private val computer = HostedComputer(
        host = host,
        installed = installed,
        family = family,
        initialPowered = installed.get(CCDataComponents.COMPUTER_POWERED.get()) ?: false,
        configure = { properties -> properties.addComponent(CCComputerComponents.CONSOLE, ComputerConsoleAccess(this)) },
        refreshInputs = ::refreshComputerInputs,
        changed = { setChanged(); sendData() }
    )

    val family: ComputerFamily
        get() = if (installed.`is`(ModRegistry.Items.COMPUTER_ADVANCED.get())) ComputerFamily.ADVANCED else ComputerFamily.NORMAL

    val isAdvanced: Boolean
        get() = family == ComputerFamily.ADVANCED

    fun setRadarDestinationDeskId(value: String?) {
        val normalized = value?.takeIf(String::isNotBlank)
        if (radarDestinationDeskId == normalized) return
        radarDestinationDeskId = normalized
        setChanged()
        sendData()
    }

    init {
        radarDestinationDeskId = installed.get(CCDataComponents.RADAR_DESTINATION_DESK_ID.get())
        wireBank.loadEncodedDefinitions(installed.get(CCDataComponents.WIRE_CHANNELS.get()))
        groups.loadEncodedDefinitions(installed.get(CCDataComponents.CHANNEL_GROUPS.get()))
    }

    fun item(): ItemStack = installed.copy().also(::collectSafeComputerComponents)

    fun wireChannelNames(): List<String> = wireBank.channelNames()

    internal fun isRuntimePowered(): Boolean = computer.serverComputer()?.isOn == true

    internal fun markWireChannelsChanged() {
        setChanged()
        sendData()
    }

    /**
     * Queue an event for the embedded CraftOS runtime without losing the event while the computer
     * is starting. CC:Tweaked intentionally drops queueEvent calls while a computer is off, while
     * turnOn() only schedules an asynchronous start. Display interactions need to survive that gap.
     */
    internal fun queueComputerEventWhenReady(name: String, vararg arguments: Any?) {
        computer.queueEventWhenReady(name, *arguments)
    }

    fun tick() {
        val serverLevel = level as? ServerLevel ?: return
        val serverComputer = computer.tick() ?: run {
            ControlOverrideManager.tick(this, false)
            wireBank.tick(false)
            return
        }
        val newPowered = serverComputer.isOn
        ControlOverrideManager.tick(this, newPowered)
        wireBank.tick(newPowered)
        publishConsoleEvents(serverComputer)
        PeripheralNetworkRuntimes.tick(this)
    }

    fun createServerComputer(): ServerComputer = computer.createServerComputer()

    fun getServerComputer(): ServerComputer? = computer.serverComputer()

    fun openTerminal(player: Player, direct: Boolean = false): Boolean {
        if (!family.checkUsable(player)) return false
        if (!direct) {
            val currentLevel = level ?: return false
            val snapshot = ConsoleMultiblockManager.resolve(currentLevel, blockPos)
            if (snapshot.state != ConsoleNetworkState.ACTIVE || snapshot.owner !== this) return false
        }

        val canUse: (Player) -> Boolean = if (direct) {
            { candidate ->
                val currentLevel = level
                currentLevel != null && candidate.level() === currentLevel &&
                    candidate.distanceToSqr(blockPos.center) <=
                    (candidate.blockInteractionRange() + 1.0) * (candidate.blockInteractionRange() + 1.0)
            }
        } else ::isUsableFromNetwork
        val serverComputer = computer.prepareMenu(player, canUse)
        val displayStack = installed.copy()
        collectSafeComputerComponents(displayStack)
        displayStack.set(CCDataComponents.DESK_ID.get(), deskId.toString())
        PlatformHelper.get().openMenu(
            player,
            displayName,
            this,
            ComputerContainerData(serverComputer, displayStack)
        )
        return true
    }

    fun isUsableFromNetwork(player: Player): Boolean {
        val currentLevel = level ?: return false
        if (player.level() !== currentLevel || !family.checkUsable(player)) return false
        val snapshot = ConsoleMultiblockManager.resolve(currentLevel, blockPos)
        if (snapshot.state != ConsoleNetworkState.ACTIVE || snapshot.owner !== this) return false
        val maximum = player.blockInteractionRange() + 1.0
        return snapshot.members.any { player.distanceToSqr(it.pos.center) <= maximum * maximum }
    }

    override fun createMenu(id: Int, inventory: Inventory, player: Player): AbstractContainerMenu {
        return computer.createMenu(id, inventory, player)
    }

    fun writeToItem(stack: ItemStack) {
        collectSafeComputerComponents(stack)
    }

    fun redstoneOutput(direction: Direction): Int {
        val computer = getServerComputer() ?: return 0
        return computer.getRedstoneOutput(remap(direction.opposite))
    }

    fun bundledRedstoneOutput(direction: Direction): Int {
        val computer = getServerComputer() ?: return 0
        return computer.getBundledRedstoneOutput(remap(direction))
    }

    fun invalidate() {
        ControlOverrideManager.releaseAll(this, "invalidated")
        wireBank.shutdown()
        closeComputer()
    }

    fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        computer.write(tag, COMPUTER_KEYS)
        radarDestinationDeskId?.let { tag.putString(NBT_RADAR_DESTINATION_DESK_ID, it) }
        wireBank.encodedDefinitions().takeIf(String::isNotEmpty)?.let {
            tag.putString(NBT_WIRE_CHANNELS, it)
        }
    }

    fun read(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        computer.read(tag, COMPUTER_KEYS, clientPacket)
        radarDestinationDeskId = tag.getString(NBT_RADAR_DESTINATION_DESK_ID).takeIf(String::isNotEmpty)
        wireBank.loadEncodedDefinitions(tag.getString(NBT_WIRE_CHANNELS).takeIf(String::isNotEmpty))
    }

    override fun getName(): Component =
        computer.label?.let { Component.literal(it) } ?: installed.hoverName

    override fun getDisplayName(): Component = name

    override fun getCustomName(): Component? = computer.label?.let { Component.literal(it) }

    override fun hasCustomName(): Boolean = !computer.label.isNullOrEmpty()

    private fun collectSafeComputerComponents(stack: ItemStack) {
        computer.writeStandardComponents(stack)
        stack.set(CCDataComponents.COMPUTER_POWERED.get(), computer.powered)
        stack.set(CCDataComponents.RADAR_DESTINATION_DESK_ID.get(), radarDestinationDeskId)
        wireBank.encodedDefinitions().takeIf(String::isNotEmpty)?.let {
            stack.set(CCDataComponents.WIRE_CHANNELS.get(), it)
        }
        stack.set(CCDataComponents.CHANNEL_GROUPS.get(), groups.encodedDefinitions().takeIf(String::isNotEmpty))
    }

    private fun closeComputer() {
        computer.close()
    }

    private fun refreshComputerInputs(computer: ServerComputer) {
        val currentLevel = level ?: return
        val networkMembers = ConsoleMultiblockManager.resolve(currentLevel, blockPos)
            .members
            .mapTo(hashSetOf()) { it.pos }
        for (direction in Direction.values()) {
            val targetPos = blockPos.relative(direction)
            val local = remap(direction)
            computer.setRedstoneInput(
                local,
                RedstoneUtil.getRedstoneInput(currentLevel, targetPos, direction),
                BundledRedstone.getOutput(currentLevel, targetPos, direction.opposite)
            )
            val peripheral = if (targetPos in networkMembers) null else peripheralAccess.get(direction)
            computer.setPeripheral(local, peripheral)
        }
        if (computer.pollRedstoneChanges() != 0) {
            currentLevel.updateNeighborsAt(blockPos, blockState.block)
        }
    }

    private fun remap(direction: Direction): ComputerSide {
        var side = DirectionUtil.toLocal(
            blockState.getValue(BlockStateProperties.HORIZONTAL_FACING),
            direction
        )
        if (side == ComputerSide.LEFT) side = ComputerSide.RIGHT
        else if (side == ComputerSide.RIGHT) side = ComputerSide.LEFT
        return side
    }

    private fun publishConsoleEvents(computer: ServerComputer) {
        val currentLevel = level ?: return
        val snapshot = ConsoleMultiblockManager.resolve(currentLevel, blockPos)
        if (snapshot.revision != lastNetworkRevision) {
            computer.queueEvent(
                CCAeroworks.CONSOLE_CHANGED_EVENT,
                arrayOf<Any?>(snapshot.state.name.lowercase(), snapshot.members.size, snapshot.revision)
            )
            lastNetworkRevision = snapshot.revision
            lastInputs = emptyMap()
        }
        if (snapshot.state != ConsoleNetworkState.ACTIVE || snapshot.owner !== this) return

        val current = snapshot.members.associate { member ->
            member.id to AeroworksDeskService.snapshotInputs(member.desk)
        }
        if (lastInputs.isNotEmpty()) {
            snapshot.members.forEach { member ->
                val previousDesk = lastInputs[member.id].orEmpty()
                val currentDesk = current[member.id].orEmpty()
                val sockets = previousDesk.keys + currentDesk.keys
                sockets.forEach { socket ->
                    val previous = previousDesk[socket]
                    val next = currentDesk[socket]
                    val channels = previous?.channels.orEmpty().keys + next?.channels.orEmpty().keys
                    channels.forEach { channel ->
                        val oldValue = previous?.channels?.get(channel)
                        val newValue = next?.channels?.get(channel)
                        if (oldValue != newValue) {
                            computer.queueEvent(
                                CCAeroworks.CONSOLE_INPUT_EVENT,
                                de.teutonstudio.ccaeroworks.compat.computercraft.ConsoleInputEventArguments.create(
                                    member.id,
                                    member.index,
                                    socket,
                                    next?.moduleId ?: previous?.moduleId.orEmpty(),
                                    newValue,
                                    channel,
                                    de.teutonstudio.ccaeroworks.display.ControlScriptBindings.path(member.desk, socket),
                                    oldValue
                                )
                            )
                        }
                    }
                }
            }
        }
        lastInputs = current
    }

    companion object {
        private const val NBT_COMPUTER_ID = "CCAeroworksComputerId"
        private const val NBT_LABEL = "CCAeroworksLabel"
        private const val NBT_CAPACITY = "CCAeroworksCapacity"
        private const val NBT_TERMINAL_WIDTH = "CCAeroworksTerminalWidth"
        private const val NBT_TERMINAL_HEIGHT = "CCAeroworksTerminalHeight"
        private const val NBT_POWERED = "CCAeroworksPowered"
        private const val NBT_RADAR_DESTINATION_DESK_ID = "CCAeroworksRadarDestinationDeskId"
        private const val NBT_WIRE_CHANNELS = "CCAeroworksWireChannels"
        private val COMPUTER_KEYS = HostedComputerPersistenceKeys(
            NBT_COMPUTER_ID, NBT_LABEL, NBT_CAPACITY, NBT_TERMINAL_WIDTH, NBT_TERMINAL_HEIGHT, NBT_POWERED
        )
    }
}
