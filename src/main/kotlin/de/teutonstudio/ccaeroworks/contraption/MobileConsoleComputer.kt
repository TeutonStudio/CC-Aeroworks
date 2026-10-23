package de.teutonstudio.ccaeroworks.contraption

import com.simibubi.create.content.contraptions.AbstractContraptionEntity
import dan200.computercraft.api.ComputerCraftAPI
import dan200.computercraft.shared.ModRegistry
import dan200.computercraft.shared.computer.core.ComputerFamily
import dan200.computercraft.shared.computer.core.ServerComputer
import dan200.computercraft.shared.computer.core.ServerContext
import dan200.computercraft.shared.computer.core.TerminalSize
import dan200.computercraft.shared.computer.inventory.ComputerMenuWithoutInventory
import dan200.computercraft.shared.network.container.ComputerContainerData
import dan200.computercraft.shared.platform.PlatformHelper
import dan200.computercraft.shared.util.NonNegativeId
import dan200.computercraft.shared.util.StorageCapacity
import de.teutonstudio.ccaeroworks.api.mobile.MobilePeripheralInstance
import de.teutonstudio.ccaeroworks.computer.MobileComputerComponents
import de.teutonstudio.ccaeroworks.computer.MobileConsoleAccess
import de.teutonstudio.ccaeroworks.registry.CCDataComponents
import java.util.UUID
import net.minecraft.core.BlockPos
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

/** Minimal mobile host for the same CC computer item and save directory used by a stationary desk. */
class MobileConsoleComputer(
    val entity: AbstractContraptionEntity,
    val level: ServerLevel,
    val deskPositions: Set<BlockPos>,
    private val computerDesk: BlockPos,
    private val data: CompoundTag,
    private val instances: List<Pair<BlockPos, MobilePeripheralInstance>>
) : MenuProvider, Nameable, AutoCloseable {
    private var installed = ItemStack.parseOptional(level.registryAccess(), data.getCompound(INSTALLED_TAG))
    private var computerId = NonNegativeId.getId(installed.get(ModRegistry.DataComponents.COMPUTER_ID.get()))
    private var storageCapacity = StorageCapacity.getOrDefault(
        installed.get(ModRegistry.DataComponents.STORAGE_CAPACITY.get()), -1
    )
    private var terminalSize = installed.get(ModRegistry.DataComponents.TERMINAL_SIZE.get())
    private var label = installed.get(DataComponents.CUSTOM_NAME)?.string
    private var powered = installed.get(CCDataComponents.COMPUTER_POWERED.get()) ?: false
    private var startOn = powered
    private var instanceId: UUID? = null
    var closed: Boolean = false
        private set

    val family: ComputerFamily
        get() = if (installed.`is`(ModRegistry.Items.COMPUTER_ADVANCED.get())) {
            ComputerFamily.ADVANCED
        } else {
            ComputerFamily.NORMAL
        }

    fun peripheralInstances(): List<MobilePeripheralInstance> = instances.map(Pair<BlockPos, MobilePeripheralInstance>::second)
    fun localPosition(instance: MobilePeripheralInstance): BlockPos =
        instances.firstOrNull { it.second === instance }?.first ?: computerDesk
    fun worldPosition(local: BlockPos): BlockPos = BlockPos.containing(entity.toGlobalVector(local.center, 1f))

    fun tick() {
        if (closed || entity.isRemoved) return
        val computer = getServerComputer() ?: if (computerId >= 0 || startOn) createServerComputer() else return
        computer.setPosition(level, worldPosition(computerDesk))
        if (startOn || powered) {
            computer.turnOn()
            startOn = false
        }
        computer.keepAlive()
        powered = computer.isOn
        label = computer.label
    }

    fun save() {
        if (closed) return
        getServerComputer()?.let {
            powered = it.isOn
            label = it.label
        }
        installed.set(ModRegistry.DataComponents.COMPUTER_ID.get(), NonNegativeId.of(computerId))
        installed.set(ModRegistry.DataComponents.STORAGE_CAPACITY.get(), storageCapacity.takeIf { it > 0 }?.let(::StorageCapacity))
        installed.set(ModRegistry.DataComponents.TERMINAL_SIZE.get(), terminalSize)
        installed.set(CCDataComponents.COMPUTER_POWERED.get(), powered)
        installed.set(DataComponents.CUSTOM_NAME, label?.let(Component::literal))
        data.put(INSTALLED_TAG, installed.save(level.registryAccess()))
    }

    fun openTerminal(player: Player): Boolean {
        if (closed || player.level() !== level || !family.checkUsable(player)) return false
        if (deskPositions.none { entity.canInteractWithBlock(player, it, 2.0) }) return false
        val computer = createServerComputer()
        computer.turnOn()
        powered = true
        PlatformHelper.get().openMenu(player, displayName, this, ComputerContainerData(computer, installed.copy()))
        return true
    }

    fun createServerComputer(): ServerComputer {
        val context = ServerContext.get(level.server)
        instanceId?.let { context.registry().get(it) }?.let { return it }
        if (computerId < 0) computerId = ComputerCraftAPI.createUniqueNumberedSaveDir(level.server, "computer")
        val properties = ServerComputer.properties(computerId, family)
            .label(label)
            .terminalSize(terminalSize ?: TerminalSize(
                dan200.computercraft.shared.config.ConfigSpec.computerTermWidth.get(),
                dan200.computercraft.shared.config.ConfigSpec.computerTermHeight.get()
            ))
            .storageCapacity(storageCapacity)
            .addComponent(MobileComputerComponents.CONSOLE, MobileConsoleAccess(this))
        return ServerComputer(level, worldPosition(computerDesk), properties).also { instanceId = it.register() }
    }

    private fun getServerComputer(): ServerComputer? =
        instanceId?.let { ServerContext.get(level.server).registry().get(it) }

    override fun createMenu(id: Int, inventory: Inventory, player: Player): AbstractContainerMenu =
        ComputerMenuWithoutInventory(
            ModRegistry.Menus.COMPUTER.get(), id, inventory,
            { candidate -> !closed && candidate.level() === level && deskPositions.any { entity.canInteractWithBlock(candidate, it, 2.0) } },
            createServerComputer()
        )

    override fun getName(): Component = label?.let(Component::literal) ?: installed.hoverName
    override fun getDisplayName(): Component = name
    override fun getCustomName(): Component? = label?.let(Component::literal)
    override fun hasCustomName(): Boolean = !label.isNullOrEmpty()

    override fun close() {
        if (closed) return
        save()
        closed = true
        getServerComputer()?.close()
        instanceId = null
    }

    companion object {
        const val INSTALLED_TAG = "CCAeroworksInstalledComputer"
    }
}
