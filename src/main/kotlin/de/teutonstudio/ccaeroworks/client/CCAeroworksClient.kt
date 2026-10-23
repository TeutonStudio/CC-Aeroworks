package de.teutonstudio.ccaeroworks.client

import com.mred231.aeroworks.content.controls.console.ConsoleVisual
import com.mred231.aeroworks.content.controls.module.ModulePartials
import com.mred231.aeroworks.content.controls.module.ModuleTypes
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
import de.teutonstudio.ccaeroworks.compat.teutonlib.DeskComputerWorkspaceClient
import de.teutonstudio.ccaeroworks.registry.CCBlockEntities
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer
import net.minecraft.client.resources.model.ModelResourceLocation
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
        modBus.addListener<net.neoforged.neoforge.client.event.RegisterMenuScreensEvent> { event ->
            event.register(de.teutonstudio.ccaeroworks.registry.CCMenus.CONSOLE_INVENTORY.get(), ::ConsoleInventoryScreen)
        }
        modBus.addListener(::clientSetup)
        modBus.addListener(::registerRenderers)
        modBus.addListener<ModelEvent.RegisterAdditional>(::registerModulePartials)
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
            InputSource.displayName(CombinedInputSource.ID)
            DeskComputerWorkspaceClient.register()
            PonderIndex.addPlugin(CCAeroworksPonderPlugin())

            SimpleBlockEntityVisualizer.builder(CCBlockEntities.COMPUTER_CONTROL_DESK.get())
                .factory { context, blockEntity, partialTick ->
                    ConsoleVisual(context, blockEntity, partialTick)
                }
                .skipVanillaRender { true }
                .apply()
        }
    }

    private fun registerModulePartials(event: ModelEvent.RegisterAdditional) {
        // Aeroworks initializes its partial cache before extension mods have registered their
        // ModuleTypes. Calling init again discovers our types, but doing so in clientSetup is too
        // late for Flywheel's initial model bake and leaves old/held display previews with a null
        // BakedModel. Discover and register every CC-Aeroworks extension model while additional
        // models are still accepted. This also covers optional radar module types.
        ModulePartials.init()
        ModuleTypes.all()
            .filterKeys { it.namespace == CCAeroworks.MOD_ID }
            .values
            .flatMap { it.parts() }
            .map { it.model() }
            .distinct()
            .map(ModelResourceLocation::standalone)
            .forEach(event::register)
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
