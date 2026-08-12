package de.teutonstudio.ccaeroworks.network

import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.computer.ControlDeskUiSwitchState
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

data class SwitchControlDeskUiPayload(val target: Target) : CustomPacketPayload {
    enum class Target {
        COMPUTER,
        CONTROLS
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<SwitchControlDeskUiPayload> =
            CustomPacketPayload.Type(CCAeroworks.id("switch_control_desk_ui"))

        @JvmField
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, SwitchControlDeskUiPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, SwitchControlDeskUiPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf): SwitchControlDeskUiPayload =
                    SwitchControlDeskUiPayload(
                        if (buffer.readBoolean()) Target.CONTROLS else Target.COMPUTER
                    )

                override fun encode(buffer: RegistryFriendlyByteBuf, payload: SwitchControlDeskUiPayload) {
                    buffer.writeBoolean(payload.target == Target.CONTROLS)
                }
            }

        @JvmStatic
        fun handle(payload: SwitchControlDeskUiPayload, context: IPayloadContext) {
            val player = context.player() as? ServerPlayer ?: return
            when (payload.target) {
                Target.COMPUTER -> ControlDeskUiSwitchState.switchToComputer(player)
                Target.CONTROLS -> ControlDeskUiSwitchState.switchToControls(player)
            }
        }
    }
}
