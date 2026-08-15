package de.teutonstudio.ccaeroworks.network

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.display.DeskDisplayType
import de.teutonstudio.ccaeroworks.display.DisplayScriptCatalog
import de.teutonstudio.ccaeroworks.display.DisplayScriptCatalogState
import de.teutonstudio.ccaeroworks.display.DisplayScriptDescriptor
import de.teutonstudio.ccaeroworks.registry.CCModuleTypes
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext

data class RequestDisplayScriptCatalogPayload(
    val pos: BlockPos,
    val socket: Int
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE = CustomPacketPayload.Type<RequestDisplayScriptCatalogPayload>(
            CCAeroworks.id("request_display_script_catalog")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, RequestDisplayScriptCatalogPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, RequestDisplayScriptCatalogPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf) =
                    RequestDisplayScriptCatalogPayload(buffer.readBlockPos(), buffer.readVarInt())

                override fun encode(buffer: RegistryFriendlyByteBuf, payload: RequestDisplayScriptCatalogPayload) {
                    buffer.writeBlockPos(payload.pos)
                    buffer.writeVarInt(payload.socket)
                }
            }

        @JvmStatic
        fun handle(payload: RequestDisplayScriptCatalogPayload, context: IPayloadContext) {
            val player = context.player() as? ServerPlayer ?: return
            val desk = validateDesk(player, payload.pos, payload.socket) ?: return
            val module = desk.module(payload.socket) ?: return
            val displayType = CCModuleTypes.displayType(module.type()) ?: return
            val owner = DisplayScriptCatalog.ownerFor(desk) ?: return
            val entries = DisplayScriptCatalog.scan(owner, force = true)
                .filter { it.supports(displayType) }
            PacketDistributor.sendToPlayer(
                player,
                DisplayScriptCatalogPayload(payload.pos, payload.socket, entries)
            )
        }
    }
}

data class DisplayScriptCatalogPayload(
    val pos: BlockPos,
    val socket: Int,
    val entries: List<DisplayScriptDescriptor>
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE = CustomPacketPayload.Type<DisplayScriptCatalogPayload>(
            CCAeroworks.id("display_script_catalog")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, DisplayScriptCatalogPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, DisplayScriptCatalogPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf): DisplayScriptCatalogPayload {
                    val pos = buffer.readBlockPos()
                    val socket = buffer.readVarInt()
                    val count = buffer.readVarInt()
                    require(count in 0..DisplayScriptCatalog.MAX_SCRIPTS) { "Invalid display script catalog size: $count" }
                    val entries = ArrayList<DisplayScriptDescriptor>(count)
                    repeat(count) {
                        entries += DisplayScriptDescriptor(
                            path = buffer.readUtf(DisplayScriptCatalog.MAX_PATH_LENGTH),
                            name = buffer.readUtf(128),
                            display = buffer.readBoolean(),
                            touchDisplay = buffer.readBoolean()
                        )
                    }
                    return DisplayScriptCatalogPayload(pos, socket, entries)
                }

                override fun encode(buffer: RegistryFriendlyByteBuf, payload: DisplayScriptCatalogPayload) {
                    buffer.writeBlockPos(payload.pos)
                    buffer.writeVarInt(payload.socket)
                    buffer.writeVarInt(payload.entries.size.coerceAtMost(DisplayScriptCatalog.MAX_SCRIPTS))
                    payload.entries.take(DisplayScriptCatalog.MAX_SCRIPTS).forEach { entry ->
                        buffer.writeUtf(entry.path, DisplayScriptCatalog.MAX_PATH_LENGTH)
                        buffer.writeUtf(entry.name, 128)
                        buffer.writeBoolean(entry.display)
                        buffer.writeBoolean(entry.touchDisplay)
                    }
                }
            }

        @JvmStatic
        fun handle(payload: DisplayScriptCatalogPayload, context: IPayloadContext) {
            DisplayScriptCatalogState.accept(payload.pos, payload.socket, payload.entries)
        }
    }
}

private fun validateDesk(player: ServerPlayer, pos: BlockPos, socket: Int): ConsoleBlockEntity? {
    val level = player.serverLevel()
    if (!level.hasChunkAt(pos) || !level.mayInteract(player, pos)) return null
    val desk = level.getBlockEntity(pos) as? ConsoleBlockEntity ?: return null
    if (desk.hasController() && !desk.checkUser(player.uuid)) return null
    val maximumDistance = player.blockInteractionRange() + 1.0
    if (player.distanceToSqr(pos.center) > maximumDistance * maximumDistance) return null
    if (socket !in 0 until desk.socketCount()) return null
    return desk
}
