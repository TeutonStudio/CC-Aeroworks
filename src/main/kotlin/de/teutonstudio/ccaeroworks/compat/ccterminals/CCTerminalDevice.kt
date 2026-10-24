package de.teutonstudio.ccaeroworks.compat.ccterminals

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import com.sashafiesta.ccterminals.CCTerminals
import com.sashafiesta.ccterminals.TerminalBlockEntity
import com.sashafiesta.ccterminals.TerminalContainerData
import com.sashafiesta.ccterminals.TerminalMenu
import com.sashafiesta.ccterminals.network.TerminalStatePacket
import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.core.apis.TermMethods
import dan200.computercraft.shared.ModRegistry
import dan200.computercraft.shared.computer.terminal.TerminalState
import dan200.computercraft.shared.platform.PlatformHelper
import de.teutonstudio.ccaeroworks.computer.ConsoleTerminal
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntityType

/** Bridges a CC: Terminals block entity into an Aeroworks slot without placing a second block. */
class CCTerminalDevice(
    override val host: ConsoleBlockEntity,
    private val installed: ItemStack,
    override val isAdvanced: Boolean
) : ConsoleTerminal {
    private val id = ResourceLocation.fromNamespaceAndPath(
        "ccterminals", if (isAdvanced) "terminal_advanced" else "terminal_normal"
    )
    @Suppress("UNCHECKED_CAST")
    private val terminal = TerminalBlockEntity(
        BuiltInRegistries.BLOCK_ENTITY_TYPE.get(id) as BlockEntityType<TerminalBlockEntity>,
        host.blockPos,
        BuiltInRegistries.BLOCK.get(id).defaultBlockState(),
        isAdvanced
    )
    private var lastState = CompoundTag()

    init {
        installed.get(ModRegistry.DataComponents.TERMINAL_SIZE.get())?.let {
            terminal.terminal.resize(it.width(), it.height())
        }
    }

    override fun item(): ItemStack = installed.copyWithCount(1)
    override fun peripheral(): IPeripheral = terminal.peripheral()
    override fun termMethods(): TermMethods = terminal.peripheral()

    override fun tick() {
        val level = host.level as? ServerLevel ?: return
        if (terminal.level !== level) terminal.setLevel(level)
        val current = terminal.terminal.writeToNBT(CompoundTag())
        if (current == lastState) return
        lastState = current.copy()
        host.setChanged()
        val state = TerminalState.create(terminal.terminal) ?: return
        level.server.playerList.players.forEach { player ->
            val menu = player.containerMenu as? TerminalMenu ?: return@forEach
            if (menu.blockEntity === terminal) {
                CCTerminals.sendToPlayer.accept(player, TerminalStatePacket(menu.containerId, state))
            }
        }
    }

    override fun open(player: ServerPlayer, direct: Boolean): Boolean {
        val level = host.level as? ServerLevel ?: return false
        if (terminal.level !== level) terminal.setLevel(level)
        if (!direct) {
            val snapshot = ConsoleMultiblockManager.resolve(level, host.blockPos)
            if (snapshot.state != ConsoleNetworkState.TERMINAL || snapshot.terminal !== this) return false
        }
        val maximum = player.blockInteractionRange() + 1.0
        val snapshot = ConsoleMultiblockManager.resolve(level, host.blockPos)
        if (player.distanceToSqr(host.blockPos.center) > maximum * maximum &&
            snapshot.members.none { player.distanceToSqr(it.pos.center) <= maximum * maximum }) return false
        PlatformHelper.get().openMenu(
            player,
            terminal.displayName,
            terminal,
            TerminalContainerData(isAdvanced, TerminalState.create(terminal.terminal))
        )
        return true
    }

    override fun save(tag: CompoundTag) {
        tag.putInt("TermWidth", terminal.terminal.width)
        tag.putInt("TermHeight", terminal.terminal.height)
        terminal.terminal.writeToNBT(tag)
    }

    override fun load(tag: CompoundTag) {
        if (tag.contains("TermWidth") && tag.contains("TermHeight")) {
            terminal.terminal.resize(tag.getInt("TermWidth"), tag.getInt("TermHeight"))
        }
        terminal.terminal.readFromNBT(tag)
        lastState = terminal.terminal.writeToNBT(CompoundTag())
    }

    override fun invalidate() {
        terminal.setRemoved()
    }
}
