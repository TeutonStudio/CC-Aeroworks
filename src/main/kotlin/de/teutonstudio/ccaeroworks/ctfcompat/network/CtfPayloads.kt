package de.teutonstudio.ccaeroworks.ctfcompat.network

import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

object CtfPayloads {
    fun register(bus: IEventBus) = bus.addListener(::registerPayloads)

    private fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")
        registrar.playToServer(
            RequestDashboardSourcePayload.TYPE,
            RequestDashboardSourcePayload.STREAM_CODEC,
            RequestDashboardSourcePayload::handle
        )
        registrar.playToClient(
            DashboardSourceStatusPayload.TYPE,
            DashboardSourceStatusPayload.STREAM_CODEC,
            DashboardSourceStatusPayload::handle
        )
        registrar.playToServer(
            SetDashboardSourcePayload.TYPE,
            SetDashboardSourcePayload.STREAM_CODEC,
            SetDashboardSourcePayload::handle
        )
    }
}
