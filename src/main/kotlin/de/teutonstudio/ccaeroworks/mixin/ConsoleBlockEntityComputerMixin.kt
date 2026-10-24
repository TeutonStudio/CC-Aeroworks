package de.teutonstudio.ccaeroworks.mixin

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import dan200.computercraft.shared.ModRegistry
import dan200.computercraft.shared.computer.core.ComputerFamily
import de.teutonstudio.ccaeroworks.computer.*
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(value = [ConsoleBlockEntity::class], remap = false)
abstract class ConsoleBlockEntityComputerMixin : ConsoleComputerAccess, ConsoleTerminalAccess {
    @Unique private var ccaeroworks_computerState: ConsoleComputer? = null
    @Unique private var ccaeroworks_terminalState: ConsoleTerminal? = null
    override fun ccaeroworks_computer() = ccaeroworks_computerState
    override fun ccaeroworks_setComputer(computer: ConsoleComputer?) { ccaeroworks_computerState = computer }
    override fun ccaeroworks_terminal() = ccaeroworks_terminalState
    override fun ccaeroworks_setTerminal(terminal: ConsoleTerminal?) { ccaeroworks_terminalState = terminal }

    @Inject(method = ["tick"], at = [At("TAIL")])
    private fun ccaeroworks_tickComputer(ci: CallbackInfo) {
        ccaeroworks_computerState?.tick()
        ccaeroworks_terminalState?.tick()
    }

    @Inject(method = ["invalidate"], at = [At("HEAD")])
    private fun ccaeroworks_closeComputer(ci: CallbackInfo) {
        val host = this as Any as ConsoleBlockEntity
        ccaeroworks_computerState?.invalidate()
        ccaeroworks_terminalState?.let {
            de.teutonstudio.ccaeroworks.compat.computercraft.ControlDeskPeripheralRegistry
                .terminalRemoved(host, it)
            it.invalidate()
        }
    }

    @Inject(method = ["write"], at = [At("TAIL")])
    private fun ccaeroworks_saveComputer(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean, ci: CallbackInfo) {
        val computer = ccaeroworks_computerState
        if (computer == null) tag.remove("CCAeroworksInstalledComputer")
        else tag.put("CCAeroworksInstalledComputer", computer.item().save(registries))
        val terminal = ccaeroworks_terminalState
        if (terminal == null) {
            tag.remove("CCAeroworksInstalledTerminal")
            tag.remove("CCAeroworksTerminalState")
        } else {
            tag.put("CCAeroworksInstalledTerminal", terminal.item().save(registries))
            tag.put("CCAeroworksTerminalState", CompoundTag().also(terminal::save))
        }
    }

    @Inject(method = ["read"], at = [At("TAIL")])
    private fun ccaeroworks_loadComputer(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean, ci: CallbackInfo) {
        val host = this as Any as ConsoleBlockEntity
        var stack = ItemStack.parseOptional(registries, tag.getCompound("CCAeroworksInstalledComputer"))
        val legacy = host.blockState.block as? ComputerControlDeskBlock
        if (stack.isEmpty && legacy != null) {
            stack = ItemStack(if (legacy.family == ComputerFamily.ADVANCED) ModRegistry.Items.COMPUTER_ADVANCED.get() else ModRegistry.Items.COMPUTER_NORMAL.get())
            val computer = ConsoleComputer(host, stack)
            computer.read(tag, registries, clientPacket)
            computer.groups.loadEncodedDefinitions(tag.getString("CCAeroworksChannelGroups"))
            stack = computer.item()
        }
        // Server read is load-time only. Client packets replace the non-running client component.
        ccaeroworks_computerState?.invalidate()
        ccaeroworks_computerState = if (ConsoleComputers.accepts(stack)) ConsoleComputer(host, stack.copyWithCount(1)) else null
        ccaeroworks_terminalState?.let {
            de.teutonstudio.ccaeroworks.compat.computercraft.ControlDeskPeripheralRegistry
                .terminalRemoved(host, it)
            it.invalidate()
        }
        ccaeroworks_terminalState = null
        if (ccaeroworks_computerState == null) {
            val terminalStack = ItemStack.parseOptional(registries, tag.getCompound("CCAeroworksInstalledTerminal"))
            if (ConsoleTerminals.accepts(terminalStack)) {
                ConsoleTerminals.install(
                    host,
                    terminalStack.copyWithCount(1),
                    tag.getCompound("CCAeroworksTerminalState").takeUnless(CompoundTag::isEmpty)
                )
            }
        }
        host.level?.let { ConsoleMultiblockManager.invalidate(it, host.blockPos) }
    }

    @Inject(method = ["onLoad"], at = [At("TAIL")])
    private fun ccaeroworks_migrateComputer(ci: CallbackInfo) {
        ConsoleComputerLifecycle.scheduleMigration(this as Any as ConsoleBlockEntity)
    }
}
