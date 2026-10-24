package de.teutonstudio.ccaeroworks.computer

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.core.apis.TermMethods
import de.teutonstudio.ccaeroworks.compat.ccterminals.CCTerminalDevice
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

/** Optional, non-programmable operator terminal installed in a desk or stand slot. */
interface ConsoleTerminal {
    val host: ConsoleBlockEntity
    val isAdvanced: Boolean
    val blockPos get() = host.blockPos
    fun item(): ItemStack
    fun peripheral(): IPeripheral
    fun termMethods(): TermMethods
    fun tick()
    fun open(player: ServerPlayer, direct: Boolean = false): Boolean
    fun save(tag: CompoundTag)
    fun load(tag: CompoundTag)
    fun invalidate()
}

interface ConsoleTerminalAccess {
    fun ccaeroworks_terminal(): ConsoleTerminal?
    fun ccaeroworks_setTerminal(terminal: ConsoleTerminal?)
}

object ConsoleTerminals {
    private val NORMAL = ResourceLocation.fromNamespaceAndPath("ccterminals", "terminal_normal")
    private val ADVANCED = ResourceLocation.fromNamespaceAndPath("ccterminals", "terminal_advanced")

    @JvmStatic
    fun get(value: Any?): ConsoleTerminal? = (value as? ConsoleTerminalAccess)?.ccaeroworks_terminal()

    /** Resolve the row's shared terminal from any desk or stand member. */
    fun resolve(host: ConsoleBlockEntity): ConsoleTerminal? {
        get(host)?.let { return it }
        val level = host.level ?: return null
        val snapshot = ConsoleMultiblockManager.resolve(level, host.blockPos)
        return snapshot.terminal.takeIf { snapshot.state == ConsoleNetworkState.TERMINAL }
    }

    fun accepts(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val id = BuiltInRegistries.ITEM.getKey(stack.item)
        return id == NORMAL || id == ADVANCED
    }

    fun install(host: ConsoleBlockEntity, stack: ItemStack, state: CompoundTag? = null) {
        require(accepts(stack) && stack.count == 1)
        check(ConsoleDevices.get(host) == null)
        val terminal = CCTerminalDevice(
            host,
            stack.copyWithCount(1),
            BuiltInRegistries.ITEM.getKey(stack.item) == ADVANCED
        )
        state?.let(terminal::load)
        (host as ConsoleTerminalAccess).ccaeroworks_setTerminal(terminal)
        ConsoleComputers.changed(host)
        de.teutonstudio.ccaeroworks.compat.computercraft.ControlDeskPeripheralRegistry.terminalInstalled(host, terminal)
    }

    fun remove(host: ConsoleBlockEntity): ItemStack {
        val terminal = get(host) ?: return ItemStack.EMPTY
        val result = terminal.item()
        (host as ConsoleTerminalAccess).ccaeroworks_setTerminal(null)
        ConsoleComputers.changed(host)
        de.teutonstudio.ccaeroworks.compat.computercraft.ControlDeskPeripheralRegistry.terminalRemoved(host, terminal)
        terminal.invalidate()
        return result
    }
}

object ConsoleDevices {
    fun get(host: ConsoleBlockEntity): Any? = ConsoleComputers.get(host) ?: ConsoleTerminals.get(host)
    fun accepts(stack: ItemStack): Boolean = ConsoleComputers.accepts(stack) || ConsoleTerminals.accepts(stack)
    fun item(host: ConsoleBlockEntity): ItemStack =
        ConsoleComputers.get(host)?.item() ?: ConsoleTerminals.get(host)?.item() ?: ItemStack.EMPTY

    fun install(host: ConsoleBlockEntity, stack: ItemStack) {
        when {
            ConsoleComputers.accepts(stack) -> ConsoleComputers.install(host, stack)
            ConsoleTerminals.accepts(stack) -> ConsoleTerminals.install(host, stack)
            else -> throw IllegalArgumentException("Unsupported console slot device: ${stack.item}")
        }
    }
    fun remove(host: ConsoleBlockEntity): ItemStack = when {
        ConsoleComputers.get(host) != null -> ConsoleComputers.remove(host)
        ConsoleTerminals.get(host) != null -> ConsoleTerminals.remove(host)
        else -> ItemStack.EMPTY
    }
}
