package de.teutonstudio.ccaeroworks.ctfcompat.network

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import de.TeutonStudio.TeutonLib.api.sable.SubLevelInteractionGeometry
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.ctfcompat.client.DashboardSourceClientState
import de.teutonstudio.ccaeroworks.ctfcompat.control.DashboardControllerLookup
import de.teutonstudio.ccaeroworks.ctfcompat.control.DashboardSourceStatus
import de.teutonstudio.ccaeroworks.ctfcompat.control.TrainControlBinding
import de.teutonstudio.ccaeroworks.ctfcompat.control.TrainControlBindings
import de.teutonstudio.ccaeroworks.ctfcompat.control.TrainFunction
import de.teutonstudio.ccaeroworks.display.ControlScriptBindings
import de.teutonstudio.ccaeroworks.display.DisplayBindings
import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext

data class RequestDashboardSourcePayload(val pos: BlockPos, val socket: Int) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField val TYPE = CustomPacketPayload.Type<RequestDashboardSourcePayload>(
            CCAeroworks.id("request_ctf_dashboard_source")
        )
        @JvmField val STREAM_CODEC = object : StreamCodec<RegistryFriendlyByteBuf, RequestDashboardSourcePayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf) =
                RequestDashboardSourcePayload(buffer.readBlockPos(), buffer.readVarInt())
            override fun encode(buffer: RegistryFriendlyByteBuf, payload: RequestDashboardSourcePayload) {
                buffer.writeBlockPos(payload.pos)
                buffer.writeVarInt(payload.socket)
            }
        }

        @JvmStatic
        fun handle(payload: RequestDashboardSourcePayload, context: IPayloadContext) {
            val player = context.player() as? ServerPlayer ?: return
            val desk = validateDesk(player, payload.pos, payload.socket) ?: return
            val module = desk.module(payload.socket) ?: return
            if (!ControlScriptBindings.supports(module)) return
            val resolution = DashboardControllerLookup.resolve(player.serverLevel(), desk.blockPos)
            PacketDistributor.sendToPlayer(
                player,
                DashboardSourceStatusPayload(
                    payload.pos,
                    payload.socket,
                    resolution.status,
                    resolution.controllerId,
                    resolution.controller?.blockPos
                )
            )
        }
    }
}

data class DashboardSourceStatusPayload(
    val pos: BlockPos,
    val socket: Int,
    val status: DashboardSourceStatus,
    val controllerId: UUID?,
    val controllerPos: BlockPos?
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField val TYPE = CustomPacketPayload.Type<DashboardSourceStatusPayload>(
            CCAeroworks.id("ctf_dashboard_source_status")
        )
        @JvmField val STREAM_CODEC = object : StreamCodec<RegistryFriendlyByteBuf, DashboardSourceStatusPayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): DashboardSourceStatusPayload {
                val pos = buffer.readBlockPos()
                val socket = buffer.readVarInt()
                val ordinal = buffer.readVarInt()
                require(ordinal in DashboardSourceStatus.entries.indices) { "Invalid Dashboard source status: $ordinal" }
                val controllerId = if (buffer.readBoolean()) buffer.readUUID() else null
                val controllerPos = if (buffer.readBoolean()) buffer.readBlockPos() else null
                return DashboardSourceStatusPayload(
                    pos, socket, DashboardSourceStatus.entries[ordinal], controllerId, controllerPos
                )
            }

            override fun encode(buffer: RegistryFriendlyByteBuf, payload: DashboardSourceStatusPayload) {
                buffer.writeBlockPos(payload.pos)
                buffer.writeVarInt(payload.socket)
                buffer.writeVarInt(payload.status.ordinal)
                buffer.writeBoolean(payload.controllerId != null)
                payload.controllerId?.let(buffer::writeUUID)
                buffer.writeBoolean(payload.controllerPos != null)
                payload.controllerPos?.let(buffer::writeBlockPos)
            }
        }

        @JvmStatic
        fun handle(payload: DashboardSourceStatusPayload, context: IPayloadContext) {
            DashboardSourceClientState.accept(payload)
        }
    }
}

data class SetDashboardSourcePayload(
    val pos: BlockPos,
    val socket: Int,
    val controllerId: UUID,
    val assignments: Map<String, TrainFunction>
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField val TYPE = CustomPacketPayload.Type<SetDashboardSourcePayload>(
            CCAeroworks.id("set_ctf_dashboard_source")
        )
        @JvmField val STREAM_CODEC = object : StreamCodec<RegistryFriendlyByteBuf, SetDashboardSourcePayload> {
            override fun decode(buffer: RegistryFriendlyByteBuf): SetDashboardSourcePayload {
                val pos = buffer.readBlockPos()
                val socket = buffer.readVarInt()
                val controller = buffer.readUUID()
                val size = buffer.readVarInt()
                require(size in 0..TrainControlBindings.MAX_ASSIGNMENTS) { "Invalid train binding size: $size" }
                val assignments = linkedMapOf<String, TrainFunction>()
                repeat(size) {
                    val channel = buffer.readUtf(128)
                    val ordinal = buffer.readVarInt()
                    require(ordinal in TrainFunction.entries.indices) { "Invalid train function: $ordinal" }
                    require(channel.isNotBlank() && channel !in assignments) { "Invalid train channel: $channel" }
                    assignments[channel] = TrainFunction.entries[ordinal]
                }
                return SetDashboardSourcePayload(pos, socket, controller, assignments)
            }

            override fun encode(buffer: RegistryFriendlyByteBuf, payload: SetDashboardSourcePayload) {
                buffer.writeBlockPos(payload.pos)
                buffer.writeVarInt(payload.socket)
                buffer.writeUUID(payload.controllerId)
                buffer.writeVarInt(payload.assignments.size)
                payload.assignments.forEach { (channel, function) ->
                    buffer.writeUtf(channel, 128)
                    buffer.writeVarInt(function.ordinal)
                }
            }
        }

        @JvmStatic
        fun handle(payload: SetDashboardSourcePayload, context: IPayloadContext) {
            val player = context.player() as? ServerPlayer ?: return
            val desk = validateDesk(player, payload.pos, payload.socket) ?: return
            val module = desk.module(payload.socket) ?: return
            if (!ControlScriptBindings.supports(module)) return
            val resolution = DashboardControllerLookup.resolve(player.serverLevel(), desk.blockPos)
            if (resolution.status != DashboardSourceStatus.AVAILABLE ||
                resolution.controllerId != payload.controllerId
            ) return
            if (payload.assignments.size > TrainControlBindings.MAX_ASSIGNMENTS) return
            val valid = payload.assignments.all { (channelId, function) ->
                val channel = module.channels().firstOrNull { it.id() == channelId }
                channel != null && TrainControlBindings.supports(channel.kind(), function)
            }
            if (!valid) return
            DisplayBindings.set(
                desk,
                payload.socket,
                TrainControlBindings.extension(TrainControlBinding(payload.controllerId, payload.assignments))
            )
        }
    }
}

private fun validateDesk(player: ServerPlayer, pos: BlockPos, socket: Int): ConsoleBlockEntity? {
    val level = player.serverLevel()
    if (!level.hasChunkAt(pos) || !SubLevelInteractionGeometry.mayInteract(player, level, pos)) return null
    val desk = level.getBlockEntity(pos) as? ConsoleBlockEntity ?: return null
    if (desk.hasController() && !desk.checkUser(player.uuid)) return null
    if (!SubLevelInteractionGeometry.withinReach(player, level, pos)) return null
    if (socket !in 0 until desk.socketCount()) return null
    return desk
}
