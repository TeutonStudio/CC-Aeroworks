package de.teutonstudio.ccaeroworks.computer

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
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
import de.teutonstudio.ccaeroworks.registry.CCItems
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentMap
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
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import java.util.UUID

class ComputerControlDeskBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
) : ConsoleBlockEntity(type, pos, state), MenuProvider, Nameable {
    val deskId: UUID
        get() = (this as DeskIdentityAccess).ccaeroworks_getDeskId()

    var radarDestinationDeskId: String? = null
        private set

    internal val wireBank = WireChannelBank(this)

    private var instanceId: UUID? = null
    private var computerId: Int = -1
    private var label: String? = null
    private var storageCapacity: Long = -1
    private var terminalSize: TerminalSize? = null
    private var powered: Boolean = false
    private var startOn: Boolean = false
    private var lastNetworkRevision: Long = Long.MIN_VALUE
    private var lastInputs: Map<String, Map<Int, DeskInputSnapshot>> = emptyMap()
    private val directMenuOpeners = hashSetOf<UUID>()

    private val peripheralAccess: ComponentAccess<dan200.computercraft.api.peripheral.IPeripheral> =
        PlatformHelper.get().createPeripheralAccess(this) {}

    val family: ComputerFamily
        get() = (blockState.block as? ComputerControlDeskBlock)?.family ?: ComputerFamily.NORMAL

    val isAdvanced: Boolean
        get() = family == ComputerFamily.ADVANCED

    fun setRadarDestinationDeskId(value: String?) {
        val normalized = value?.takeIf(String::isNotBlank)
        if (radarDestinationDeskId == normalized) return
        radarDestinationDeskId = normalized
        setChanged()
        sendData()
    }

    fun wireChannelNames(): List<String> = wireBank.channelNames()

    internal fun markWireChannelsChanged() {
        setChanged()
        sendData()
    }

    override fun tick() {
        super.tick()
        val serverLevel = level as? ServerLevel ?: return
        val computer = getServerComputer() ?: if (computerId >= 0 || startOn) {
            createServerComputer()
        } else {
            ControlOverrideManager.tick(this, false)
            wireBank.tick(false)
            return
        }

        computer.setPosition(serverLevel, blockPos)
        if (startOn || powered) {
            computer.turnOn()
            startOn = false
        }
        refreshComputerInputs(computer)
        computer.keepAlive()

        val newPowered = computer.isOn
        val newLabel = computer.label
        if (powered != newPowered || label != newLabel) {
            powered = newPowered
            label = newLabel
            setChanged()
            sendData()
        }

        ControlOverrideManager.tick(this, newPowered)
        wireBank.tick(newPowered)
        publishConsoleEvents(computer)
        PeripheralNetworkRuntimes.tick(this)
    }

    fun createServerComputer(): ServerComputer {
        val serverLevel = level as? ServerLevel
            ?: throw IllegalStateException("Cannot create a server computer outside a ServerLevel")
        val context = ServerContext.get(serverLevel.server)
        instanceId?.let { context.registry().get(it) }?.let { return it }

        if (computerId < 0) {
            computerId = ComputerCraftAPI.createUniqueNumberedSaveDir(serverLevel.server, "computer")
            setChanged()
        }

        val properties = ServerComputer.properties(computerId, family)
            .label(label)
            .terminalSize(terminalSize ?: defaultTerminalSize())
            .storageCapacity(storageCapacity)
            .addComponent(CCComputerComponents.CONSOLE, ComputerConsoleAccess(this))

        val computer = ServerComputer(serverLevel, blockPos, properties)
        instanceId = computer.register()
        refreshComputerInputs(computer)
        return computer
    }

    fun getServerComputer(): ServerComputer? {
        val serverLevel = level as? ServerLevel ?: return null
        return instanceId?.let { ServerContext.get(serverLevel.server).registry().get(it) }
    }

    fun openTerminal(player: Player, direct: Boolean = false): Boolean {
        if (!family.checkUsable(player)) return false
        if (!direct) {
            val currentLevel = level ?: return false
            val snapshot = ConsoleMultiblockManager.resolve(currentLevel, blockPos)
            if (snapshot.state != ConsoleNetworkState.ACTIVE || snapshot.owner !== this) return false
        }

        val computer = createServerComputer()
        computer.turnOn()
        if (direct) directMenuOpeners += player.uuid
        powered = true
        val displayStack = ItemStack(
            if (isAdvanced) CCItems.ADVANCED_COMPUTER_CONTROL_DESK.get()
            else CCItems.COMPUTER_CONTROL_DESK.get()
        )
        collectSafeComputerComponents(displayStack)
        PlatformHelper.get().openMenu(
            player,
            displayName,
            this,
            ComputerContainerData(computer, displayStack)
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
        val direct = directMenuOpeners.remove(player.uuid)
        val canUse: (Player) -> Boolean = if (direct) {
            { candidate ->
                val currentLevel = level
                currentLevel != null &&
                    candidate.level() === currentLevel &&
                    candidate.distanceToSqr(blockPos.center) <=
                    (candidate.blockInteractionRange() + 1.0) * (candidate.blockInteractionRange() + 1.0)
            }
        } else {
            ::isUsableFromNetwork
        }
        return ComputerMenuWithoutInventory(
            ModRegistry.Menus.COMPUTER.get(),
            id,
            inventory,
            canUse,
            createServerComputer()
        )
    }

    fun writeToItem(stack: ItemStack) {
        stack.applyComponents(collectComponents())
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

    override fun invalidate() {
        ControlOverrideManager.releaseAll(this, "invalidated")
        wireBank.shutdown()
        closeComputer()
        super.invalidate()
    }

    override fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.write(tag, registries, clientPacket)
        if (computerId >= 0) tag.putInt(NBT_COMPUTER_ID, computerId)
        label?.let { tag.putString(NBT_LABEL, it) }
        if (storageCapacity > 0) tag.putLong(NBT_CAPACITY, storageCapacity)
        terminalSize?.let {
            tag.putInt(NBT_TERMINAL_WIDTH, it.width())
            tag.putInt(NBT_TERMINAL_HEIGHT, it.height())
        }
        radarDestinationDeskId?.let { tag.putString(NBT_RADAR_DESTINATION_DESK_ID, it) }
        wireBank.encodedDefinitions().takeIf(String::isNotEmpty)?.let {
            tag.putString(NBT_WIRE_CHANNELS, it)
        }
        tag.putBoolean(NBT_POWERED, powered)
    }

    override fun read(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.read(tag, registries, clientPacket)
        computerId = if (tag.contains(NBT_COMPUTER_ID)) tag.getInt(NBT_COMPUTER_ID) else -1
        label = tag.getString(NBT_LABEL).takeIf { it.isNotEmpty() }
        storageCapacity = if (tag.contains(NBT_CAPACITY)) tag.getLong(NBT_CAPACITY) else -1
        terminalSize = if (tag.contains(NBT_TERMINAL_WIDTH) && tag.contains(NBT_TERMINAL_HEIGHT)) {
            TerminalSize(tag.getInt(NBT_TERMINAL_WIDTH), tag.getInt(NBT_TERMINAL_HEIGHT))
        } else {
            null
        }
        radarDestinationDeskId = tag.getString(NBT_RADAR_DESTINATION_DESK_ID).takeIf(String::isNotEmpty)
        wireBank.loadEncodedDefinitions(tag.getString(NBT_WIRE_CHANNELS).takeIf(String::isNotEmpty))
        powered = tag.getBoolean(NBT_POWERED)
        if (!clientPacket) startOn = powered
    }

    override fun collectImplicitComponents(builder: DataComponentMap.Builder) {
        super.collectImplicitComponents(builder)
        builder.set(ModRegistry.DataComponents.COMPUTER_ID.get(), NonNegativeId.of(computerId))
        builder.set(ModRegistry.DataComponents.STORAGE_CAPACITY.get(), storageCapacity.takeIf { it > 0 }?.let(::StorageCapacity))
        builder.set(ModRegistry.DataComponents.TERMINAL_SIZE.get(), terminalSize)
        builder.set(CCDataComponents.COMPUTER_POWERED.get(), powered)
        builder.set(CCDataComponents.RADAR_DESTINATION_DESK_ID.get(), radarDestinationDeskId)
        builder.set(CCDataComponents.WIRE_CHANNELS.get(), wireBank.encodedDefinitions().takeIf(String::isNotEmpty))
        builder.set(DataComponents.CUSTOM_NAME, label?.let { Component.literal(it) })
    }

    override fun applyImplicitComponents(component: BlockEntity.DataComponentInput) {
        super.applyImplicitComponents(component)
        computerId = NonNegativeId.getId(component.get(ModRegistry.DataComponents.COMPUTER_ID.get()))
        storageCapacity = StorageCapacity.getOrDefault(
            component.get(ModRegistry.DataComponents.STORAGE_CAPACITY.get()),
            -1
        )
        terminalSize = component.get(ModRegistry.DataComponents.TERMINAL_SIZE.get())
        powered = component.get(CCDataComponents.COMPUTER_POWERED.get()) ?: false
        radarDestinationDeskId = component.get(CCDataComponents.RADAR_DESTINATION_DESK_ID.get())
        wireBank.loadEncodedDefinitions(component.get(CCDataComponents.WIRE_CHANNELS.get()))
        startOn = powered
        label = component.get(DataComponents.CUSTOM_NAME)?.string
    }

    override fun getName(): Component =
        label?.let { Component.literal(it) } ?: Component.translatable(
            if (isAdvanced) "block.cc_aeroworks.advanced_computer_control_desk"
            else "block.cc_aeroworks.computer_control_desk"
        )

    override fun getDisplayName(): Component = name

    override fun getCustomName(): Component? = label?.let { Component.literal(it) }

    override fun hasCustomName(): Boolean = !label.isNullOrEmpty()

    private fun collectSafeComputerComponents(stack: ItemStack) {
        stack.set(ModRegistry.DataComponents.COMPUTER_ID.get(), NonNegativeId.of(computerId))
        stack.set(ModRegistry.DataComponents.STORAGE_CAPACITY.get(), storageCapacity.takeIf { it > 0 }?.let(::StorageCapacity))
        stack.set(ModRegistry.DataComponents.TERMINAL_SIZE.get(), terminalSize)
        stack.set(CCDataComponents.DESK_ID.get(), deskId.toString())
        stack.set(CCDataComponents.COMPUTER_POWERED.get(), powered)
        stack.set(CCDataComponents.RADAR_DESTINATION_DESK_ID.get(), radarDestinationDeskId)
        wireBank.encodedDefinitions().takeIf(String::isNotEmpty)?.let {
            stack.set(CCDataComponents.WIRE_CHANNELS.get(), it)
        }
        stack.set(DataComponents.CUSTOM_NAME, label?.let { Component.literal(it) })
    }

    private fun closeComputer() {
        getServerComputer()?.close()
        instanceId = null
    }

    private fun defaultTerminalSize(): TerminalSize =
        TerminalSize(
            dan200.computercraft.shared.config.ConfigSpec.computerTermWidth.get(),
            dan200.computercraft.shared.config.ConfigSpec.computerTermHeight.get()
        )

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
                arrayOf(snapshot.state.name.lowercase(), snapshot.members.size, snapshot.revision)
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
                                arrayOf(
                                    member.id,
                                    member.index,
                                    socket,
                                    de.teutonstudio.ccaeroworks.compat.aeroworks.DeskSockets.name(socket),
                                    next?.moduleId ?: previous?.moduleId.orEmpty(),
                                    newValue,
                                    channel
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
    }
}