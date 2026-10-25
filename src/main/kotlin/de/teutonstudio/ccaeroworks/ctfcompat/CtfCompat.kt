package de.teutonstudio.ccaeroworks.ctfcompat

import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.ctfcompat.computer.DashboardControllerPeripheralRegistry
import de.teutonstudio.ccaeroworks.ctfcompat.control.TrainControlBindings
import de.teutonstudio.ccaeroworks.ctfcompat.network.CtfPayloads
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModList
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLEnvironment

@Mod(CtfCompat.MOD_ID)
class CtfCompat(modEventBus: IEventBus) {
    init {
        if (ModList.get().isLoaded(CTF_MOD_ID)) {
            TrainControlBindings.register()
            DashboardControllerPeripheralRegistry.register(modEventBus)
            CtfPayloads.register(modEventBus)
            if (FMLEnvironment.dist == Dist.CLIENT) CtfCompatClient.register(modEventBus)
            CCAeroworks.LOGGER.info(
                "[CC-Aeroworks CTF Compat] Create: Train Functionality integration enabled"
            )
        } else {
            CCAeroworks.LOGGER.info(
                "[CC-Aeroworks CTF Compat] Create: Train Functionality not present; integration disabled"
            )
        }
    }

    companion object {
        const val MOD_ID = "cc_aeroworks_ctfcompat"
        const val CTF_MOD_ID = "create_train_functionality"
    }
}
