package de.teutonstudio.ccaeroworks.client

import com.mred231.aeroworks.content.controls.ModulePartials
import com.mred231.aeroworks.foundation.input.InputSource
import de.teutonstudio.ccaeroworks.client.display.DeskDisplayModels
import de.teutonstudio.ccaeroworks.client.creative.AeroworksCreativeSections
import de.teutonstudio.ccaeroworks.input.CombinedLeverController
import de.teutonstudio.ccaeroworks.input.CombinedInputSource
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent

object CCAeroworksClient {
    fun register(modBus: IEventBus) {
        DeskDisplayModels.init()
        modBus.addListener(::clientSetup)
        NeoForge.EVENT_BUS.register(CombinedLeverController)
        NeoForge.EVENT_BUS.register(AeroworksCreativeSections)
        NeoForge.EVENT_BUS.register(GuideBookClientHandler)
    }

    private fun clientSetup(event: FMLClientSetupEvent) {
        event.enqueueWork {
            ModulePartials.init()
            InputSource.displayName(CombinedInputSource.ID)
        }
    }
}
