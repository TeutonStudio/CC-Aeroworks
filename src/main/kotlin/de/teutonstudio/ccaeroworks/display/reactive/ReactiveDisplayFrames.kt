package de.teutonstudio.ccaeroworks.display.reactive

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.network.ReactiveDisplayPatchPayload
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.neoforged.neoforge.network.PacketDistributor
import java.util.WeakHashMap

object ReactiveDisplayFrames {
    private const val CLIENT_NBT_KEY = "CCAeroworksReactiveDisplayFrames"
    private val frames = WeakHashMap<ConsoleBlockEntity, MutableMap<Int, ReactiveDisplaySnapshot>>()

    @Synchronized
    fun snapshot(desk: ConsoleBlockEntity, socket: Int): ReactiveDisplaySnapshot? =
        frames[desk]?.get(socket)

    @Synchronized
    fun begin(desk: ConsoleBlockEntity, socket: Int, width: Int, height: Int): ReactiveDisplayFrameBuilder {
        val current = frames[desk]?.get(socket)
            ?.takeIf { it.width == width && it.height == height }
            ?: ReactiveDisplaySnapshot.blank(width, height)
        return ReactiveDisplayFrameBuilder(current)
    }

    @Synchronized
    fun commit(
        desk: ConsoleBlockEntity,
        socket: Int,
        builder: ReactiveDisplayFrameBuilder
    ): ReactiveDisplaySnapshot {
        val current = frames[desk]?.get(socket)
        val revision = (current?.revision ?: 0L) + 1L
        val (snapshot, patch) = builder.build(revision)
        if (patch == null) return current ?: snapshot

        frames.getOrPut(desk) { linkedMapOf() }[socket] = snapshot
        val level = desk.level as? ServerLevel
        if (level != null) sendPatch(level, desk, socket, patch)
        return snapshot
    }

    @Synchronized
    fun clear(desk: ConsoleBlockEntity, socket: Int) {
        val current = frames[desk]?.get(socket) ?: return
        val revision = current.revision + 1L
        frames[desk]?.remove(socket)
        if (frames[desk].isNullOrEmpty()) frames.remove(desk)
        val level = desk.level as? ServerLevel ?: return
        sendPatch(
            level,
            desk,
            socket,
            ReactiveDisplayPatch(current.width, current.height, revision, true, true, emptyList())
        )
    }

    @Synchronized
    fun applyClientPatch(
        desk: ConsoleBlockEntity,
        socket: Int,
        patch: ReactiveDisplayPatch
    ) {
        val previous = frames[desk]?.get(socket)
        // A single logical revision may be split over several ordered network packets. Accept
        // equal-revision continuation packets while still rejecting genuinely stale data.
        if (previous != null && patch.revision < previous.revision) return

        if (patch.remove) {
            frames[desk]?.remove(socket)
            if (frames[desk].isNullOrEmpty()) frames.remove(desk)
            return
        }

        val nextTiles = linkedMapOf<ReactiveTileKey, LongArray>()
        if (!patch.full && previous != null && previous.width == patch.width && previous.height == patch.height) {
            previous.tileKeys().forEach { key -> previous.tileRows(key)?.let { nextTiles[key] = it } }
        }
        patch.tiles.forEach { tile ->
            if (tile.cleared || tile.rows.all { it == 0L }) nextTiles.remove(tile.key)
            else if (tile.rows.size == REACTIVE_DISPLAY_TILE_SIZE) nextTiles[tile.key] = tile.rows.copyOf()
        }

        frames.getOrPut(desk) { linkedMapOf() }[socket] = ReactiveDisplaySnapshot.create(
            patch.width,
            patch.height,
            patch.revision,
            nextTiles
        )
    }

    @Synchronized
    fun writeClientTag(desk: ConsoleBlockEntity, tag: CompoundTag) {
        val current = frames[desk]
        if (current.isNullOrEmpty()) {
            tag.remove(CLIENT_NBT_KEY)
            return
        }
        val list = ListTag()
        current.forEach { (socket, frame) ->
            list.add(CompoundTag().apply {
                putInt("socket", socket)
                put("frame", frame.toTag())
            })
        }
        tag.put(CLIENT_NBT_KEY, list)
    }

    @Synchronized
    fun readClientTag(desk: ConsoleBlockEntity, tag: CompoundTag) {
        frames.remove(desk)
        if (!tag.contains(CLIENT_NBT_KEY, Tag.TAG_LIST.toInt())) return
        val list = tag.getList(CLIENT_NBT_KEY, Tag.TAG_COMPOUND.toInt())
        val decoded = linkedMapOf<Int, ReactiveDisplaySnapshot>()
        for (index in 0 until list.size) {
            val entry = list.getCompound(index)
            val socket = entry.getInt("socket")
            if (socket !in 0 until desk.socketCount()) continue
            val frame = ReactiveDisplaySnapshot.fromTag(entry.getCompound("frame")) ?: continue
            decoded[socket] = frame
        }
        if (decoded.isNotEmpty()) frames[desk] = decoded
    }

    private fun sendPatch(
        level: ServerLevel,
        desk: ConsoleBlockEntity,
        socket: Int,
        patch: ReactiveDisplayPatch
    ) {
        val chunks = if (patch.tiles.isEmpty()) {
            listOf(emptyList())
        } else {
            patch.tiles.chunked(ReactiveDisplayPatchPayload.MAX_TILES_PER_PACKET)
        }
        chunks.forEachIndexed { index, tiles ->
            PacketDistributor.sendToPlayersTrackingChunk(
                level,
                ChunkPos(desk.blockPos),
                ReactiveDisplayPatchPayload(
                    desk.blockPos.immutable(),
                    socket,
                    patch.width,
                    patch.height,
                    patch.revision,
                    patch.full && index == 0,
                    patch.remove && index == 0,
                    tiles
                )
            )
        }
    }
}
