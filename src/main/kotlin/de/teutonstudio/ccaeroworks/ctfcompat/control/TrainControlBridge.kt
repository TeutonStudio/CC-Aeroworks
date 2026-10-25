package de.teutonstudio.ccaeroworks.ctfcompat.control

import com.mred231.aeroworks.content.controls.channel.ChannelTravel
import com.mred231.aeroworks.content.controls.channel.ControlChannel
import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.module.MountedModule
import createtrainfunctionality.DashboardControl
import createtrainfunctionality.DashboardControllerBlockEntity
import de.teutonstudio.ccaeroworks.ctfcompat.computer.DashboardControllerPeripheralRegistry
import de.teutonstudio.ccaeroworks.display.DisplayBindings
import kotlin.math.abs
import net.minecraft.server.level.ServerLevel
import net.minecraft.core.BlockPos

object TrainControlBridge {
    private val negativeThrottleChannels = setOf("y", "lever", "pitch", "pedal")

    /** Returns null only when the block above is not one of our rows or CTF owns frequency mode. */
    @JvmStatic
    fun read(
        level: ServerLevel,
        controllerPos: BlockPos,
        controller: DashboardControllerBlockEntity
    ): DashboardControl.Ask? {
        val segment = controllerPos.above()
        if (!level.isLoaded(segment) ||
            de.teutonstudio.ccaeroworks.multiblock.ConsoleAssemblyRuntime.geometry(level.getBlockState(segment)) == null
        ) return null

        if (DashboardControl.frequencyMode(controller.blockState)) {
            DashboardControllerPeripheralRegistry.reset(controller)
            return null
        }
        val resolution = DashboardControllerLookup.resolve(level, segment)
        if (resolution.status != DashboardSourceStatus.AVAILABLE || resolution.controller !== controller) {
            return REST
        }

        val controllerId = resolution.controllerId ?: return REST
        val signals = arrayListOf(DashboardControllerPeripheralRegistry.peripheral(controller).currentSignal())
        resolution.row.forEach { pos ->
            val desk = level.getBlockEntity(pos) as? ConsoleBlockEntity ?: return@forEach
            for (socket in 0 until desk.socketCount()) {
                val module = desk.module(socket) ?: continue
                val binding = TrainControlBindings.from(DisplayBindings.get(desk, socket)) ?: continue
                if (binding.controllerId != controllerId) continue
                signals += readModule(module, binding)
            }
        }
        val signal = TrainSignalMixer.mergeAll(signals)
        return DashboardControl.Ask(
            signal.throttle.coerceIn(-1.0f, 1.0f),
            TrainSignalMixer.ctfSteer(signal.steer),
            signal.brake,
            signal.station
        )
    }

    internal fun readModule(module: MountedModule, binding: TrainControlBinding): TrainSignal {
        val signals = arrayListOf<TrainSignal>()
        binding.assignments.forEach { (channelId, function) ->
            val channel = module.channels().firstOrNull { it.id() == channelId } ?: return@forEach
            if (!TrainControlBindings.supports(channel.kind(), function)) return@forEach
            val raw = module.value(channelId)
            val candidate = when (function) {
                TrainFunction.THROTTLE -> TrainSignal(throttle = throttle(module, channelId, raw))
                TrainFunction.STEER -> TrainSignal(steer = (raw / 15.0f).coerceIn(-1.0f, 1.0f))
                TrainFunction.BRAKE -> TrainSignal(brake = active(channel.kind(), raw))
                TrainFunction.STATION -> TrainSignal(station = raw != 0)
            }
            signals += candidate
        }
        return TrainSignalMixer.mergeAll(signals)
    }

    internal fun throttle(module: MountedModule, channelId: String, raw: Int): Float {
        val config = module.channelConfig(channelId)
        if (config != null && config.travel() != ChannelTravel.FULL) {
            return (abs(raw) / 15.0f).coerceIn(0.0f, 1.0f)
        }
        var value = (raw / 15.0f).coerceIn(-1.0f, 1.0f)
        if (channelId.substringAfterLast('/') in negativeThrottleChannels) value = -value
        return value
    }

    internal fun active(kind: ControlChannel.Kind, raw: Int): Boolean = when (kind) {
        ControlChannel.Kind.BUTTON -> raw != 0
        ControlChannel.Kind.AXIS -> abs(raw) > 1
    }

    private val REST = DashboardControl.Ask(0.0f, 0, false, false)
}
