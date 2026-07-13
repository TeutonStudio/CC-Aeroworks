package de.teutonstudio.ccaeroworks

import de.teutonstudio.ccaeroworks.client.CCAeroworksClient
import de.teutonstudio.ccaeroworks.compat.computercraft.ControlDeskPeripheralRegistry
import de.teutonstudio.ccaeroworks.compat.computercraft.ControlDeskPeripheralState
import de.teutonstudio.ccaeroworks.config.CCClientConfig
import de.teutonstudio.ccaeroworks.network.CCPayloads
import de.teutonstudio.ccaeroworks.registry.CCDisplayTargets
import de.teutonstudio.ccaeroworks.registry.CCItems
import de.teutonstudio.ccaeroworks.registry.CCModuleTypes
import net.minecraft.resources.ResourceLocation
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.common.NeoForge
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Mod(CCAeroworks.MOD_ID)
class CCAeroworks(modEventBus: IEventBus, modContainer: ModContainer) {
    init {
        CCModuleTypes.register()
        CCItems.register(modEventBus)
        CCDisplayTargets.register(modEventBus)
        ControlDeskPeripheralRegistry.register(modEventBus)
        CCPayloads.register(modEventBus)
        modContainer.registerConfig(ModConfig.Type.CLIENT, CCClientConfig.SPEC, "cc_aeroworks-client.toml")
        NeoForge.EVENT_BUS.register(ControlDeskPeripheralState)
        if (FMLEnvironment.dist == Dist.CLIENT) CCAeroworksClient.register(modEventBus)
        LOGGER.info("[CC-Aeroworks] Initializing")
    }

    companion object {
        const val MOD_ID: String = "cc_aeroworks"
        const val DISPLAY_TARGET_ID: String = "control_desk"
        const val PERIPHERAL_TYPE: String = "cc_aeroworks_control_desk"
        const val INPUT_EVENT: String = "cc_aeroworks_desk_input"

        @JvmField
        val LOGGER: Logger = LoggerFactory.getLogger("CC-Aeroworks")

        @JvmStatic
        fun id(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID, path)
    }
}
