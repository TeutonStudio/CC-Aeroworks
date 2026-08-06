package de.teutonstudio.ccaeroworks.compat.createradar

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess
import de.teutonstudio.ccaeroworks.display.RadarDisplaySnapshot
import de.teutonstudio.ccaeroworks.display.RadarDisplayTrack
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
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

    private val NATIVE_SELECTION_KEYS: Set<String> = setOf(
        "SelectedFiltererPos",
        "SelectedMountPos",
        "SelectedYawPos",
        "SelectedPitchPos",
        "SelectedFiringPos"
    )

    @JvmStatic
    fun handleDataLinkUse(context: UseOnContext): InteractionResult? {
        if (!ModList.get().isLoaded(MOD_ID)) return null
        val player = context.player ?: return null
        val level = context.level
        val stack = context.itemInHand
        val existingSelection = itemData(stack)

        // Create: Radars owns every interaction after one of its native first-click
        // selections. In particular, Network Controller -> Monitor must reach the
        // original DataLinkBlockItem instead of being mistaken for our monitor-first mode.
        if (hasNativeSelection(existingSelection)) {
            clearMonitorSelection(stack)
            return null
        }

        if (player.isShiftKeyDown && hasMonitorSelection(existingSelection)) {
            clearMonitorSelection(stack)
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
            CustomData.update(DataComponents.CUSTOM_DATA, stack) { selection ->
                selection.putLong(SELECTED_MONITOR_KEY, controller.blockPos.asLong())
                selection.putString(SELECTED_MONITOR_DIMENSION_KEY, level.dimension().location().toString())
            }
            if (!level.isClientSide) {
                player.displayClientMessage(
                    Component.translatable("message.cc_aeroworks.radar_monitor_selected"),
                    true
                )
            }
            return InteractionResult.sidedSuccess(level.isClientSide)
        }

        val sourceDesk = clickedEntity as? ConsoleBlockEntity ?: return null
        val route = resolveRadarRoute(sourceDesk)
        if (!isRoutable(route.state)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable(routeFailureKey(route.state)), true)
            }
            return InteractionResult.sidedSuccess(level.isClientSide)
        }
        if (route.destinations.isEmpty()) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                    Component.translatable("message.cc_aeroworks.radar_route_missing"),
                    true
                )
            }
            return InteractionResult.sidedSuccess(level.isClientSide)
        }
        if (route.destinations.size > 1) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                    Component.translatable("message.cc_aeroworks.radar_route_ambiguous"),
                    true
                )
            }
            return InteractionResult.sidedSuccess(level.isClientSide)
        }

        val selection = itemData(stack)
        if (!hasMonitorSelection(selection)) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                    Component.translatable("message.cc_aeroworks.radar_select_monitor_first"),
                    true
                )
            }
            return InteractionResult.sidedSuccess(level.isClientSide)
        }

        val selectedDimension = selection!!.getString(SELECTED_MONITOR_DIMENSION_KEY)
        val currentDimension = level.dimension().location().toString()
        val monitorPos = BlockPos.of(selection.getLong(SELECTED_MONITOR_KEY))
        val monitor = if (selectedDimension == currentDimension && level.isLoaded(monitorPos)) {
            level.getBlockEntity(monitorPos)
        } else {
            null
        }
        if (monitor == null || !isMonitor(monitor)) {
            clearMonitorSelection(stack)
            if (!level.isClientSide) {
                player.displayClientMessage(
                    Component.translatable("message.cc_aeroworks.radar_monitor_invalid"),
                    true
                )
            }
            return InteractionResult.sidedSuccess(level.isClientSide)
        }

        val dataLinkItem = stack.item as? BlockItem ?: return null
        val placeContext = BlockPlaceContext(context)
        val placedPos = placeContext.clickedPos
        val placement = dataLinkItem.place(placeContext)
        if (!placement.consumesAction()) return placement

        if (level.isClientSide) {
            clearMonitorSelection(stack)
            return placement
        }

        val dataLink = level.getBlockEntity(placedPos)?.takeIf(::isDataLink)
        if (dataLink == null) {
            level.removeBlock(placedPos, false)
            if (!player.abilities.instabuild) stack.grow(1)
            player.displayClientMessage(
                Component.translatable("message.cc_aeroworks.radar_link_failed"),
                true
            )
            return InteractionResult.FAIL
        }

        val configured = invokeBlockPos(dataLink, "target", monitorPos) &&
            invoke(dataLink, "getSourcePosition") == sourceDesk.blockPos &&
            invoke(dataLink, "getTargetPosition") == monitorPos
        if (!configured) {
            level.removeBlock(placedPos, false)
            if (!player.abilities.instabuild) stack.grow(1)
            player.displayClientMessage(
                Component.translatable("message.cc_aeroworks.radar_link_failed"),
                true
            )
            return InteractionResult.FAIL
        }

        clearMonitorSelection(stack)
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
        val sourceDesk = level.getBlockEntity(sourcePos) as? ConsoleBlockEntity ?: return
        val route = resolveRadarRoute(sourceDesk)
        if (!isRoutable(route.state)) return
        val destination = route.destinations.singleOrNull() ?: return

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

        val access = destination as? RadarDeskStateAccess ?: return
        access.ccaeroworks_setRadarSnapshot(snapshot)
        destination.setChanged()
        level.sendBlockUpdated(destination.blockPos, destination.blockState, destination.blockState, 3)
    }

    private fun resolveRadarRoute(sourceDesk: ConsoleBlockEntity): RadarRouteResolution {
        val level = sourceDesk.level ?: return RadarRouteResolution(ConsoleNetworkState.NONE, emptyList())
        val network = ConsoleMultiblockManager.resolve(level, sourceDesk.blockPos)
        val destinations = if (isRoutable(network.state)) {
            network.members.map { it.desk }.filter(AeroworksDeskAccess::hasRadarDisplay)
        } else {
            emptyList()
        }
        return RadarRouteResolution(network.state, destinations)
    }

    private fun isRoutable(state: ConsoleNetworkState): Boolean =
        state == ConsoleNetworkState.ACTIVE || state == ConsoleNetworkState.NONE

    private fun routeFailureKey(state: ConsoleNetworkState): String = when (state) {
        ConsoleNetworkState.CONFLICT -> "message.cc_aeroworks.console_conflict"
        ConsoleNetworkState.TOO_LARGE -> "message.cc_aeroworks.console_too_large"
        ConsoleNetworkState.PARTIALLY_LOADED -> "message.cc_aeroworks.console_partially_loaded"
        else -> "message.cc_aeroworks.radar_route_missing"
    }

    private fun itemData(stack: ItemStack): CompoundTag? =
        stack.get(DataComponents.CUSTOM_DATA)?.copyTag()

    private fun hasNativeSelection(selection: CompoundTag?): Boolean =
        selection != null && NATIVE_SELECTION_KEYS.any { key -> selection.contains(key) }

    private fun hasMonitorSelection(selection: CompoundTag?): Boolean =
        selection != null &&
            selection.contains(SELECTED_MONITOR_KEY) &&
            selection.contains(SELECTED_MONITOR_DIMENSION_KEY)

    private fun clearMonitorSelection(stack: ItemStack) {
        val selection = itemData(stack) ?: return
        if (!hasMonitorSelection(selection)) return
        CustomData.update(DataComponents.CUSTOM_DATA, stack) { data ->
            data.remove(SELECTED_MONITOR_KEY)
            data.remove(SELECTED_MONITOR_DIMENSION_KEY)
        }
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

    private data class RadarRouteResolution(
        val state: ConsoleNetworkState,
        val destinations: List<ConsoleBlockEntity>
    )
}
