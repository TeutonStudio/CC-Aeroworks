package de.teutonstudio.ccaeroworks.computer

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.computercraft.ControlDeskPeripheralState
import de.teutonstudio.ccaeroworks.display.DeskDisplayTouch
import de.teutonstudio.ccaeroworks.display.DisplayBindings
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import de.teutonstudio.ccaeroworks.network.DisplayPointerAction

object DeskDisplayInputDispatcher {
    @JvmStatic
    fun dispatch(desk: ConsoleBlockEntity, touch: DeskDisplayTouch, action: DisplayPointerAction) {
        CCAeroworks.LOGGER.info(
            "[CC-AW TOUCH 5/8 DISPATCHER] desk=${desk.blockPos} socket=${touch.socket} " +
                "action=${action.eventName} pixel=${touch.x},${touch.y}"
        )
        ControlDeskPeripheralState.queueDisplayInput(desk, touch, action)

        val level = desk.level
        if (level == null) {
            traceReject("desk_has_no_level", desk, touch, action)
            return
        }
        val snapshot = ConsoleMultiblockManager.resolve(level, desk.blockPos)
        if (snapshot.state != ConsoleNetworkState.ACTIVE) {
            traceReject("network_not_active:${snapshot.state}", desk, touch, action)
            return
        }
        val owner = snapshot.owner
        if (owner == null) {
            traceReject("network_has_no_computer_owner", desk, touch, action)
            return
        }
        val member = snapshot.members.firstOrNull { it.desk === desk }
        if (member == null) {
            traceReject("source_desk_not_in_snapshot", desk, touch, action)
            return
        }
        val binding = DisplayBindings.get(desk, touch.socket)
        val handlerPath = DisplayBindings.controllerPath(binding)
        val bootProgramPath = DisplayBindings.bootProgramPath(binding)

        CCAeroworks.LOGGER.info(
            "[CC-AW TOUCH 6/8 COMPUTER_QUEUE] owner=${owner.blockPos} computerOn=${owner.getServerComputer()?.isOn == true} " +
                "deskId=${member.id} deskIndex=${member.index} socket=${touch.socket} " +
                "handler='$handlerPath' application='$bootProgramPath'"
        )
        owner.queueComputerEventWhenReady(
            CCAeroworks.CONSOLE_DISPLAY_INPUT_EVENT,
            member.id,
            member.index,
            touch.socket,
            touch.socketName,
            touch.moduleId,
            action.eventName,
            touch.x,
            touch.y,
            touch.width,
            touch.height,
            handlerPath,
            touch.u,
            touch.v,
            member.pos.x,
            member.pos.y,
            member.pos.z,
            bootProgramPath
        )

        CCAeroworks.LOGGER.info(
            "[CC-AW TOUCH 6/8 COMPUTER_QUEUED] event=${CCAeroworks.CONSOLE_DISPLAY_INPUT_EVENT} " +
                "owner=${owner.blockPos} computerOnAfterQueue=${owner.getServerComputer()?.isOn == true}"
        )

        if (action == DisplayPointerAction.TAP) {
            owner.queueComputerEventWhenReady(
                CCAeroworks.CONSOLE_TOUCH_EVENT,
                member.id,
                member.index,
                touch.socket,
                touch.socketName,
                touch.moduleId,
                touch.x,
                touch.y,
                touch.width,
                touch.height,
                touch.u,
                touch.v,
                member.pos.x,
                member.pos.y,
                member.pos.z
            )
        }
    }

    private fun traceReject(
        reason: String,
        desk: ConsoleBlockEntity,
        touch: DeskDisplayTouch,
        action: DisplayPointerAction
    ) {
        CCAeroworks.LOGGER.warn(
            "[CC-AW TOUCH 5/8 DISPATCH_REJECT] reason=$reason desk=${desk.blockPos} " +
                "socket=${touch.socket} action=${action.eventName}"
        )
    }
}
