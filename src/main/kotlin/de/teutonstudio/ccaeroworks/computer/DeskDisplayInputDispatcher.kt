package de.teutonstudio.ccaeroworks.computer

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.computercraft.ControlDeskPeripheralState
import de.teutonstudio.ccaeroworks.debug.TouchInputDiagnostics
import de.teutonstudio.ccaeroworks.display.DeskDisplayInput
import de.teutonstudio.ccaeroworks.display.DeskDisplayTouch
import de.teutonstudio.ccaeroworks.display.DisplayBinding
import de.teutonstudio.ccaeroworks.display.DisplayBindings
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import de.teutonstudio.ccaeroworks.network.DisplayPointerAction

object DeskDisplayInputDispatcher {
    @JvmStatic
    fun dispatch(desk: ConsoleBlockEntity, touch: DeskDisplayTouch, action: DisplayPointerAction) =
        dispatch(desk, DeskDisplayInput(action = action.eventName, touch = touch, isEnd = true))

    @JvmStatic
    fun dispatch(desk: ConsoleBlockEntity, input: DeskDisplayInput) {
        val touch = input.touch
        val deskPos = desk.blockPos.toShortString()
        val gesture = if (input.isDraw) {
            " gesture=${input.gestureId} seq=${input.sequence} start=${input.startX},${input.startY} delta=${input.deltaX},${input.deltaY} end=${input.isEnd}"
        } else ""
        TouchInputDiagnostics.info(
            "dispatch",
            "begin desk=$deskPos socket=${touch.socket}/${touch.socketName} action=${input.action} pixel=${touch.x},${touch.y}/${touch.width}x${touch.height} u=${touch.u} v=${touch.v}$gesture"
        )

        ControlDeskPeripheralState.queueDisplayInput(desk, input)

        val level = desk.level
        if (level == null) {
            TouchInputDiagnostics.warn("dispatch", "stop desk=$deskPos: desk has no level")
            return
        }

        val snapshot = ConsoleMultiblockManager.resolve(level, desk.blockPos)
        if (snapshot.state != ConsoleNetworkState.ACTIVE) {
            TouchInputDiagnostics.warn(
                "dispatch",
                "stop desk=$deskPos: multiblock state=${snapshot.state}, members=${snapshot.members.size}, owner=${snapshot.owner?.blockPos?.toShortString()}"
            )
            return
        }
        val owner = snapshot.owner
        if (owner == null) {
            TouchInputDiagnostics.warn("dispatch", "stop desk=$deskPos: ACTIVE multiblock has no computer owner")
            return
        }
        val member = snapshot.members.firstOrNull { it.desk === desk }
        if (member == null) {
            TouchInputDiagnostics.warn("dispatch", "stop desk=$deskPos: source desk is not present in resolved multiblock members")
            return
        }

        val binding = DisplayBindings.get(desk, touch.socket)
        val handlerPath = (binding as? DisplayBinding.LuaHandler)?.path.orEmpty()
        TouchInputDiagnostics.info(
            "dispatch",
            "binding desk=$deskPos socket=${touch.socket}: ${DisplayBindings.describe(binding)}; owner=${owner.blockPos.toShortString()} memberId=${member.id} memberIndex=${member.index}"
        )
        if (handlerPath.isBlank()) {
            TouchInputDiagnostics.warn(
                "dispatch",
                "no Lua handler path for desk=$deskPos socket=${touch.socket}; raw console event will still be queued, but automatic handler execution cannot draw anything"
            )
        }

        owner.queueComputerEventWhenReady(
            CCAeroworks.CONSOLE_DISPLAY_INPUT_EVENT,
            member.id,
            member.index,
            touch.socket,
            touch.socketName,
            touch.moduleId,
            input.action,
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
            input.gestureId ?: -1L,
            input.sequence ?: -1,
            input.startX ?: touch.x,
            input.startY ?: touch.y,
            input.deltaX ?: 0,
            input.deltaY ?: 0,
            input.isEnd
        )
        TouchInputDiagnostics.info(
            "dispatch",
            "queued ${CCAeroworks.CONSOLE_DISPLAY_INPUT_EVENT} action=${input.action} handler='$handlerPath' to embedded computer at ${owner.blockPos.toShortString()}$gesture"
        )

        if (input.action == DisplayPointerAction.TAP.eventName) {
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
            TouchInputDiagnostics.info("dispatch", "queued compatibility ${CCAeroworks.CONSOLE_TOUCH_EVENT} for tap")
        }
    }
}
