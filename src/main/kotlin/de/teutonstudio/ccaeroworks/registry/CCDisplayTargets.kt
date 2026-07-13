package de.teutonstudio.ccaeroworks.registry

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import com.simibubi.create.api.behaviour.display.DisplayTarget
import com.simibubi.create.api.registry.CreateRegistries
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.display.DeskDisplayTarget
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.entity.BlockEntityType
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object CCDisplayTargets {
    private val TARGETS: DeferredRegister<DisplayTarget> =
        DeferredRegister.create(CreateRegistries.DISPLAY_TARGET, CCAeroworks.MOD_ID)

    @JvmField
    val CONTROL_DESK: DeferredHolder<DisplayTarget, DeskDisplayTarget> = TARGETS.register(
        CCAeroworks.DISPLAY_TARGET_ID,
        Supplier { DeskDisplayTarget() }
    )

    fun register(bus: IEventBus) {
        TARGETS.register(bus)
        bus.addListener(::commonSetup)
    }

    @Suppress("UNCHECKED_CAST")
    private fun commonSetup(event: FMLCommonSetupEvent) {
        event.enqueueWork {
            val id = ResourceLocation.fromNamespaceAndPath("aeroworks", "console")
            val type = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(id) as BlockEntityType<ConsoleBlockEntity>
            DisplayTarget.BY_BLOCK_ENTITY.register(type, CONTROL_DESK.get())
        }
    }
}
