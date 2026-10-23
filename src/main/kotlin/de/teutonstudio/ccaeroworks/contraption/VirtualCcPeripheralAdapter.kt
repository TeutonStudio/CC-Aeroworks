package de.teutonstudio.ccaeroworks.contraption

import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import dan200.computercraft.core.computer.ComputerSide
import dan200.computercraft.shared.peripheral.diskdrive.DiskDriveBlockEntity
import dan200.computercraft.shared.peripheral.modem.wired.CableBlockEntity
import dan200.computercraft.shared.peripheral.modem.wired.WiredModemFullBlockEntity
import dan200.computercraft.shared.peripheral.modem.wired.WiredModemElement
import dan200.computercraft.shared.peripheral.modem.wireless.WirelessModemBlockEntity
import dan200.computercraft.shared.peripheral.monitor.MonitorBlockEntity
import dan200.computercraft.shared.peripheral.printer.PrinterBlockEntity
import dan200.computercraft.shared.peripheral.redstone.RedstoneRelayBlockEntity
import dan200.computercraft.shared.peripheral.speaker.SpeakerBlockEntity
import de.teutonstudio.ccaeroworks.api.mobile.MobilePeripheralAdapter
import de.teutonstudio.ccaeroworks.api.mobile.MobilePeripheralContext
import de.teutonstudio.ccaeroworks.api.mobile.MobilePeripheralInstance
import de.teutonstudio.ccaeroworks.mixin.BlockEntityPositionAccessor
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/** Explicit CC base-device adapter. It never attempts to execute unknown block entities. */
internal class VirtualCcPeripheralAdapter(
    private val registryId: ResourceLocation,
    private val blocks: Set<ResourceLocation>
) : MobilePeripheralAdapter {
    override fun id(): ResourceLocation = registryId
    override fun matches(state: BlockState, blockEntityData: CompoundTag): Boolean =
        BuiltInRegistries.BLOCK.getKey(state.block) in blocks

    override fun create(context: MobilePeripheralContext): MobilePeripheralInstance =
        VirtualCcPeripheralInstance(context)
}

internal class VirtualCcPeripheralInstance(
    private val initialContext: MobilePeripheralContext
) : MobilePeripheralInstance {
    internal val blockEntity: BlockEntity = BlockEntity.loadStatic(
        initialContext.worldPosition(),
        initialContext.blockState(),
        initialContext.blockEntityData().copy(),
        initialContext.level().registryAccess()
    ) ?: error("CC block entity NBT is missing or invalid at ${initialContext.localPosition()}")

    private val target: IPeripheral = when (val be = blockEntity) {
        is WirelessModemBlockEntity -> be.getPeripheral(Direction.NORTH) ?: error("Wireless modem has no peripheral")
        is CableBlockEntity -> Direction.values().firstNotNullOfOrNull(be::getPeripheral) ?: CarrierPeripheral
        is WiredModemFullBlockEntity -> be.getPeripheral(Direction.NORTH) ?: error("Wired modem has no peripheral")
        is SpeakerBlockEntity -> be.peripheral()
        is MonitorBlockEntity -> be.peripheral()
        is DiskDriveBlockEntity -> be.peripheral()
        is PrinterBlockEntity -> be.peripheral()
        is RedstoneRelayBlockEntity -> MobileRedstoneRelayPeripheral
        else -> error("Unsupported CC block entity ${be.javaClass.name}")
    }

    init {
        blockEntity.setLevel(initialContext.level())
        blockEntity.clearRemoved()
    }

    override fun peripheral(): IPeripheral = target

    override fun tick(context: MobilePeripheralContext) {
        (blockEntity as BlockEntityPositionAccessor).ccaeroworks_setWorldPosition(context.worldPosition())
        // Wired networks are connected from local contraption coordinates, monitors are synced by
        // their adapter state, and relay outputs require an actor port. Never let those query the world.
        val blockId = BuiltInRegistries.BLOCK.getKey(context.blockState().block)
        if (blockId.namespace == "computercraft" && blockId.path in NO_WORLD_TICK) return
        val block = context.blockState().block as? EntityBlock ?: return
        @Suppress("UNCHECKED_CAST")
        val ticker = block.getTicker(
            context.level(), context.blockState(), blockEntity.type as net.minecraft.world.level.block.entity.BlockEntityType<BlockEntity>
        ) ?: return
        ticker.tick(context.level(), context.worldPosition(), context.blockState(), blockEntity)
    }

    override fun save(context: MobilePeripheralContext) {
        val saved = blockEntity.saveWithFullMetadata(context.level().registryAccess())
        context.setBlockEntityData(saved)
    }

    override fun clientSync(context: MobilePeripheralContext): CompoundTag =
        blockEntity.getUpdateTag(context.level().registryAccess())

    override fun releaseActorOutputs() {
        // Relay world outputs are deliberately never applied without a registered actor port.
    }

    override fun close() {
        blockEntity.setRemoved()
    }

    fun wiredNode(): dan200.computercraft.api.network.wired.WiredNode? = when (val be = blockEntity) {
        is CableBlockEntity -> Direction.values().firstNotNullOfOrNull(be::getWiredElement)
            ?.let { it as? WiredModemElement }
            ?.node
        is WiredModemFullBlockEntity -> be.element.node
        else -> null
    }

    private object CarrierPeripheral : IPeripheral {
        override fun getType(): String = "wired_network"
        override fun equals(other: IPeripheral?): Boolean = other === this
    }

    /** A moving relay has no implicit world neighbours. Outputs stay released until a future
     * opt-in actor-port adapter maps them to actual contraption actors. */
    private object MobileRedstoneRelayPeripheral : IPeripheral {
        override fun getType(): String = "redstone_relay"
        override fun equals(other: IPeripheral?): Boolean = other === this

        @LuaFunction fun getOutput(side: ComputerSide): Boolean = false
        @LuaFunction fun getInput(side: ComputerSide): Boolean = false
        @LuaFunction fun getAnalogOutput(side: ComputerSide): Int = 0
        @LuaFunction fun getAnalogInput(side: ComputerSide): Int = 0
        @LuaFunction fun getBundledOutput(side: ComputerSide): Int = 0
        @LuaFunction fun getBundledInput(side: ComputerSide): Int = 0
        @LuaFunction fun testBundledInput(side: ComputerSide, mask: Int): Boolean = false

        @LuaFunction fun setOutput(side: ComputerSide, value: Boolean): Nothing = unavailable()
        @LuaFunction fun setAnalogOutput(side: ComputerSide, value: Int): Nothing = unavailable()
        @LuaFunction fun setBundledOutput(side: ComputerSide, value: Int): Nothing = unavailable()

        private fun unavailable(): Nothing =
            throw LuaException("Mobile redstone relay output is unavailable without an actor-port adapter")
    }

    private companion object {
        val NO_WORLD_TICK = setOf(
            "cable", "wired_modem_full", "monitor_normal", "monitor_advanced", "redstone_relay"
        )
    }
}
