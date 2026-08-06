package de.teutonstudio.ccaeroworks.compat.createradar

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess
import de.teutonstudio.ccaeroworks.display.RadarDisplaySnapshot
import de.teutonstudio.ccaeroworks.display.RadarDisplayTrack
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import net.neoforged.fml.ModList

object CreateRadarCompat {
    const val MOD_ID: String = "create_radar"
    private const val MONITOR_CLASS: String = "com.happysg.radar.block.monitor.MonitorBlockEntity"
    private const val UPDATE_INTERVAL: Long = 5

    @JvmStatic
    fun capture(dataLink: Any) {
        if (!ModList.get().isLoaded(MOD_ID)) return
        val linkEntity = dataLink as? BlockEntity ?: return
        val level = linkEntity.level ?: return
        if (level.isClientSide || level.gameTime % UPDATE_INTERVAL != 0L) return

        val sourcePos = invoke(dataLink, "getSourcePosition") as? BlockPos ?: return
        if (!level.isLoaded(sourcePos)) return
        val desk = level.getBlockEntity(sourcePos) as? ConsoleBlockEntity ?: return
        if (!AeroworksDeskAccess.hasRadarDisplay(desk)) return

        val targetPos = invoke(dataLink, "getTargetPosition") as? BlockPos
        val fallbackCenter = targetPos?.let(Vec3::atCenterOf) ?: Vec3.atCenterOf(sourcePos)
        val target = if (targetPos != null && level.isLoaded(targetPos)) {
            level.getBlockEntity(targetPos)
        } else {
            null
        }
        val monitor = target?.let(::monitorController)
        val snapshot = if (monitor == null) {
            RadarDisplaySnapshot.disconnected(fallbackCenter, level.gameTime)
        } else {
            readSnapshot(monitor, fallbackCenter, level.gameTime)
        }

        val access = desk as? RadarDeskStateAccess ?: return
        access.ccaeroworks_setRadarSnapshot(snapshot)
        desk.setChanged()
        level.sendBlockUpdated(desk.blockPos, desk.blockState, desk.blockState, 3)
    }

    private fun monitorController(target: Any): Any? {
        val controller = invoke(target, "getController") ?: target
        return controller.takeIf(::isMonitor)
    }

    private fun isMonitor(value: Any): Boolean {
        var current: Class<*>? = value.javaClass
        while (current != null) {
            if (current.name == MONITOR_CLASS) return true
            current = current.superclass
        }
        return false
    }

    private fun readSnapshot(monitor: Any, fallbackCenter: Vec3, gameTime: Long): RadarDisplaySnapshot {
        val center = invoke(monitor, "getRadarCenterPos") as? Vec3 ?: fallbackCenter
        val range = (invoke(monitor, "getRange") as? Number)?.toDouble()?.coerceAtLeast(0.0) ?: 0.0
        val connected = (invoke(monitor, "isLinked") as? Boolean) == true && range > 0.0
        val selected = invoke(monitor, "getSelectedEntity") as? String
        val tracks = if (connected) readTracks(monitor, center) else emptyList()
        return RadarDisplaySnapshot(connected, center, range, selected, tracks, gameTime)
    }

    private fun readTracks(monitor: Any, center: Vec3): List<RadarDisplayTrack> {
        val values = invoke(monitor, "getTracks") as? Iterable<*> ?: return emptyList()
        return values.mapNotNull { raw ->
            raw ?: return@mapNotNull null
            val id = (invoke(raw, "getId") as? String)?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            val position = invoke(raw, "getPosition") as? Vec3 ?: return@mapNotNull null
            val velocity = invoke(raw, "getVelocity") as? Vec3 ?: Vec3.ZERO
            RadarDisplayTrack(id, position, velocity)
        }
            .sortedBy { it.position.distanceToSqr(center) }
            .take(RadarDisplaySnapshot.MAX_SYNCED_TRACKS)
    }

    private fun invoke(instance: Any, methodName: String): Any? = runCatching {
        instance.javaClass.getMethod(methodName).invoke(instance)
    }.getOrNull()
}
