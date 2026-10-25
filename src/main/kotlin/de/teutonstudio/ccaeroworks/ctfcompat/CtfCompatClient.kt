package de.teutonstudio.ccaeroworks.ctfcompat

import de.teutonstudio.ccaeroworks.ctfcompat.client.registerDashboardSourceOptions
import net.neoforged.bus.api.IEventBus

object CtfCompatClient {
    fun register(bus: IEventBus) {
        registerDashboardSourceOptions()
    }
}
