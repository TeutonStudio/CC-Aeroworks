package de.teutonstudio.ccaeroworks.computer

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import dan200.computercraft.shared.ModRegistry
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import net.minecraft.world.item.ItemStack

/** Implemented on every Aeroworks console, including compatibility-only legacy consoles. */
interface ConsoleComputerAccess {
    fun ccaeroworks_computer(): ConsoleComputer?
    fun ccaeroworks_setComputer(computer: ConsoleComputer?)
}

object ConsoleComputers {
    @JvmStatic
    fun get(value: Any?): ConsoleComputer? = (value as? ConsoleComputerAccess)?.ccaeroworks_computer()

    fun accepts(stack: ItemStack): Boolean = stack.`is`(ModRegistry.Items.COMPUTER_NORMAL.get()) ||
        stack.`is`(ModRegistry.Items.COMPUTER_ADVANCED.get())

    fun legacyItem(source: ItemStack, family: dan200.computercraft.shared.computer.core.ComputerFamily): ItemStack {
        val result = ItemStack(if (family == dan200.computercraft.shared.computer.core.ComputerFamily.ADVANCED)
            ModRegistry.Items.COMPUTER_ADVANCED.get() else ModRegistry.Items.COMPUTER_NORMAL.get())
        copyComponent(source, result, ModRegistry.DataComponents.COMPUTER_ID.get())
        copyComponent(source, result, ModRegistry.DataComponents.STORAGE_CAPACITY.get())
        copyComponent(source, result, ModRegistry.DataComponents.TERMINAL_SIZE.get())
        copyComponent(source, result, net.minecraft.core.component.DataComponents.CUSTOM_NAME)
        copyComponent(source, result, de.teutonstudio.ccaeroworks.registry.CCDataComponents.COMPUTER_POWERED.get())
        copyComponent(source, result, de.teutonstudio.ccaeroworks.registry.CCDataComponents.RADAR_DESTINATION_DESK_ID.get())
        copyComponent(source, result, de.teutonstudio.ccaeroworks.registry.CCDataComponents.WIRE_CHANNELS.get())
        copyComponent(source, result, de.teutonstudio.ccaeroworks.registry.CCDataComponents.CHANNEL_GROUPS.get())
        return result
    }

    private fun <T> copyComponent(source: ItemStack, target: ItemStack, type: net.minecraft.core.component.DataComponentType<T>) {
        source.get(type)?.let { target.set(type, it) }
    }

    fun install(host: ConsoleBlockEntity, stack: ItemStack) {
        require(accepts(stack) && stack.count == 1)
        check(get(host) == null)
        (host as ConsoleComputerAccess).ccaeroworks_setComputer(ConsoleComputer(host, stack.copy()))
        changed(host)
    }

    fun remove(host: ConsoleBlockEntity): ItemStack {
        val computer = get(host) ?: return ItemStack.EMPTY
        // Release outputs before serializing, but retain the latest CC id/label/power state.
        val stack = computer.item()
        computer.invalidate()
        (host as ConsoleComputerAccess).ccaeroworks_setComputer(null)
        changed(host)
        return stack
    }

    fun changed(host: ConsoleBlockEntity) {
        host.setChanged()
        host.level?.let { ConsoleMultiblockManager.invalidate(it, host.blockPos) }
        host.sendData()
        (host.level as? net.minecraft.server.level.ServerLevel)?.let {
            it.updateNeighborsAt(host.blockPos, host.blockState.block)
            de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockSkinUpdater.scheduleRefresh(it, host.blockPos)
        }
    }
}
