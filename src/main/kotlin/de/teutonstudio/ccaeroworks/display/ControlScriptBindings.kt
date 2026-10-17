package de.teutonstudio.ccaeroworks.display

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import com.mred231.aeroworks.content.controls.module.MountedModule
import de.teutonstudio.ccaeroworks.input.CombinedInputSource
import de.teutonstudio.ccaeroworks.registry.CCModuleTypes

/** Normal controls share the persisted per-socket script binding and its module-removal lifecycle. */
object ControlScriptBindings {
    fun supports(module: MountedModule): Boolean =
        CCModuleTypes.displayType(module.type()) == null &&
            !CombinedInputSource.isDisplayPointerModule(module) && module.channels().isNotEmpty()

    fun path(desk: ConsoleBlockEntity, socket: Int): String {
        val module = desk.module(socket) ?: return ""
        if (!supports(module)) return ""
        return (DisplayBindings.get(desk, socket) as? DisplayBinding.LuaHandler)?.path.orEmpty()
    }
}
