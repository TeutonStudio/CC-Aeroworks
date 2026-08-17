package de.teutonstudio.ccaeroworks.computer

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.computercraft.ControlDeskPeripheralState
import de.teutonstudio.ccaeroworks.debug.TouchInputDiagnostics
import de.teutonstudio.ccaeroworks.display.DeskDisplayTouch
import de.teutonstudio.ccaeroworks.display.DisplayBinding
import de.teutonstudio.ccaeroworks.display.DisplayBindings
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import de.teutonstudio.ccaeroworks.network.DisplayPointerAction

object DeskDisplayInputDispatcher {
    @JvmStatic
    fun dispatch(desk: ConsoleBlockEntity, touch: DeskDisplayTouch, action: DisplayPointerAction) {
        val deskPos = desk.blockPos.toShortString()
        TouchInputDiagnostics.info(
            "dispatch",
            "begin desk=$deskPos socket=${touch.socket}/${touch.socketName} action=${action.eventName} pixel=${touch.x},${touch.y}/${touch.width}x${touch.height} u=${touch.u} v=${touch.v}"
        )

        ControlDeskPeripheralState.queueDisplayInput(desk, touch, action)

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
            member.pos.z
        )
        TouchInputDiagnostics.info(
            "dispatch",
            "queued ${CCAeroworks.CONSOLE_DISPLAY_INPUT_EVENT} action=${action.eventName} handler='$handlerPath' to embedded computer at ${owner.blockPos.toShortString()}"
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
            TouchInputDiagnostics.info("dispatch", "queued compatibility ${CCAeroworks.CONSOLE_TOUCH_EVENT} for tap")
        }
    }
}
