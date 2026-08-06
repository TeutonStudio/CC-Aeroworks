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
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.phys.Vec3
import net.neoforged.fml.ModList

object CreateRadarCompat {
    const val MOD_ID: String = "create_radar"

    private const val NETWORK_CONTROLLER_CLASS: String =
        "com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity"
    private const val SELECTED_FILTERER_KEY: String = "SelectedFiltererPos"
    private const val UPDATE_INTERVAL: Long = 5

    @JvmStatic
    fun handleControllerLink(context: UseOnContext): InteractionResult? {
        if (!ModList.get().isLoaded(MOD_ID)) return null
        val player = context.player ?: return null
        if (player.isShiftKeyDown) return null

        val sourceDesk = context.level.getBlockEntity(context.clickedPos) as? ConsoleBlockEntity ?: return null
        val stack = context.itemInHand
        val selectedController = readSelectedController(itemData(stack)) ?: return null

        if (context.level.isClientSide) {
            return InteractionResult.sidedSuccess(true)
        }

        val level = context.level as? ServerLevel ?: return InteractionResult.FAIL
        if (!level.isLoaded(selectedController)) {
            clearControllerSelection(stack)
            player.displayClientMessage(
                Component.translatable("message.cc_aeroworks.radar_controller_invalid"),
                true
            )
            return InteractionResult.FAIL
        }

        val controller = level.getBlockEntity(selectedController)
        if (controller == null || !hasClass(controller, NETWORK_CONTROLLER_CLASS)) {
            clearControllerSelection(stack)
            player.displayClientMessage(
                Component.translatable("message.cc_aeroworks.radar_controller_invalid"),
                true
            )
            return InteractionResult.FAIL
        }

        val route = resolveDeskNetwork(sourceDesk)
        if (!isRoutable(route.state)) {
            clearControllerSelection(stack)
            player.displayClientMessage(Component.translatable(routeFailureKey(route.state)), true)
            return InteractionResult.FAIL
        }

        val destinations = route.desks.filter(AeroworksDeskAccess::hasRadarDisplay)
        if (destinations.isEmpty()) {
            clearControllerSelection(stack)
            player.displayClientMessage(
                Component.translatable("message.cc_aeroworks.radar_display_missing"),
                true
            )
            return InteractionResult.FAIL
        }

        val link = RadarControllerLink(
            selectedController,
            level.dimension().location().toString()
        )

        // A desk network has one controller source. Re-linking deliberately replaces
        // the previous source instead of leaving several desks to race each other.
        route.desks.forEach { desk ->
            val deskAccess = desk as? RadarDeskStateAccess ?: return@forEach
            if (deskAccess.ccaeroworks_getRadarControllerLink() != null) {
                deskAccess.ccaeroworks_setRadarControllerLink(null)
                desk.setChanged()
                level.sendBlockUpdated(desk.blockPos, desk.blockState, desk.blockState, 3)
            }
        }
        (sourceDesk as? RadarDeskStateAccess)?.ccaeroworks_setRadarControllerLink(link)
        clearControllerSelection(stack)

        sourceDesk.setChanged()
        level.sendBlockUpdated(sourceDesk.blockPos, sourceDesk.blockState, sourceDesk.blockState, 3)
        refreshDesk(sourceDesk, force = true)

        player.displayClientMessage(
            Component.translatable(
                "message.cc_aeroworks.radar_controller_linked",
                destinations.size
            ),
            true
        )
        return InteractionResult.sidedSuccess(false)
    }

    @JvmStatic
    fun refreshDesk(desk: ConsoleBlockEntity) {
        refreshDesk(desk, force = false)
    }

    private fun refreshDesk(desk: ConsoleBlockEntity, force: Boolean) {
        if (!ModList.get().isLoaded(MOD_ID)) return
        val level = desk.level as? ServerLevel ?: return
        if (!force && level.gameTime % UPDATE_INTERVAL != 0L) return

        val access = desk as? RadarDeskStateAccess ?: return
        val link = access.ccaeroworks_getRadarControllerLink() ?: return
        val route = resolveDeskNetwork(desk)
        if (!isRoutable(route.state)) return

        val destinations = route.desks.filter(AeroworksDeskAccess::hasRadarDisplay)
        if (destinations.isEmpty()) return

        val snapshot = readControllerSnapshot(level, link)
        destinations.forEach { destination ->
            val destinationAccess = destination as? RadarDeskStateAccess ?: return@forEach
            destinationAccess.ccaeroworks_setRadarSnapshot(snapshot)
            destination.setChanged()
            level.sendBlockUpdated(
                destination.blockPos,
                destination.blockState,
                destination.blockState,
                3
            )
        }
    }

    private fun readControllerSnapshot(
        level: ServerLevel,
        link: RadarControllerLink
    ): RadarDisplaySnapshot {
        val fallbackCenter = Vec3.atCenterOf(link.position)
        if (link.dimension != level.dimension().location().toString() || !level.isLoaded(link.position)) {
            return RadarDisplaySnapshot.disconnected(fallbackCenter, level.gameTime)
        }

        val controller = level.getBlockEntity(link.position)
        if (controller == null || !hasClass(controller, NETWORK_CONTROLLER_CLASS)) {
            return RadarDisplaySnapshot.disconnected(fallbackCenter, level.gameTime)
        }

        val radar = invokeDeclared(controller, "getRadar", level)
            ?: readField(controller, "radarCache")
            ?: (readField(controller, "radarPosCache") as? BlockPos)
                ?.takeIf(level::isLoaded)
                ?.let(level::getBlockEntity)
            ?: return RadarDisplaySnapshot.disconnected(fallbackCenter, level.gameTime)

        val center = when (val worldPosition = invoke(radar, "getWorldPos")) {
            is BlockPos -> Vec3.atCenterOf(worldPosition)
            is Vec3 -> worldPosition
            else -> fallbackCenter
        }
        val range = (invoke(radar, "getRange") as? Number)?.toDouble()?.coerceAtLeast(0.0) ?: 0.0
        val connected = (invoke(radar, "isRunning") as? Boolean) == true && range > 0.0
        val selected = readField(controller, "activeTrackCache")
            ?.let { invoke(it, "getId") as? String }
        val tracks = if (connected) readTracks(radar, center) else emptyList()

        return RadarDisplaySnapshot(
            connected = connected,
            center = center,
            range = range,
            selectedTrackId = selected,
            tracks = tracks,
            updatedAt = level.gameTime
        )
    }

    private fun resolveDeskNetwork(sourceDesk: ConsoleBlockEntity): RadarDeskNetwork {
        val level = sourceDesk.level ?: return RadarDeskNetwork(ConsoleNetworkState.NONE, listOf(sourceDesk))
        val network = ConsoleMultiblockManager.resolve(level, sourceDesk.blockPos)
        val desks = network.members.map { it.desk }.ifEmpty { listOf(sourceDesk) }
        return RadarDeskNetwork(network.state, desks)
    }

    private fun isRoutable(state: ConsoleNetworkState): Boolean =
        state == ConsoleNetworkState.ACTIVE || state == ConsoleNetworkState.NONE

    private fun routeFailureKey(state: ConsoleNetworkState): String = when (state) {
        ConsoleNetworkState.CONFLICT -> "message.cc_aeroworks.console_conflict"
        ConsoleNetworkState.TOO_LARGE -> "message.cc_aeroworks.console_too_large"
        ConsoleNetworkState.PARTIALLY_LOADED -> "message.cc_aeroworks.console_partially_loaded"
        else -> "message.cc_aeroworks.radar_display_missing"
    }

    private fun itemData(stack: ItemStack): CompoundTag? =
        stack.get(DataComponents.CUSTOM_DATA)?.copyTag()

    private fun readSelectedController(selection: CompoundTag?): BlockPos? {
        selection ?: return null
        if (selection.contains(SELECTED_FILTERER_KEY, Tag.TAG_LONG.toInt())) {
            return BlockPos.of(selection.getLong(SELECTED_FILTERER_KEY))
        }
        if (!selection.contains(SELECTED_FILTERER_KEY, Tag.TAG_COMPOUND.toInt())) return null

        val position = selection.getCompound(SELECTED_FILTERER_KEY)
        fun coordinate(upper: String, lower: String): Int =
            if (position.contains(upper)) position.getInt(upper) else position.getInt(lower)
        return BlockPos(
            coordinate("X", "x"),
            coordinate("Y", "y"),
            coordinate("Z", "z")
        )
    }

    private fun clearControllerSelection(stack: ItemStack) {
        val data = itemData(stack) ?: return
        if (!data.contains(SELECTED_FILTERER_KEY)) return
        CustomData.update(DataComponents.CUSTOM_DATA, stack) { selection ->
            selection.remove(SELECTED_FILTERER_KEY)
        }
    }

    private fun readTracks(radar: Any, center: Vec3): List<RadarDisplayTrack> {
        val values = invoke(radar, "getTracks") as? Iterable<*> ?: return emptyList()
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

    private fun invokeDeclared(instance: Any, methodName: String, vararg arguments: Any): Any? {
        var current: Class<*>? = instance.javaClass
        while (current != null) {
            val type = current
            val method = type.declaredMethods.firstOrNull { candidate ->
                candidate.name == methodName &&
                    candidate.parameterCount == arguments.size &&
                    candidate.parameterTypes.zip(arguments).all { (type, argument) ->
                        type.isAssignableFrom(argument.javaClass)
                    }
            }
            if (method != null) {
                return runCatching {
                    method.trySetAccessible()
                    method.invoke(instance, *arguments)
                }.getOrNull()
            }
            current = type.superclass
        }
        return null
    }

    private fun readField(instance: Any, fieldName: String): Any? {
        var current: Class<*>? = instance.javaClass
        while (current != null) {
            val type = current
            val field = runCatching { type.getDeclaredField(fieldName) }.getOrNull()
            if (field != null) {
                return runCatching {
                    field.trySetAccessible()
                    field.get(instance)
                }.getOrNull()
            }
            current = type.superclass
        }
        return null
    }

    private fun hasClass(value: Any, className: String): Boolean {
        var current: Class<*>? = value.javaClass
        while (current != null) {
            val type = current
            if (type.name == className) return true
            current = type.superclass
        }
        return false
    }

    private data class RadarDeskNetwork(
        val state: ConsoleNetworkState,
        val desks: List<ConsoleBlockEntity>
    )
}
