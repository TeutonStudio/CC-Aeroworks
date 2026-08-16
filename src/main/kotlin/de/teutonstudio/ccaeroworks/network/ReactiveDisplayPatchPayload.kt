package de.teutonstudio.ccaeroworks.network

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.display.reactive.REACTIVE_DISPLAY_TILE_SIZE
import de.teutonstudio.ccaeroworks.display.reactive.ReactiveDisplayFrames
import de.teutonstudio.ccaeroworks.display.reactive.ReactiveDisplayPatch
import de.teutonstudio.ccaeroworks.display.reactive.ReactiveTileKey
import de.teutonstudio.ccaeroworks.display.reactive.ReactiveTilePatch
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.neoforge.network.handling.IPayloadContext

data class ReactiveDisplayPatchPayload(
    val pos: BlockPos,
    val socket: Int,
    val width: Int,
    val height: Int,
    val revision: Long,
    val full: Boolean,
    val remove: Boolean,
    val tiles: List<ReactiveTilePatch>
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    fun toPatch(): ReactiveDisplayPatch = ReactiveDisplayPatch(
        width = width,
        height = height,
        revision = revision,
        full = full,
        remove = remove,
        tiles = tiles
    )

    companion object {
        const val MAX_TILES_PER_PACKET: Int = 256

        @JvmField
        val TYPE: CustomPacketPayload.Type<ReactiveDisplayPatchPayload> =
            CustomPacketPayload.Type(CCAeroworks.id("reactive_display_patch"))

        @JvmField
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ReactiveDisplayPatchPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, ReactiveDisplayPatchPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf): ReactiveDisplayPatchPayload {
                    val pos = buffer.readBlockPos()
                    val socket = buffer.readVarInt()
                    val width = buffer.readVarInt()
                    val height = buffer.readVarInt()
                    val revision = buffer.readVarLong()
                    val full = buffer.readBoolean()
                    val remove = buffer.readBoolean()
                    val count = buffer.readVarInt()
                    require(count in 0..MAX_TILES_PER_PACKET) {
                        "Reactive display patch contains $count tiles; maximum is $MAX_TILES_PER_PACKET"
                    }
                    val tiles = ArrayList<ReactiveTilePatch>(count)
                    repeat(count) {
                        val x = buffer.readVarInt()
                        val y = buffer.readVarInt()
                        require(x >= 0 && y >= 0) { "Reactive display tile coordinates must not be negative" }
                        val rowCount = buffer.readVarInt()
                        val rows = when (rowCount) {
                            0 -> LongArray(0)
                            REACTIVE_DISPLAY_TILE_SIZE -> LongArray(REACTIVE_DISPLAY_TILE_SIZE) { buffer.readLong() }
                            else -> throw IllegalArgumentException("Invalid reactive display tile row count: $rowCount")
                        }
                        tiles += ReactiveTilePatch(ReactiveTileKey(x, y), rows)
                    }
                    return ReactiveDisplayPatchPayload(pos, socket, width, height, revision, full, remove, tiles)
                }

                override fun encode(buffer: RegistryFriendlyByteBuf, payload: ReactiveDisplayPatchPayload) {
                    require(payload.tiles.size <= MAX_TILES_PER_PACKET) {
                        "Reactive display payload must be split before encoding"
                    }
                    buffer.writeBlockPos(payload.pos)
                    buffer.writeVarInt(payload.socket)
                    buffer.writeVarInt(payload.width)
                    buffer.writeVarInt(payload.height)
                    buffer.writeVarLong(payload.revision)
                    buffer.writeBoolean(payload.full)
                    buffer.writeBoolean(payload.remove)
                    buffer.writeVarInt(payload.tiles.size)
                    payload.tiles.forEach { tile ->
                        buffer.writeVarInt(tile.key.x)
                        buffer.writeVarInt(tile.key.y)
                        buffer.writeVarInt(tile.rows.size)
                        tile.rows.forEach(buffer::writeLong)
                    }
                }
            }

        @JvmStatic
        fun handle(payload: ReactiveDisplayPatchPayload, context: IPayloadContext) {
            context.enqueueWork {
                val level = context.player().level()
                if (!level.hasChunkAt(payload.pos)) return@enqueueWork
                val desk = level.getBlockEntity(payload.pos) as? ConsoleBlockEntity ?: return@enqueueWork
                if (payload.socket !in 0 until desk.socketCount()) return@enqueueWork
                if (payload.width <= 0 || payload.height <= 0 || payload.tiles.size > MAX_TILES_PER_PACKET) {
                    return@enqueueWork
                }
                ReactiveDisplayFrames.applyClientPatch(desk, payload.socket, payload.toPatch())
            }
        }

        fun from(pos: BlockPos, socket: Int, patch: ReactiveDisplayPatch): ReactiveDisplayPatchPayload =
            ReactiveDisplayPatchPayload(
                pos.immutable(),
                socket,
                patch.width,
                patch.height,
                patch.revision,
                patch.full,
                patch.remove,
                patch.tiles
            )
    }
}
