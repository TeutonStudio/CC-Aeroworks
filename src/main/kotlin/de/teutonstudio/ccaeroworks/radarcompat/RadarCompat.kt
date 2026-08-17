package de.teutonstudio.ccaeroworks.radarcompat

import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.computercraft.ControlDeskPeripheralFactory
import de.teutonstudio.ccaeroworks.computer.source.InformationSourceExtensions
import de.teutonstudio.ccaeroworks.display.InteractiveDisplayExtensions
import de.teutonstudio.ccaeroworks.radarcompat.compat.computercraft.RadarControlDeskPeripheral
import de.teutonstudio.ccaeroworks.radarcompat.computer.source.RadarInformationSources
import de.teutonstudio.ccaeroworks.radarcompat.display.RadarDisplayBindings
import de.teutonstudio.ccaeroworks.radarcompat.display.RadarDisplayType
import de.teutonstudio.ccaeroworks.radarcompat.network.RadarPayloads
import de.teutonstudio.ccaeroworks.radarcompat.registry.RadarItems
import de.teutonstudio.ccaeroworks.radarcompat.registry.RadarModuleTypes
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModList
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLEnvironment

@Mod(RadarCompat.MOD_ID)
class RadarCompat(modEventBus: IEventBus) {
    init {
        if (ModList.get().isLoaded(CREATE_RADAR_MOD_ID)) {
            RadarDisplayBindings.register()
            InteractiveDisplayExtensions.register { RadarModuleTypes.radarDisplayType(it) == RadarDisplayType.LARGE }
            RadarModuleTypes.register()
            RadarItems.register(modEventBus)
            RadarPayloads.register(modEventBus)
            ControlDeskPeripheralFactory.registerExtension(::RadarControlDeskPeripheral)
            InformationSourceExtensions.register(RadarInformationSources::sources)
            if (FMLEnvironment.dist == Dist.CLIENT) RadarCompatClient.register(modEventBus)
            CCAeroworks.LOGGER.info("[CC-Aeroworks Radar Compat] Create: Radars integration enabled")
        } else {
            CCAeroworks.LOGGER.info("[CC-Aeroworks Radar Compat] Create: Radars not present; integration disabled")
        }
    }

    companion object {
        const val MOD_ID = "cc_aeroworks_radarcompat"
        const val CREATE_RADAR_MOD_ID = "create_radar"
    }
}
