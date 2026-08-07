package de.teutonstudio.ccaeroworks.display

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.nbt.Tag
import java.util.Locale

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

/**
 * Synchronized state needed to hydrate a native Create: Radars MonitorBlockEntity
 * on the client. Radar tracks remain in Create: Radars' own serialized format;
 * CC-Aeroworks deliberately does not redefine their fields or sprite mapping.
 */
data class RadarDisplaySnapshot(
    val connected: Boolean,
    val operational: Boolean,
    val radarPos: BlockPos?,
    val range: Double,
    val detectionTag: CompoundTag,
    val selectedTrackId: String?,
    /** Compound returned by RadarTrackUtil.serializeNBTList(Collection<RadarTrack>). */
    val nativeTracks: CompoundTag,
    val trackCount: Int,
    val updatedAt: Long,
    val status: RadarLinkStatus,
    /**
     * Local client game tick at which this snapshot was received. This value is
     * intentionally not serialized: server and client gameTime are independent
     * clocks and must never be compared for display freshness.
     */
    val receivedAtClientTick: Long = -1L
) {
    fun toTag(): CompoundTag = CompoundTag().apply {
        putBoolean("connected", connected)
        putBoolean("operational", operational)
        radarPos?.let { put("radarPos", NbtUtils.writeBlockPos(it)) }
        putDouble("range", range)
        put("detection", detectionTag.copy())
        selectedTrackId?.let { putString("selected", it) }
        put("tracks", nativeTracks.copy())
        putInt("trackCount", trackCount)
        putLong("updatedAt", updatedAt)
        putString("status", status.name.lowercase(Locale.ROOT))
    }

    fun sameContentAs(other: RadarDisplaySnapshot?): Boolean =
        other != null &&
            connected == other.connected &&
            operational == other.operational &&
            radarPos == other.radarPos &&
            range == other.range &&
            detectionTag == other.detectionTag &&
            selectedTrackId == other.selectedTrackId &&
            nativeTracks == other.nativeTracks &&
            trackCount == other.trackCount &&
            status == other.status

    companion object {
        const val MAX_SYNCED_TRACKS: Int = 256
        const val STALE_AFTER_TICKS: Long = 20

        fun disconnected(
            updatedAt: Long,
            status: RadarLinkStatus = RadarLinkStatus.NOT_LINKED,
            radarPos: BlockPos? = null,
            detectionTag: CompoundTag = CompoundTag()
        ): RadarDisplaySnapshot = RadarDisplaySnapshot(
            connected = false,
            operational = false,
            radarPos = radarPos,
            range = 0.0,
            detectionTag = detectionTag.copy(),
            selectedTrackId = null,
            nativeTracks = CompoundTag(),
            trackCount = 0,
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

            val freshnessTick = snapshot.receivedAtClientTick
                .takeIf { it >= 0L }
                ?: snapshot.updatedAt
            val age = gameTime - freshnessTick
            return age in 0..STALE_AFTER_TICKS
        }

        fun fromTag(tag: CompoundTag, receivedAtClientTick: Long = -1L): RadarDisplaySnapshot {
            val connected = tag.getBoolean("connected")
            val nativeTracks = if (tag.contains("tracks", Tag.TAG_COMPOUND.toInt())) {
                tag.getCompound("tracks").copy()
            } else {
                CompoundTag()
            }
            val serializedTrackCount = nativeTracks
                .getList("tracks", Tag.TAG_COMPOUND.toInt())
                .size
            return RadarDisplaySnapshot(
                connected = connected,
                operational = if (tag.contains("operational", Tag.TAG_BYTE.toInt())) {
                    tag.getBoolean("operational")
                } else {
                    connected
                },
                radarPos = NbtUtils.readBlockPos(tag, "radarPos").orElse(null),
                range = tag.getDouble("range").coerceAtLeast(0.0),
                detectionTag = if (tag.contains("detection", Tag.TAG_COMPOUND.toInt())) {
                    tag.getCompound("detection").copy()
                } else {
                    CompoundTag()
                },
                selectedTrackId = tag.getString("selected").takeIf { it.isNotEmpty() },
                nativeTracks = nativeTracks,
                trackCount = if (tag.contains("trackCount", Tag.TAG_INT.toInt())) {
                    tag.getInt("trackCount").coerceAtLeast(0)
                } else {
                    serializedTrackCount
                },
                updatedAt = tag.getLong("updatedAt"),
                status = RadarLinkStatus.fromTag(tag.getString("status"), connected),
                receivedAtClientTick = receivedAtClientTick
            )
        }
    }
}
