package de.teutonstudio.ccaeroworks.network

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.display.DisplayBindings
import de.teutonstudio.ccaeroworks.display.DisplayContentSource
import de.teutonstudio.ccaeroworks.display.RadarSourceKey
import de.teutonstudio.ccaeroworks.display.RadarSourceRegistry
import de.teutonstudio.ccaeroworks.registry.CCModuleTypes
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

data class SetRadarDisplaySourcePayload(
    val pos: BlockPos,
    val socket: Int,
    val sourceIngressPos: BlockPos?
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<SetRadarDisplaySourcePayload> =
            CustomPacketPayload.Type(CCAeroworks.id("set_radar_display_source"))

        @JvmField
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, SetRadarDisplaySourcePayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, SetRadarDisplaySourcePayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf): SetRadarDisplaySourcePayload {
                    val pos = buffer.readBlockPos()
                    val socket = buffer.readVarInt()
                    val source = if (buffer.readBoolean()) buffer.readBlockPos() else null
                    return SetRadarDisplaySourcePayload(pos, socket, source)
                }

                override fun encode(buffer: RegistryFriendlyByteBuf, payload: SetRadarDisplaySourcePayload) {
                    buffer.writeBlockPos(payload.pos)
                    buffer.writeVarInt(payload.socket)
                    buffer.writeBoolean(payload.sourceIngressPos != null)
                    payload.sourceIngressPos?.let(buffer::writeBlockPos)
                }
            }

        @JvmStatic
        fun handle(payload: SetRadarDisplaySourcePayload, context: IPayloadContext) {
            val player = context.player() as? ServerPlayer ?: return
            val level = player.serverLevel()
            if (!level.hasChunkAt(payload.pos) || !level.mayInteract(player, payload.pos)) return

            val desk = level.getBlockEntity(payload.pos) as? ConsoleBlockEntity ?: return
            if (desk.hasController() && !desk.checkUser(player.uuid)) return
            val maximumDistance = player.blockInteractionRange() + 1.0
            if (player.distanceToSqr(payload.pos.center) > maximumDistance * maximumDistance) return
            if (payload.socket !in 0 until desk.socketCount()) return
            val module = desk.module(payload.socket) ?: return
            if (CCModuleTypes.radarDisplayType(module.type()) == null) return

            val content = payload.sourceIngressPos?.let { ingressPos ->
                val source = RadarSourceRegistry.sources(desk)
                    .firstOrNull { it.ingressPos == ingressPos }
                    ?: return
                DisplayContentSource.RadarSource(
                    RadarSourceKey(level.dimension().location(), source.ingressPos)
                )
            } ?: DisplayContentSource.Default

            DisplayBindings.setContent(desk, payload.socket, content)
        }
    }
}
