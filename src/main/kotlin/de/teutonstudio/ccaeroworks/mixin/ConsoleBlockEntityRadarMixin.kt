package de.teutonstudio.ccaeroworks.mixin

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.createradar.CreateRadarCompat
import de.teutonstudio.ccaeroworks.compat.createradar.RadarDeskStateAccess
import de.teutonstudio.ccaeroworks.display.RadarDisplaySnapshot
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
        CreateRadarCompat.refreshDesk(this as ConsoleBlockEntity)
    }

    @Inject(method = ["write"], at = [At("TAIL")])
    private fun ccaeroworks_writeRadarSnapshot(
        tag: CompoundTag,
        registries: HolderLookup.Provider,
        clientPacket: Boolean,
        callback: CallbackInfo
    ) {
        if (!clientPacket) return
        ccaeroworks_radarSnapshot?.let {
            tag.put(RADAR_NBT_KEY, it.toTag())
        } ?: tag.remove(RADAR_NBT_KEY)
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
        ccaeroworks_radarSnapshot =
            if (tag.contains(RADAR_NBT_KEY, Tag.TAG_COMPOUND.toInt())) {
                RadarDisplaySnapshot.fromTag(tag.getCompound(RADAR_NBT_KEY), clientTick)
            } else {
                null
            }

        ccaeroworks_logClientRadarSnapshot(desk, clientTick, ccaeroworks_radarSnapshot)
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
            "${snapshot.status}:${snapshot.radarPos}:${snapshot.tracks.size}"
        }
        if (diagnostic == ccaeroworks_lastClientRadarDiagnostic) return
        ccaeroworks_lastClientRadarDiagnostic = diagnostic

        CCAeroworks.LOGGER.info(
            "[CC-Aeroworks] Radar client snapshot desk={} status={} radar={} tracks={} serverTick={} clientTick={}",
            desk.blockPos,
            snapshot?.status,
            snapshot?.radarPos,
            snapshot?.tracks?.size ?: 0,
            snapshot?.updatedAt ?: -1L,
            clientTick
        )
    }
}
