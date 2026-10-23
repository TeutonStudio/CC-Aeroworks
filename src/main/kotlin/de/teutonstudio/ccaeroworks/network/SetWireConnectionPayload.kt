package de.teutonstudio.ccaeroworks.network

import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.drivebywire.NativeDriveByWireChannels
import de.TeutonStudio.TeutonLib.api.sable.SubLevelInteractionGeometry
import de.teutonstudio.ccaeroworks.computer.ControlDeskUiSwitchState
import de.teutonstudio.ccaeroworks.computer.channel.ChannelRegistry
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext

/** The client submits a canonical channel ID, never an arbitrary physical source or DBW channel. */
data class SetWireConnectionPayload(val channelId: String, val target: BlockPos, val side: Direction, val add: Boolean) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField val TYPE = CustomPacketPayload.Type<SetWireConnectionPayload>(CCAeroworks.id("set_wire_connection"))
        @JvmField val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, SetWireConnectionPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, SetWireConnectionPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf): SetWireConnectionPayload {
                    val id = buffer.readUtf(512)
                    val pos = buffer.readBlockPos()
                    val side = Direction.entries.getOrNull(buffer.readVarInt()) ?: error("Invalid target side")
                    return SetWireConnectionPayload(id, pos, side, buffer.readBoolean())
                }
                override fun encode(buffer: RegistryFriendlyByteBuf, payload: SetWireConnectionPayload) {
                    buffer.writeUtf(payload.channelId, 512)
                    buffer.writeBlockPos(payload.target)
                    buffer.writeVarInt(payload.side.ordinal)
                    buffer.writeBoolean(payload.add)
                }
            }

        @JvmStatic fun handle(payload: SetWireConnectionPayload, context: IPayloadContext) {
            val player = context.player() as? ServerPlayer ?: return
            val result = runCatching {
                check(!player.isSpectator) { "Spectators cannot change connections" }
                val owner = ControlDeskUiSwitchState.activeComputerDesk(player) ?: error("Open the console again")
                val level = player.serverLevel()
                val network = ConsoleMultiblockManager.resolve(level, owner.blockPos)
                check(network.state == ConsoleNetworkState.ACTIVE && network.owner === owner) { "Console network is not active" }
                check(network.members.any { SubLevelInteractionGeometry.withinReach(player, level, it.pos) }) { "Console is out of reach" }
                check(network.members.none { it.desk.hasController() && !it.desk.checkUser(player.uuid) }) { "Console is in use" }
                check(level.isInWorldBounds(payload.target) && level.hasChunkAt(payload.target)) { "Target block is not loaded or outside world bounds" }
                check(SubLevelInteractionGeometry.mayInteract(player, level, payload.target)) { "Cannot modify this target" }
                check(network.members.none { it.pos == payload.target }) { "Select a target outside this console" }
                if (payload.add) check(!level.getBlockState(payload.target).isAir) { "Target block is empty" }
                val channel = ChannelRegistry.findById(owner, payload.channelId) ?: error("Channel is no longer available")
                val control = channel.controlTarget
                val (sourcePos, nativeChannel) = if (control == null) {
                    owner.blockPos to (channel.wireName ?: error("Channel has no wire output"))
                } else {
                    val member = network.members.firstOrNull { it.id == control.deskId } ?: error("Source desk is missing")
                    check(SubLevelInteractionGeometry.mayInteract(player, level, member.pos)) { "Cannot modify this source" }
                    val native = NativeDriveByWireChannels.channels(member.desk).firstOrNull {
                        it.socket == control.socket && it.channelId == control.nativeChannel && it.sign == control.sign
                    } ?: error("Channel has no wire output")
                    member.pos to native.id
                }
                check(SubLevelInteractionGeometry.mayInteract(player, level, sourcePos)) { "Cannot modify this source" }
                owner.wireBank.changeConnection(sourcePos, nativeChannel, payload.target, payload.side, payload.add, player)
            }
            PacketDistributor.sendToPlayer(player, WireConnectionResultPayload(
                result.isSuccess,
                result.exceptionOrNull()?.message ?: if (payload.add) "Connection added" else "Connection removed"
            ))
            sendSnapshot(player)
        }
    }
}

data class WireConnectionResultPayload(val success: Boolean, val message: String) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
    companion object {
        @JvmField val TYPE = CustomPacketPayload.Type<WireConnectionResultPayload>(CCAeroworks.id("wire_connection_result"))
        @JvmField val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, WireConnectionResultPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, WireConnectionResultPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf) = WireConnectionResultPayload(buffer.readBoolean(), buffer.readUtf(256))
                override fun encode(buffer: RegistryFriendlyByteBuf, payload: WireConnectionResultPayload) {
                    buffer.writeBoolean(payload.success)
                    buffer.writeUtf(payload.message.take(256), 256)
                }
            }
        @JvmStatic fun handle(payload: WireConnectionResultPayload, context: IPayloadContext) {
            WireConnectionResultState.result = payload
        }
    }
}

object WireConnectionResultState {
    var result: WireConnectionResultPayload? = null
}
