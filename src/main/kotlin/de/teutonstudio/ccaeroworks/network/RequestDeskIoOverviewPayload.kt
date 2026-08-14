package de.teutonstudio.ccaeroworks.network

import com.google.gson.Gson
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import de.teutonstudio.ccaeroworks.computer.ControlDeskUiSwitchState
import de.teutonstudio.ccaeroworks.computer.io.DeskIoInventory
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext

/** Requests the server-authoritative ControlDesk I/O overview for one reachable desk. */
data class RequestDeskIoOverviewPayload(val pos: BlockPos) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<RequestDeskIoOverviewPayload> =
            CustomPacketPayload.Type(CCAeroworks.id("request_desk_io_overview"))

        @JvmField
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, RequestDeskIoOverviewPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, RequestDeskIoOverviewPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf): RequestDeskIoOverviewPayload =
                    RequestDeskIoOverviewPayload(buffer.readBlockPos())

                override fun encode(buffer: RegistryFriendlyByteBuf, payload: RequestDeskIoOverviewPayload) {
                    buffer.writeBlockPos(payload.pos)
                }
            }

        @JvmStatic
        fun handle(payload: RequestDeskIoOverviewPayload, context: IPayloadContext) {
            val player = context.player() as? ServerPlayer ?: return
            val level = player.serverLevel()
            if (!level.hasChunkAt(payload.pos) || !level.mayInteract(player, payload.pos)) return
            if (!AeroworksTypes.isControlDesk(level.getBlockState(payload.pos).block)) return

            val maximumDistance = player.blockInteractionRange() + 1.0
            if (player.distanceToSqr(payload.pos.center) > maximumDistance * maximumDistance) return

            val direct = level.getBlockEntity(payload.pos) as? ComputerControlDeskBlockEntity
            val network = ConsoleMultiblockManager.resolve(level, payload.pos)
            val owner = direct ?: network.owner?.takeIf { network.state == ConsoleNetworkState.ACTIVE } ?: return

            ControlDeskUiSwitchState.remember(player, payload.pos)
            val json = GSON.toJson(DeskIoInventory.overview(owner, payload.pos))
            if (json.length > DeskIoOverviewPayload.MAX_JSON_LENGTH) return
            PacketDistributor.sendToPlayer(player, DeskIoOverviewPayload(payload.pos, json))
        }

        private val GSON = Gson()
    }
}
