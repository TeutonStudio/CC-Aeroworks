package de.teutonstudio.ccaeroworks.registry

import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.block.entity.BlockEntityType
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister

object CCBlockEntities {
    private val BLOCK_ENTITIES: DeferredRegister<BlockEntityType<*>> =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CCAeroworks.MOD_ID)

    @JvmField
    val COMPUTER_CONTROL_DESK:
        DeferredHolder<BlockEntityType<*>, BlockEntityType<ComputerControlDeskBlockEntity>> =
        BLOCK_ENTITIES.register("computer_control_desk") {
            BlockEntityType.Builder.of(
                { pos, state ->
                    ComputerControlDeskBlockEntity(COMPUTER_CONTROL_DESK.get(), pos, state)
                },
                CCBlocks.COMPUTER_CONTROL_DESK.get(),
                CCBlocks.ADVANCED_COMPUTER_CONTROL_DESK.get()
            ).build(null)
        }

    fun register(bus: IEventBus) = BLOCK_ENTITIES.register(bus)
}
