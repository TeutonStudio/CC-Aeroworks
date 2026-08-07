package de.teutonstudio.ccaeroworks.display

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.nbt.Tag
import net.minecraft.world.phys.Vec3
import java.util.Locale

enum class RadarDisplayTrackSprite {
    CONTRAPTION,
    PLAYER,
    PROJECTILE,
    ENTITY;

    companion object {
        fun fromCategory(category: Any?): RadarDisplayTrackSprite = when (
            category?.toString()?.uppercase(Locale.ROOT)
        ) {
            "SABLE", "CONTRAPTION" -> CONTRAPTION
            "PLAYER" -> PLAYER
            "PROJECTILE" -> PROJECTILE
            else -> ENTITY
        }

        fun fromTag(value: String): RadarDisplayTrackSprite = runCatching {
            valueOf(value.uppercase(Locale.ROOT))
        }.getOrDefault(ENTITY)
    }
}

enum class RadarLinkStatus {
    NOT_LINKED,
    RADAR_NOT_ASSIGNED,
    RADAR_NOT_LOADED,
    RADAR_STOPPED,
    INVALID_RANGE,
    API_ERROR,
    ACTIVE;

    companion object {
        fun fromTag(value: String, connected: Boolean): RadarLinkStatus = runCatching {
            valueOf(value.uppercase(Locale.ROOT))
        }.getOrElse { if (connected) ACTIVE else NOT_LINKED }
    }
}

data class RadarDisplayTrack(
    val id: String,
    val position: Vec3,
    val velocity: Vec3,
    val sprite: RadarDisplayTrackSprite = RadarDisplayTrackSprite.ENTITY
) {
    fun toTag(): CompoundTag = CompoundTag().apply {
        putString("id", this@RadarDisplayTrack.id)
        put("position", position.toTag())
        put("velocity", velocity.toTag())
        putString("sprite", sprite.name.lowercase(Locale.ROOT))
    }

    companion object {
        fun fromTag(tag: CompoundTag): RadarDisplayTrack? {
            if (!tag.contains("position", Tag.TAG_COMPOUND.toInt())) return null
            val id = tag.getString("id")
            if (id.isEmpty()) return null
            return RadarDisplayTrack(
                id = id,
                position = tag.getCompound("position").toVec3(),
                velocity = if (tag.contains("velocity", Tag.TAG_COMPOUND.toInt())) {
                    tag.getCompound("velocity").toVec3()
                } else {
                    Vec3.ZERO
                },
                sprite = RadarDisplayTrackSprite.fromTag(tag.getString("sprite"))
            )
        }
    }
}

data class RadarDisplaySnapshot(
    val connected: Boolean,
    val operational: Boolean,
    val radarPos: BlockPos?,
    val center: Vec3,
    val range: Double,
    val detectionTag: CompoundTag,
    val selectedTrackId: String?,
    val tracks: List<RadarDisplayTrack>,
    val updatedAt: Long,
    val status: RadarLinkStatus
) {
    fun toTag(): CompoundTag = CompoundTag().apply {
        putBoolean("connected", connected)
        putBoolean("operational", operational)
        radarPos?.let { put("radarPos", NbtUtils.writeBlockPos(it)) }
        put("center", center.toTag())
        putDouble("range", range)
        put("detection", detectionTag.copy())
        selectedTrackId?.let { putString("selected", it) }
        putLong("updatedAt", updatedAt)
        putString("status", status.name.lowercase(Locale.ROOT))
        put("tracks", ListTag().apply {
            tracks.take(MAX_SYNCED_TRACKS).forEach { add(it.toTag()) }
        })
    }

    fun sameContentAs(other: RadarDisplaySnapshot?): Boolean =
        other != null &&
            connected == other.connected &&
            operational == other.operational &&
            radarPos == other.radarPos &&
            center == other.center &&
            range == other.range &&
            detectionTag == other.detectionTag &&
            selectedTrackId == other.selectedTrackId &&
            tracks == other.tracks &&
            status == other.status

    companion object {
        const val MAX_SYNCED_TRACKS: Int = 256
        const val STALE_AFTER_TICKS: Long = 20

        fun disconnected(
            center: Vec3,
            updatedAt: Long,
            status: RadarLinkStatus = RadarLinkStatus.NOT_LINKED,
            radarPos: BlockPos? = null,
            detectionTag: CompoundTag = CompoundTag()
        ): RadarDisplaySnapshot = RadarDisplaySnapshot(
            connected = false,
            operational = false,
            radarPos = radarPos,
            center = center,
            range = 0.0,
            detectionTag = detectionTag.copy(),
            selectedTrackId = null,
            tracks = emptyList(),
            updatedAt = updatedAt,
            status = status
        )

        fun isFresh(snapshot: RadarDisplaySnapshot?, gameTime: Long): Boolean {
            if (
                snapshot == null ||
                !snapshot.connected ||
                !snapshot.operational ||
                snapshot.range <= 0.0 ||
                snapshot.status != RadarLinkStatus.ACTIVE
            ) {
                return false
            }
            val age = gameTime - snapshot.updatedAt
            return age in 0..STALE_AFTER_TICKS
        }

        fun fromTag(tag: CompoundTag): RadarDisplaySnapshot {
            val tracksTag = tag.getList("tracks", Tag.TAG_COMPOUND.toInt())
            val tracks = buildList {
                for (index in 0 until minOf(tracksTag.size, MAX_SYNCED_TRACKS)) {
                    RadarDisplayTrack.fromTag(tracksTag.getCompound(index))?.let(::add)
                }
            }
            val connected = tag.getBoolean("connected")
            return RadarDisplaySnapshot(
                connected = connected,
                operational = if (tag.contains("operational", Tag.TAG_BYTE.toInt())) {
                    tag.getBoolean("operational")
                } else {
                    connected
                },
                radarPos = NbtUtils.readBlockPos(tag, "radarPos").orElse(null),
                center = if (tag.contains("center", Tag.TAG_COMPOUND.toInt())) {
                    tag.getCompound("center").toVec3()
                } else {
                    Vec3.ZERO
                },
                range = tag.getDouble("range").coerceAtLeast(0.0),
                detectionTag = if (tag.contains("detection", Tag.TAG_COMPOUND.toInt())) {
                    tag.getCompound("detection").copy()
                } else {
                    CompoundTag()
                },
                selectedTrackId = tag.getString("selected").takeIf { it.isNotEmpty() },
                tracks = tracks,
                updatedAt = tag.getLong("updatedAt"),
                status = RadarLinkStatus.fromTag(tag.getString("status"), connected)
            )
        }
    }
}

private fun Vec3.toTag(): CompoundTag = CompoundTag().apply {
    putDouble("x", this@toTag.x)
    putDouble("y", this@toTag.y)
    putDouble("z", this@toTag.z)
}

private fun CompoundTag.toVec3(): Vec3 = Vec3(getDouble("x"), getDouble("y"), getDouble("z"))
