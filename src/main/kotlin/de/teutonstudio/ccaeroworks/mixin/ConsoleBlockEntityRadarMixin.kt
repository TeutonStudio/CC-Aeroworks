package de.teutonstudio.ccaeroworks.mixin

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.compat.createradar.CreateRadarCompat
import de.teutonstudio.ccaeroworks.compat.createradar.RadarControllerLink
import de.teutonstudio.ccaeroworks.compat.createradar.RadarDeskStateAccess
import de.teutonstudio.ccaeroworks.compat.createradar.RadarRasterCache
import de.teutonstudio.ccaeroworks.display.DeskDisplayPixels
import de.teutonstudio.ccaeroworks.display.RadarDisplayRaster
import de.teutonstudio.ccaeroworks.display.RadarDisplaySnapshot
import de.teutonstudio.ccaeroworks.display.RadarDisplayType
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

private const val RADAR_NBT_KEY = "CCAeroworksRadarDisplay"
private const val RADAR_CONTROLLER_NBT_KEY = "CCAeroworksRadarController"

@Mixin(value = [ConsoleBlockEntity::class], remap = false)
abstract class ConsoleBlockEntityRadarMixin : RadarDeskStateAccess {
    @Unique
    private var ccaeroworks_radarControllerLink: RadarControllerLink? = null

    @Unique
    private var ccaeroworks_radarSnapshot: RadarDisplaySnapshot? = null

    @Unique
    private var ccaeroworks_smallRadarCache: RadarRasterCache? = null

    @Unique
    private var ccaeroworks_largeRadarCache: RadarRasterCache? = null

    override fun ccaeroworks_getRadarControllerLink(): RadarControllerLink? =
        ccaeroworks_radarControllerLink

    override fun ccaeroworks_setRadarControllerLink(link: RadarControllerLink?) {
        if (ccaeroworks_radarControllerLink == link) return
        ccaeroworks_radarControllerLink = link
        if (link == null) ccaeroworks_radarSnapshot = null
        ccaeroworks_clearRadarCaches()
    }

    override fun ccaeroworks_getRadarSnapshot(): RadarDisplaySnapshot? = ccaeroworks_radarSnapshot

    override fun ccaeroworks_setRadarSnapshot(snapshot: RadarDisplaySnapshot?) {
        ccaeroworks_radarSnapshot = snapshot
        ccaeroworks_clearRadarCaches()
    }

    @Unique
    private fun ccaeroworks_clearRadarCaches() {
        ccaeroworks_smallRadarCache = null
        ccaeroworks_largeRadarCache = null
    }

    override fun ccaeroworks_getRadarPixels(type: RadarDisplayType, gameTime: Long): DeskDisplayPixels {
        val snapshot = ccaeroworks_radarSnapshot
        val width = type.displayType.pixelWidth
        val height = type.displayType.pixelHeight
        val fresh = RadarDisplayRaster.isFresh(snapshot, gameTime)
        val current = when (type) {
            RadarDisplayType.SMALL -> ccaeroworks_smallRadarCache
            RadarDisplayType.LARGE -> ccaeroworks_largeRadarCache
        }
        if (
            current != null &&
            current.snapshot === snapshot &&
            current.width == width &&
            current.height == height &&
            current.fresh == fresh
        ) {
            return current.pixels
        }

        val rendered = RadarDisplayRaster.render(type, snapshot, gameTime)
        val next = RadarRasterCache(snapshot, width, height, fresh, rendered)
        when (type) {
            RadarDisplayType.SMALL -> ccaeroworks_smallRadarCache = next
            RadarDisplayType.LARGE -> ccaeroworks_largeRadarCache = next
        }
        return rendered
    }

    @Inject(method = ["tick"], at = [At("TAIL")], require = 0)
    private fun ccaeroworks_refreshLinkedRadar(callback: CallbackInfo) {
        CreateRadarCompat.refreshDesk(this as ConsoleBlockEntity)
    }

    @Inject(method = ["write"], at = [At("TAIL")])
    private fun ccaeroworks_writeRadarState(
        tag: CompoundTag,
        registries: HolderLookup.Provider,
        clientPacket: Boolean,
        callback: CallbackInfo
    ) {
        ccaeroworks_radarControllerLink?.let {
            tag.put(RADAR_CONTROLLER_NBT_KEY, it.toTag())
        } ?: tag.remove(RADAR_CONTROLLER_NBT_KEY)

        if (clientPacket) {
            ccaeroworks_radarSnapshot?.let {
                tag.put(RADAR_NBT_KEY, it.toTag())
            } ?: tag.remove(RADAR_NBT_KEY)
        }
    }

    @Inject(method = ["read"], at = [At("TAIL")])
    private fun ccaeroworks_readRadarState(
        tag: CompoundTag,
        registries: HolderLookup.Provider,
        clientPacket: Boolean,
        callback: CallbackInfo
    ) {
        ccaeroworks_radarControllerLink =
            if (tag.contains(RADAR_CONTROLLER_NBT_KEY, Tag.TAG_COMPOUND.toInt())) {
                RadarControllerLink.fromTag(tag.getCompound(RADAR_CONTROLLER_NBT_KEY))
            } else {
                null
            }

        if (clientPacket) {
            ccaeroworks_radarSnapshot =
                if (tag.contains(RADAR_NBT_KEY, Tag.TAG_COMPOUND.toInt())) {
                    RadarDisplaySnapshot.fromTag(tag.getCompound(RADAR_NBT_KEY))
                } else {
                    null
                }
        }
        ccaeroworks_clearRadarCaches()
    }
}
