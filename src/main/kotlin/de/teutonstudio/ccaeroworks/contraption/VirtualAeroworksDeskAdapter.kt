package de.teutonstudio.ccaeroworks.contraption

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.api.mobile.MobilePeripheralAdapter
import de.teutonstudio.ccaeroworks.api.mobile.MobilePeripheralContext
import de.teutonstudio.ccaeroworks.api.mobile.MobilePeripheralInstance
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
import de.teutonstudio.ccaeroworks.compat.computercraft.ControlDeskPeripheralFactory
import de.teutonstudio.ccaeroworks.mixin.BlockEntityPositionAccessor
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/** NBT-backed desk facade for modules and displays without registering world transmitters. */
internal object VirtualAeroworksDeskAdapter : MobilePeripheralAdapter {
    override fun id() = CCAeroworks.id("aeroworks_control_desk")

    override fun matches(state: BlockState, blockEntityData: CompoundTag): Boolean =
        AeroworksTypes.isControlDesk(state.block)

    override fun create(context: MobilePeripheralContext): MobilePeripheralInstance =
        VirtualAeroworksDeskInstance(context)
}

internal class VirtualAeroworksDeskInstance(
    context: MobilePeripheralContext
) : MobilePeripheralInstance {
    private val desk = BlockEntity.loadStatic(
        context.worldPosition(),
        context.blockState(),
        context.blockEntityData().copy(),
        context.level().registryAccess()
    ) as? ConsoleBlockEntity ?: error("Aeroworks desk NBT is missing or invalid at ${context.localPosition()}")
    private val peripheral = ControlDeskPeripheralFactory.create(desk)

    init {
        desk.setLevel(context.level())
        desk.clearRemoved()
    }

    override fun peripheral() = peripheral

    override fun tick(context: MobilePeripheralContext) {
        (desk as BlockEntityPositionAccessor).ccaeroworks_setWorldPosition(context.worldPosition())
    }

    override fun save(context: MobilePeripheralContext) {
        val installedComputer = context.blockEntityData()
            .getCompound(MobileConsoleComputer.INSTALLED_TAG)
            .copy()
        val saved = desk.saveWithFullMetadata(context.level().registryAccess())
        if (!installedComputer.isEmpty) saved.put(MobileConsoleComputer.INSTALLED_TAG, installedComputer)
        context.setBlockEntityData(saved)
    }

    override fun clientSync(context: MobilePeripheralContext): CompoundTag =
        desk.getUpdateTag(context.level().registryAccess())

    override fun close() {
        desk.setRemoved()
    }
}
