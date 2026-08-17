package de.teutonstudio.ccaeroworks.network

import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

object CCPayloads {
    fun register(bus: IEventBus) { bus.addListener(::registerPayloads) }
    private fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")
        registrar.playToServer(SetCombinedLeverValuePayload.TYPE, SetCombinedLeverValuePayload.STREAM_CODEC, SetCombinedLeverValuePayload::handle)
        registrar.playToServer(CombinedControlSamplePayload.TYPE, CombinedControlSamplePayload.STREAM_CODEC, CombinedControlSamplePayload::handle)
        registrar.playToServer(DisplayPointerActionPayload.TYPE, DisplayPointerActionPayload.STREAM_CODEC, DisplayPointerActionPayload::handle)
        registrar.playToServer(SwitchControlDeskUiPayload.TYPE, SwitchControlDeskUiPayload.STREAM_CODEC, SwitchControlDeskUiPayload::handle)
        registrar.playToServer(SetDisplayTouchScriptPayload.TYPE, SetDisplayTouchScriptPayload.STREAM_CODEC, SetDisplayTouchScriptPayload::handle)
        registrar.playToServer(RequestDisplayScriptCatalogPayload.TYPE, RequestDisplayScriptCatalogPayload.STREAM_CODEC, RequestDisplayScriptCatalogPayload::handle)
        registrar.playToClient(DisplayScriptCatalogPayload.TYPE, DisplayScriptCatalogPayload.STREAM_CODEC, DisplayScriptCatalogPayload::handle)
        registrar.playToServer(RequestWireChannelSnapshotPayload.TYPE, RequestWireChannelSnapshotPayload.STREAM_CODEC, RequestWireChannelSnapshotPayload::handle)
        registrar.playToServer(MutateWireChannelPayload.TYPE, MutateWireChannelPayload.STREAM_CODEC, MutateWireChannelPayload::handle)
        registrar.playToServer(MutateChannelGroupPayload.TYPE, MutateChannelGroupPayload.STREAM_CODEC, MutateChannelGroupPayload::handle)
        registrar.playToClient(WireChannelSnapshotPayload.TYPE, WireChannelSnapshotPayload.STREAM_CODEC, WireChannelSnapshotPayload::handle)
        registrar.playToServer(RequestInformationSourceSnapshotPayload.TYPE, RequestInformationSourceSnapshotPayload.STREAM_CODEC, RequestInformationSourceSnapshotPayload::handle)
        registrar.playToClient(InformationSourceSnapshotPayload.TYPE, InformationSourceSnapshotPayload.STREAM_CODEC, InformationSourceSnapshotPayload::handle)
    }
}
