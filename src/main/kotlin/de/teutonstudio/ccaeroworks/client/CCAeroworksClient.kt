package de.teutonstudio.ccaeroworks.client

import com.mred231.aeroworks.content.controls.ConsoleVisual
import com.mred231.aeroworks.content.controls.ModulePartials
import com.mred231.aeroworks.foundation.input.InputSource
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.client.creative.AeroworksCreativeSections
import de.teutonstudio.ccaeroworks.client.display.DeskDisplayModels
import de.teutonstudio.ccaeroworks.client.display.RadarOverlayRenderer
import de.teutonstudio.ccaeroworks.client.ponder.CCAeroworksPonderPlugin
import de.teutonstudio.ccaeroworks.compat.createradar.RadarTrace
import de.teutonstudio.ccaeroworks.input.CombinedInputSource
import de.teutonstudio.ccaeroworks.input.CombinedLeverController
import de.teutonstudio.ccaeroworks.registry.CCBlockEntities
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer
import net.createmod.ponder.foundation.PonderIndex
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModList
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.client.event.ModelEvent
import net.neoforged.neoforge.client.gui.ConfigurationScreen
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
import net.neoforged.neoforge.common.NeoForge

object CCAeroworksClient {
    fun register(modBus: IEventBus) {
        ModList.get().getModContainerById(CCAeroworks.MOD_ID).ifPresent { container ->
            container.registerExtensionPoint(
                IConfigScreenFactory::class.java,
                IConfigScreenFactory { _, parent -> ConfigurationScreen(container, parent) }
            )
        }
        RadarTrace.event(
            "C00_CLIENT_REGISTER",
            null,
            null,
            "CCAeroworksClient.register entered; registering RadarOverlayRenderer::renderLevel on NeoForge.EVENT_BUS"
        )
        DeskDisplayModels.init()
        modBus.addListener(::clientSetup)
        modBus.addListener(::registerRenderers)
        modBus.addListener<ModelEvent.RegisterAdditional>(ConsoleMultiblockModels::registerAdditional)
        modBus.addListener<ModelEvent.ModifyBakingResult>(::modifyBakingResult)
        NeoForge.EVENT_BUS.addListener(RadarOverlayRenderer::renderLevel)
        RadarTrace.event(
            "C00_OVERLAY_LISTENER_REGISTERED",
            null,
            null,
            "NeoForge.EVENT_BUS.addListener(RadarOverlayRenderer::renderLevel) completed"
        )
        NeoForge.EVENT_BUS.register(CombinedLeverController)
        NeoForge.EVENT_BUS.register(AeroworksCreativeSections)
        NeoForge.EVENT_BUS.register(GuideBookClientHandler)
        NeoForge.EVENT_BUS.register(DisplayTooltipHandler)
    }

    private fun clientSetup(event: FMLClientSetupEvent) {
        RadarTrace.event("C01_CLIENT_SETUP_EVENT", null, null, "FMLClientSetupEvent received; enqueueing client initialization")
        event.enqueueWork {
            RadarTrace.event("C02_CLIENT_SETUP_WORK", null, null, "client setup work executing")
            ModulePartials.init()
            InputSource.displayName(CombinedInputSource.ID)
            PonderIndex.addPlugin(CCAeroworksPonderPlugin())

            // Aeroworks registers ConsoleVisual for its own block entity type. Normal and
            // advanced ComputerControlDesk share CC-Aeroworks' custom type, so register the
            // same native visual explicitly. ConsoleVisualMixin appends normal display layers;
            // RadarDisplay itself is drawn by the shared native-monitor overlay.
            SimpleBlockEntityVisualizer.builder(CCBlockEntities.COMPUTER_CONTROL_DESK.get())
                .factory { context, blockEntity, partialTick ->
                    ConsoleVisual(context, blockEntity, partialTick)
                }
                .skipVanillaRender { true }
                .apply()
            RadarTrace.event(
                "C03_COMPUTER_VISUAL_REGISTERED",
                null,
                null,
                "ComputerControlDesk ConsoleVisual registered; RadarDisplay remains in shared overlay"
            )
        }
    }

    private fun registerRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        RadarTrace.event("C04_REGISTER_RENDERERS", null, null, "registering ComputerControlDeskRenderer")
        event.registerBlockEntityRenderer(
            CCBlockEntities.COMPUTER_CONTROL_DESK.get(),
            ::ComputerControlDeskRenderer
        )
    }

    private fun modifyBakingResult(event: ModelEvent.ModifyBakingResult) {
        ConsoleMultiblockModels.modifyBakingResult(event)
        ControlDeskItemOrientation.modifyBakingResult(event)
    }
}
