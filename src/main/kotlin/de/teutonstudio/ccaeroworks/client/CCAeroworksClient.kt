package de.teutonstudio.ccaeroworks.client

import com.mred231.aeroworks.content.controls.ConsoleVisual
import com.mred231.aeroworks.content.controls.ModulePartials
import com.mred231.aeroworks.foundation.input.InputSource
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.client.creative.AeroworksCreativeSections
import de.teutonstudio.ccaeroworks.client.display.DeskDisplayModels
import de.teutonstudio.ccaeroworks.client.display.DeskDisplayTextureCache
import de.teutonstudio.ccaeroworks.client.display.DeskPixelOverlayRenderer
import de.teutonstudio.ccaeroworks.client.display.DisplayPointerRenderer
import de.teutonstudio.ccaeroworks.client.ponder.CCAeroworksPonderPlugin
import de.teutonstudio.ccaeroworks.input.CombinedInputSource
import de.teutonstudio.ccaeroworks.input.CombinedLeverController
import de.teutonstudio.ccaeroworks.input.DisplayCombinedInputController
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
        DeskDisplayModels.init()
        modBus.addListener(::clientSetup)
        modBus.addListener(::registerRenderers)
        modBus.addListener<ModelEvent.RegisterAdditional>(ConsoleMultiblockModels::registerAdditional)
        modBus.addListener<ModelEvent.ModifyBakingResult>(::modifyBakingResult)
        NeoForge.EVENT_BUS.addListener(DeskDisplayTextureCache::clientTick)
        NeoForge.EVENT_BUS.addListener(DeskPixelOverlayRenderer::renderLevel)
        NeoForge.EVENT_BUS.addListener(DisplayPointerRenderer::renderLevel)
        NeoForge.EVENT_BUS.register(CombinedLeverController)
        NeoForge.EVENT_BUS.register(DisplayCombinedInputController)
        NeoForge.EVENT_BUS.register(AeroworksCreativeSections)
        NeoForge.EVENT_BUS.register(GuideBookClientHandler)
        NeoForge.EVENT_BUS.register(DisplayTooltipHandler)
    }

    private fun clientSetup(event: FMLClientSetupEvent) {
        event.enqueueWork {
            ModulePartials.init()
            InputSource.displayName(CombinedInputSource.ID)
            PonderIndex.addPlugin(CCAeroworksPonderPlugin())

            SimpleBlockEntityVisualizer.builder(CCBlockEntities.COMPUTER_CONTROL_DESK.get())
                .factory { context, blockEntity, partialTick ->
                    ConsoleVisual(context, blockEntity, partialTick)
                }
                .skipVanillaRender { true }
                .apply()
        }
    }

    private fun registerRenderers(event: EntityRenderersEvent.RegisterRenderers) {
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
