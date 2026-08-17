package de.teutonstudio.ccaeroworks.radarcompat

import de.teutonstudio.ccaeroworks.client.SourceSelectorOverlayExtensions
import de.teutonstudio.ccaeroworks.client.creative.AeroworksCreativeExtensions
import de.teutonstudio.ccaeroworks.client.display.DisplayRenderExtensions
import de.teutonstudio.ccaeroworks.radarcompat.client.RadarSourceSelectorOverlayOwner
import de.teutonstudio.ccaeroworks.radarcompat.client.ponder.RadarCompatPonderPlugin
import de.teutonstudio.ccaeroworks.radarcompat.client.render.RadarOverlayRenderer
import de.teutonstudio.ccaeroworks.radarcompat.registry.RadarItems
import net.createmod.ponder.foundation.PonderIndex
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.common.NeoForge

object RadarCompatClient {
    fun register(modBus: IEventBus) {
        DisplayRenderExtensions.registerTracker(RadarOverlayRenderer::track)
        SourceSelectorOverlayExtensions.register { screen, mouseX, mouseY ->
  (screen as? RadarSourceSelectorOverlayOwner)
      ?.ccaeroworks_isRadarSourceSelectorPopupHovered(mouseX, mouseY) == true
        }
        AeroworksCreativeExtensions.registerAeroworksItem { RadarItems.SMALL_RADAR_DISPLAY.get().defaultInstance }
        AeroworksCreativeExtensions.registerAeroworksItem { RadarItems.LARGE_RADAR_DISPLAY.get().defaultInstance }
        NeoForge.EVENT_BUS.addListener(RadarOverlayRenderer::renderLevel)
        modBus.addListener(::clientSetup)
    }

    private fun clientSetup(event: FMLClientSetupEvent) {
        event.enqueueWork { PonderIndex.addPlugin(RadarCompatPonderPlugin()) }
    }
}
