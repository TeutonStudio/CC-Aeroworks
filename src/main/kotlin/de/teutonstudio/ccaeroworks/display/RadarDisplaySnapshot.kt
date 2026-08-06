package de.teutonstudio.ccaeroworks.display

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.world.phys.Vec3
import java.util.Locale

enum class RadarLinkStatus {
    ACTIVE,
    MULTIPLE_CONTROLLERS,
    NETWORK_UNAVAILABLE,
    RADAR_NOT_LINKED,
    RADAR_NOT_LOADED,
    RADAR_NOT_RUNNING,
    INVALID_RANGE,
    API_INCOMPATIBLE,
    STALE,
    DISCONNECTED;

    companion object {
        fun fromTag(value: String, connected: Boolean): RadarLinkStatus = runCatching {
            valueOf(value.uppercase(Locale.ROOT))
        }.getOrElse {
            if (connected) ACTIVE else DISCONNECTED
        }
    }
}

enum class RadarDisplayTrackSprite {
    CONTRAPTION,
    PLAYER,
    PROJECTILE,
    ENTITY;

    companion object {
        fun fromCategory(category: Any?): RadarDisplayTrackSprite = when (
            category?.toString()?.uppercase(Locale.ROOT)
        ) {
            "VS2", "SABLE", "CONTRAPTION" -> CONTRAPTION
            "PLAYER" -> PLAYER
            "PROJECTILE" -> PROJECTILE
            else -> ENTITY
        }

        fun fromTag(value: String): RadarDisplayTrackSprite = runCatching {
            valueOf(value.uppercase(Locale.ROOT))
        }.getOrDefault(ENTITY)
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
    val center: Vec3,
    val range: Double,
    val selectedTrackId: String?,
    val tracks: List<RadarDisplayTrack>,
    val updatedAt: Long,
    val status: RadarLinkStatus = if (connected) RadarLinkStatus.ACTIVE else RadarLinkStatus.DISCONNECTED
) {
    fun toTag(): CompoundTag = CompoundTag().apply {
        putBoolean("connected", connected)
        putString("status", status.name.lowercase(Locale.ROOT))
        put("center", center.toTag())
        putDouble("range", range)
        selectedTrackId?.let { putString("selected", it) }
        putLong("updatedAt", updatedAt)
        put("tracks", ListTag().apply {
            tracks.take(MAX_SYNCED_TRACKS).forEach { add(it.toTag()) }
        })
    }

    fun contentHash(): Int = copy(updatedAt = 0L).hashCode()

    companion object {
        const val MAX_SYNCED_TRACKS: Int = 256
        const val STALE_AFTER_TICKS: Long = 20

        fun disconnected(
            center: Vec3,
            updatedAt: Long,
            status: RadarLinkStatus = RadarLinkStatus.DISCONNECTED
        ): RadarDisplaySnapshot =
            RadarDisplaySnapshot(false, center, 0.0, null, emptyList(), updatedAt, status)

        fun isFresh(snapshot: RadarDisplaySnapshot?, gameTime: Long): Boolean {
            if (
                snapshot == null ||
                !snapshot.connected ||
                snapshot.status != RadarLinkStatus.ACTIVE ||
                snapshot.range <= 0.0
            ) return false
            val age = gameTime - snapshot.updatedAt
            return age in 0..STALE_AFTER_TICKS
        }

        fun effectiveStatus(snapshot: RadarDisplaySnapshot?, gameTime: Long): RadarLinkStatus {
            snapshot ?: return RadarLinkStatus.DISCONNECTED
            if (snapshot.status == RadarLinkStatus.ACTIVE && !isFresh(snapshot, gameTime)) {
                return RadarLinkStatus.STALE
            }
            return snapshot.status
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
                center = if (tag.contains("center", Tag.TAG_COMPOUND.toInt())) {
                    tag.getCompound("center").toVec3()
                } else {
                    Vec3.ZERO
                },
                range = tag.getDouble("range").coerceAtLeast(0.0),
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
