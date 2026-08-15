package de.teutonstudio.ccaeroworks.network

import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.computer.ControlDeskUiSwitchState
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * Opens the embedded ComputerControlDesk represented by the Aeroworks screen which emitted this
 * request. The concrete desk anchor travels with the payload so the server never has to guess which
 * earlier right-click produced the currently visible controls screen.
 */
data class SwitchControlDeskUiPayload(
    val anchorPos: BlockPos
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<SwitchControlDeskUiPayload> =
            CustomPacketPayload.Type(CCAeroworks.id("switch_control_desk_ui"))

        @JvmField
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, SwitchControlDeskUiPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, SwitchControlDeskUiPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf): SwitchControlDeskUiPayload =
                    SwitchControlDeskUiPayload(buffer.readBlockPos())

                override fun encode(buffer: RegistryFriendlyByteBuf, payload: SwitchControlDeskUiPayload) {
                    buffer.writeBlockPos(payload.anchorPos)
                }
            }

        @JvmStatic
        fun handle(payload: SwitchControlDeskUiPayload, context: IPayloadContext) {
            val player = context.player() as? ServerPlayer ?: return
            ControlDeskUiSwitchState.switchToComputer(player, payload.anchorPos)
        }
    }
}
