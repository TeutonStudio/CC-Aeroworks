package de.teutonstudio.ccaeroworks.network

import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.computer.ControlDeskUiSwitchState
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * CC-Aeroworks only owns the Aeroworks -> embedded computer transition.
 * The reverse transition is handled by Aeroworks' native ConsoleSocket.reopenModuleMenu().
 */
class SwitchControlDeskUiPayload : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<SwitchControlDeskUiPayload> =
            CustomPacketPayload.Type(CCAeroworks.id("switch_control_desk_ui"))

        @JvmField
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, SwitchControlDeskUiPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, SwitchControlDeskUiPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf): SwitchControlDeskUiPayload =
                    SwitchControlDeskUiPayload()

                override fun encode(buffer: RegistryFriendlyByteBuf, payload: SwitchControlDeskUiPayload) {
                    // No payload data is needed: this packet has exactly one operation.
                }
            }

        @JvmStatic
        fun handle(payload: SwitchControlDeskUiPayload, context: IPayloadContext) {
            val player = context.player() as? ServerPlayer ?: return
            ControlDeskUiSwitchState.switchToComputer(player)
        }
    }
}
