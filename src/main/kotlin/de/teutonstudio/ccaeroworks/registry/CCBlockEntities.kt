package de.teutonstudio.ccaeroworks.registry

import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyBlockEntity
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.block.entity.BlockEntityType
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object CCBlockEntities {
    private val BLOCK_ENTITIES: DeferredRegister<BlockEntityType<*>> =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CCAeroworks.MOD_ID)

    @JvmField
    val COMPUTER_CONTROL_DESK:
        DeferredHolder<BlockEntityType<*>, BlockEntityType<ComputerControlDeskBlockEntity>> =
        BLOCK_ENTITIES.register(
            "computer_control_desk",
            Supplier<BlockEntityType<ComputerControlDeskBlockEntity>> {
                BlockEntityType.Builder.of(
                    { pos, state ->
                        ComputerControlDeskBlockEntity(COMPUTER_CONTROL_DESK.get(), pos, state)
                    },
                    CCBlocks.COMPUTER_CONTROL_DESK.get(),
                    CCBlocks.ADVANCED_COMPUTER_CONTROL_DESK.get(),
                    CCBlocks.COMPUTER_COPYCAT_CONTROL_DESK.get(),
                    CCBlocks.ADVANCED_COMPUTER_COPYCAT_CONTROL_DESK.get(),
                    CCBlocks.COMPUTER_CONTROL_STAND.get(),
                    CCBlocks.ADVANCED_COMPUTER_CONTROL_STAND.get(),
                    CCBlocks.COMPUTER_COPYCAT_CONTROL_STAND.get(),
                    CCBlocks.ADVANCED_COMPUTER_COPYCAT_CONTROL_STAND.get()
                ).build(null)
            }
        )

    @JvmField
    val CONSOLE_ASSEMBLY_SEGMENT:
        DeferredHolder<BlockEntityType<*>, BlockEntityType<ConsoleAssemblyBlockEntity>> =
        BLOCK_ENTITIES.register(
            "console_assembly_segment",
            Supplier<BlockEntityType<ConsoleAssemblyBlockEntity>> {
                val controls = BuiltInRegistries.BLOCK.get(
                    ResourceLocation.fromNamespaceAndPath("create", "controls")
                )
                BlockEntityType.Builder.of(
                    { pos, state -> ConsoleAssemblyBlockEntity(pos, state) },
                    controls
                ).build(null)
            }
        )

    fun register(bus: IEventBus) = BLOCK_ENTITIES.register(bus)
}
