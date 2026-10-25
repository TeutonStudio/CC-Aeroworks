package de.teutonstudio.ccaeroworks.ctfcompat.control

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import createtrainfunctionality.DashboardControl
import createtrainfunctionality.DashboardControllerBlockEntity
import de.teutonstudio.ccaeroworks.ctfcompat.DashboardControllerIdentityAccess
import de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyRuntime
import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level

enum class DashboardSourceStatus {
    AVAILABLE,
    UNAVAILABLE,
    AMBIGUOUS,
    FREQUENCY,
    INCOMPLETE
}

data class DashboardControllerResolution(
    val status: DashboardSourceStatus,
    val row: List<BlockPos>,
    val controller: DashboardControllerBlockEntity? = null
) {
    val controllerId: UUID?
        get() = (controller as? DashboardControllerIdentityAccess)?.ccaeroworks_getDashboardControllerId()
}

object DashboardControllerLookup {
    fun resolve(level: Level, segment: BlockPos): DashboardControllerResolution {
        if (!level.isLoaded(segment) || ConsoleAssemblyRuntime.geometry(level.getBlockState(segment)) == null) {
            return DashboardControllerResolution(DashboardSourceStatus.UNAVAILABLE, emptyList())
        }
        val members = ConsoleAssemblyRuntime.members(level, segment)
        if (!members.complete || members.tooLong) {
            return DashboardControllerResolution(DashboardSourceStatus.INCOMPLETE, members.positions)
        }
        if (members.positions.none { level.getBlockEntity(it) is ConsoleBlockEntity }) {
            return DashboardControllerResolution(DashboardSourceStatus.UNAVAILABLE, members.positions)
        }
        val controllers = members.positions.mapNotNull { position ->
            if (!level.isLoaded(position.below())) null
            else level.getBlockEntity(position.below()) as? DashboardControllerBlockEntity
        }.distinctBy { it.blockPos }
        if (controllers.size > 1) {
            return DashboardControllerResolution(DashboardSourceStatus.AMBIGUOUS, members.positions)
        }
        val controller = controllers.singleOrNull()
            ?: return DashboardControllerResolution(DashboardSourceStatus.UNAVAILABLE, members.positions)
        val status = if (DashboardControl.frequencyMode(controller.blockState)) {
            DashboardSourceStatus.FREQUENCY
        } else DashboardSourceStatus.AVAILABLE
        return DashboardControllerResolution(status, members.positions, controller)
    }

    fun forController(level: Level, controller: DashboardControllerBlockEntity): DashboardControllerResolution =
        resolve(level, controller.blockPos.above())
}
