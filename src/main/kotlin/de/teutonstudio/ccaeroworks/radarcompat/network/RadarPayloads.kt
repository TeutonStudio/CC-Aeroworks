package de.teutonstudio.ccaeroworks.radarcompat.network

import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

object RadarPayloads {
    fun register(bus: IEventBus) = bus.addListener(::registerPayloads)

    private fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        event.registrar("1").playToServer(
            SetRadarDisplaySourcePayload.TYPE,
            SetRadarDisplaySourcePayload.STREAM_CODEC,
            SetRadarDisplaySourcePayload::handle
        )
    }
}
