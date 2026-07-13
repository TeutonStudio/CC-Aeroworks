package de.teutonstudio.ccaeroworks.network

import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

object CCPayloads {
    fun register(bus: IEventBus) {
        bus.addListener(::registerPayloads)
    }

    private fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")
        registrar.playToServer(SetCombinedLeverValuePayload.TYPE, SetCombinedLeverValuePayload.STREAM_CODEC, SetCombinedLeverValuePayload::handle)
    }
}
