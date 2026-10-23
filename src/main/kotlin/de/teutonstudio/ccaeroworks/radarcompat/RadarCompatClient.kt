package de.teutonstudio.ccaeroworks.radarcompat

import de.teutonstudio.ccaeroworks.client.display.DisplayRenderExtensions
import de.teutonstudio.ccaeroworks.radarcompat.client.registerRadarDisplaySourceOptions
import de.teutonstudio.ccaeroworks.radarcompat.client.ponder.RadarCompatPonderPlugin
import de.teutonstudio.ccaeroworks.radarcompat.client.render.RadarOverlayRenderer
import net.createmod.ponder.foundation.PonderIndex
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.common.NeoForge

object RadarCompatClient {
    fun register(modBus: IEventBus) {
        DisplayRenderExtensions.registerTracker(RadarOverlayRenderer::track)
        registerRadarDisplaySourceOptions()
        NeoForge.EVENT_BUS.addListener(RadarOverlayRenderer::renderLevel)
        modBus.addListener(::clientSetup)
    }

    private fun clientSetup(event: FMLClientSetupEvent) {
        event.enqueueWork { PonderIndex.addPlugin(RadarCompatPonderPlugin()) }
    }
}
