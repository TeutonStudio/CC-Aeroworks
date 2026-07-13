package de.teutonstudio.ccaeroworks.client

import com.mred231.aeroworks.content.controls.ModulePartials
import de.teutonstudio.ccaeroworks.client.display.DeskDisplayModels
import de.teutonstudio.ccaeroworks.input.CombinedLeverController
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent

object CCAeroworksClient {
    fun register(modBus: IEventBus) {
        DeskDisplayModels.init()
        CCKeyMappings.register(modBus)
        modBus.addListener(::clientSetup)
        NeoForge.EVENT_BUS.register(CombinedLeverController)
    }

    private fun clientSetup(event: FMLClientSetupEvent) {
        event.enqueueWork { ModulePartials.init() }
    }
}
