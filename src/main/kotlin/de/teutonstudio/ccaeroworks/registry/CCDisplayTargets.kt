package de.teutonstudio.ccaeroworks.registry

import com.simibubi.create.api.behaviour.display.DisplayTarget
import com.simibubi.create.api.registry.CreateRegistries
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
import de.teutonstudio.ccaeroworks.display.DeskDisplayTarget
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

    private fun commonSetup(event: FMLCommonSetupEvent) {
        event.enqueueWork {
            DisplayTarget.BY_BLOCK_ENTITY.register(
                AeroworksTypes.consoleBlockEntityType(),
                CONTROL_DESK.get()
            )
            DisplayTarget.BY_BLOCK_ENTITY.register(
                CCBlockEntities.COMPUTER_CONTROL_DESK.get(),
                CONTROL_DESK.get()
            )
        }
    }
}
