package de.teutonstudio.ccaeroworks.compat.createradar

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess
import de.teutonstudio.ccaeroworks.display.RadarDisplaySnapshot
import de.teutonstudio.ccaeroworks.display.RadarDisplayTrack
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import net.neoforged.fml.ModList

object CreateRadarCompat {
    const val MOD_ID: String = "create_radar"
    private const val MONITOR_CLASS: String = "com.happysg.radar.block.monitor.MonitorBlockEntity"
    private const val DATA_LINK_CLASS: String = "com.happysg.radar.block.datalink.DataLinkBlockEntity"
    private const val SELECTED_MONITOR_KEY: String = "CCAeroworksRadarMonitor"
    private const val SELECTED_MONITOR_DIMENSION_KEY: String = "CCAeroworksRadarMonitorDimension"
    private const val UPDATE_INTERVAL: Long = 5

    @JvmStatic
    fun handleDataLinkUse(context: UseOnContext): InteractionResult? {
        if (!ModList.get().isLoaded(MOD_ID)) return null
        val player = context.player ?: return null
        val level = context.level
        val selection = player.persistentData

        if (player.isShiftKeyDown && selection.contains(SELECTED_MONITOR_KEY)) {
            clearMonitorSelection(selection)
            if (!level.isClientSide) {
                player.displayClientMessage(
                    Component.translatable("message.cc_aeroworks.radar_link_cleared"),
                    true
                )
            }
            return InteractionResult.sidedSuccess(level.isClientSide)
        }

        val clickedEntity = level.getBlockEntity(context.clickedPos)
        if (clickedEntity != null && isMonitor(clickedEntity)) {
            val controller = monitorController(clickedEntity) as? BlockEntity ?: clickedEntity
            selection.putLong(SELECTED_MONITOR_KEY, controller.blockPos.asLong())
            selection.putString(SELECTED_MONITOR_DIMENSION_KEY, level.dimension().location().toString())
            if (!level.isClientSide) {
                player.displayClientMessage(
                    Component.translatable("message.cc_aeroworks.radar_monitor_selected"),
                    true
                )
            }
            return InteractionResult.sidedSuccess(level.isClientSide)
        }

        val desk = clickedEntity as? ConsoleBlockEntity ?: return null
        if (!AeroworksDeskAccess.hasRadarDisplay(desk)) return null

        if (!selection.contains(SELECTED_MONITOR_KEY)) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                    Component.translatable("message.cc_aeroworks.radar_select_monitor_first"),
                    true
                )
            }
            return InteractionResult.sidedSuccess(level.isClientSide)
        }

        val selectedDimension = selection.getString(SELECTED_MONITOR_DIMENSION_KEY)
        val currentDimension = level.dimension().location().toString()
        val monitorPos = BlockPos.of(selection.getLong(SELECTED_MONITOR_KEY))
        val monitor = if (selectedDimension == currentDimension && level.isLoaded(monitorPos)) {
            level.getBlockEntity(monitorPos)
        } else {
            null
        }
        if (monitor == null || !isMonitor(monitor)) {
            clearMonitorSelection(selection)
            if (!level.isClientSide) {
                player.displayClientMessage(
                    Component.translatable("message.cc_aeroworks.radar_monitor_invalid"),
                    true
                )
            }
            return InteractionResult.sidedSuccess(level.isClientSide)
        }

        val dataLinkItem = context.itemInHand.item as? BlockItem ?: return null
        val placeContext = BlockPlaceContext(context)
        val placedPos = placeContext.clickedPos
        val placement = dataLinkItem.place(placeContext)
        if (!placement.consumesAction()) return placement

        if (level.isClientSide) {
            clearMonitorSelection(selection)
            return placement
        }

        val dataLink = level.getBlockEntity(placedPos)
        val configured = dataLink != null && isDataLink(dataLink) && invokeBlockPos(dataLink, "target", monitorPos)
        if (!configured) {
            player.displayClientMessage(
                Component.translatable("message.cc_aeroworks.radar_link_failed"),
                true
            )
            return InteractionResult.FAIL
        }

        clearMonitorSelection(selection)
        dataLink.setChanged()
        level.sendBlockUpdated(placedPos, dataLink.blockState, dataLink.blockState, 3)
        player.displayClientMessage(
            Component.translatable("message.cc_aeroworks.radar_link_created"),
            true
        )
        return placement
    }

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

    private fun clearMonitorSelection(selection: net.minecraft.nbt.CompoundTag) {
        selection.remove(SELECTED_MONITOR_KEY)
        selection.remove(SELECTED_MONITOR_DIMENSION_KEY)
    }

    private fun monitorController(target: Any): Any? {
        val controller = invoke(target, "getController") ?: target
        return controller.takeIf(::isMonitor)
    }

    private fun isMonitor(value: Any): Boolean = hasClass(value, MONITOR_CLASS)

    private fun isDataLink(value: Any): Boolean = hasClass(value, DATA_LINK_CLASS)

    private fun hasClass(value: Any, className: String): Boolean {
        var current: Class<*>? = value.javaClass
        while (current != null) {
            if (current.name == className) return true
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

    private fun invokeBlockPos(instance: Any, methodName: String, position: BlockPos): Boolean = runCatching {
        instance.javaClass.getMethod(methodName, BlockPos::class.java).invoke(instance, position)
        true
    }.getOrDefault(false)
}
