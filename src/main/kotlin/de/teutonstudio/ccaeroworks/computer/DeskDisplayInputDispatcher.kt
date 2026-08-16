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
        ControlDeskPeripheralState.queueDisplayInput(desk, touch, action)

        val level = desk.level ?: return
        val snapshot = ConsoleMultiblockManager.resolve(level, desk.blockPos)
        if (snapshot.state != ConsoleNetworkState.ACTIVE) return
        val owner = snapshot.owner ?: return
        val member = snapshot.members.firstOrNull { it.desk === desk } ?: return
        val binding = DisplayBindings.get(desk, touch.socket)
        val handlerPath = DisplayBindings.controllerPath(binding)
        val bootProgramPath = DisplayBindings.bootProgramPath(binding)

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
}
