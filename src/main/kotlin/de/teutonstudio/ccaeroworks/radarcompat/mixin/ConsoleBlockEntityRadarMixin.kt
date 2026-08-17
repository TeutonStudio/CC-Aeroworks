package de.teutonstudio.ccaeroworks.radarcompat.mixin

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.radarcompat.createradar.CreateRadarCompat
import de.teutonstudio.ccaeroworks.radarcompat.access.RadarDeskStateAccess
import de.teutonstudio.ccaeroworks.radarcompat.createradar.RadarTrace
import de.teutonstudio.ccaeroworks.radarcompat.display.RadarDisplaySnapshot
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

private const val RADAR_NBT_KEY = "CCAeroworksRadarDisplay"

@Mixin(value = [ConsoleBlockEntity::class], remap = false)
abstract class ConsoleBlockEntityRadarMixin : RadarDeskStateAccess {
    @Unique
    private var ccaeroworks_radarSnapshot: RadarDisplaySnapshot? = null

    @Unique
    private var ccaeroworks_lastClientRadarDiagnostic: String? = null

    override fun ccaeroworks_getRadarSnapshot(): RadarDisplaySnapshot? = ccaeroworks_radarSnapshot

    override fun ccaeroworks_setRadarSnapshot(snapshot: RadarDisplaySnapshot?) {
        ccaeroworks_radarSnapshot = snapshot
    }

    @Inject(method = ["tick"], at = [At("TAIL")])
    private fun ccaeroworks_refreshNativeRadarEndpoint(callback: CallbackInfo) {
        val desk = this as ConsoleBlockEntity
        RadarTrace.periodic(
            "M00_DESK_TICK",
            desk.level,
            desk.blockPos,
            20L,
            "ConsoleBlockEntityRadarMixin tick hook alive; snapshot=${ccaeroworks_snapshotSummary(ccaeroworks_radarSnapshot)}"
        )
        CreateRadarCompat.refreshDesk(desk)
    }

    @Inject(method = ["write"], at = [At("TAIL")])
    private fun ccaeroworks_writeRadarSnapshot(
        tag: CompoundTag,
        registries: HolderLookup.Provider,
        clientPacket: Boolean,
        callback: CallbackInfo
    ) {
        if (!clientPacket) return
        val desk = this as ConsoleBlockEntity
        RadarTrace.event(
            "N10_SERVER_WRITE_ENTER",
            desk.level,
            desk.blockPos,
            "clientPacket=true outerKeysBefore=${tag.allKeys.sorted()} snapshot=${ccaeroworks_snapshotSummary(ccaeroworks_radarSnapshot)}"
        )

        ccaeroworks_radarSnapshot?.let {
            val payload = it.toTag()
            tag.put(RADAR_NBT_KEY, payload)
            RadarTrace.event(
                "N11_SERVER_WRITE_PAYLOAD",
                desk.level,
                desk.blockPos,
                "storedKey=$RADAR_NBT_KEY payload=${RadarTrace.tag(payload)} outerKeysAfter=${tag.allKeys.sorted()}"
            )
        } ?: run {
            tag.remove(RADAR_NBT_KEY)
            RadarTrace.event(
                "N11_SERVER_WRITE_NO_PAYLOAD",
                desk.level,
                desk.blockPos,
                "snapshot=null removedKey=$RADAR_NBT_KEY outerKeysAfter=${tag.allKeys.sorted()}"
            )
        }
    }

    @Inject(method = ["read"], at = [At("TAIL")])
    private fun ccaeroworks_readRadarSnapshot(
        tag: CompoundTag,
        registries: HolderLookup.Provider,
        clientPacket: Boolean,
        callback: CallbackInfo
    ) {
        if (!clientPacket) return

        val desk = this as ConsoleBlockEntity
        val clientTick = desk.level?.gameTime ?: -1L
        val hasPayload = tag.contains(RADAR_NBT_KEY, Tag.TAG_COMPOUND.toInt())
        val rawPayload = if (hasPayload) tag.getCompound(RADAR_NBT_KEY) else null
        RadarTrace.event(
            "N20_CLIENT_READ_ENTER",
            desk.level,
            desk.blockPos,
            "clientPacket=true clientTick=$clientTick outerKeys=${tag.allKeys.sorted()} hasRadarPayload=$hasPayload raw=${RadarTrace.tag(rawPayload)}"
        )

        ccaeroworks_radarSnapshot = if (rawPayload != null) {
            RadarDisplaySnapshot.fromTag(rawPayload, clientTick)
        } else {
            null
        }

        val decoded = ccaeroworks_radarSnapshot
        val fresh = RadarDisplaySnapshot.isFresh(decoded, clientTick)
        val freshnessAge = decoded?.let {
            val base = it.receivedAtClientTick.takeIf { tick -> tick >= 0L } ?: it.updatedAt
            clientTick - base
        }
        RadarTrace.event(
            "N21_CLIENT_READ_DECODED",
            desk.level,
            desk.blockPos,
            "decoded=${ccaeroworks_snapshotSummary(decoded)} isFresh=$fresh freshnessAge=$freshnessAge nativeTracks=${RadarTrace.tag(decoded?.nativeTracks)}"
        )

        ccaeroworks_logClientRadarSnapshot(desk, clientTick, decoded)
    }

    @Unique
    private fun ccaeroworks_snapshotSummary(snapshot: RadarDisplaySnapshot?): String = if (snapshot == null) {
        "null"
    } else {
        "status=${snapshot.status},connected=${snapshot.connected},operational=${snapshot.operational},radar=${snapshot.radarPos}," +
            "range=${snapshot.range},tracks=${snapshot.trackCount},selected=${snapshot.selectedTrackId}," +
            "serverTick=${snapshot.updatedAt},clientReceipt=${snapshot.receivedAtClientTick}"
    }

    @Unique
    private fun ccaeroworks_logClientRadarSnapshot(
        desk: ConsoleBlockEntity,
        clientTick: Long,
        snapshot: RadarDisplaySnapshot?
    ) {
        val diagnostic = if (snapshot == null) {
            "NONE"
        } else {
            "${snapshot.status}:${snapshot.radarPos}:${snapshot.trackCount}"
        }
        if (diagnostic == ccaeroworks_lastClientRadarDiagnostic) return
        ccaeroworks_lastClientRadarDiagnostic = diagnostic

        CCAeroworks.LOGGER.info(
            "[CC-Aeroworks] Radar client snapshot desk={} status={} radar={} tracks={} serverTick={} clientTick={}",
            desk.blockPos,
            snapshot?.status,
            snapshot?.radarPos,
            snapshot?.trackCount ?: 0,
            snapshot?.updatedAt ?: -1L,
            clientTick
        )
    }
}
