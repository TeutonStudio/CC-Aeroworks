package de.teutonstudio.ccaeroworks.computer

import dan200.computercraft.api.ComputerCraftAPI
import dan200.computercraft.api.component.ComputerComponent
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.telemetry.TelemetryLuaApi
import java.lang.ref.WeakReference

class ComputerConsoleAccess(owner: ComputerControlDeskBlockEntity) {
    private val owner = WeakReference(owner)

    fun owner(): ComputerControlDeskBlockEntity? =
        owner.get()?.takeIf { !it.isRemoved && it.level != null }
}

object CCComputerComponents {
    @JvmField
    val CONSOLE: ComputerComponent<ComputerConsoleAccess> =
        ComputerComponent.create(CCAeroworks.MOD_ID, "console")
}

object CCLuaApis {
    private var registered = false

    @Synchronized
    fun register() {
        if (registered) return
        ComputerCraftAPI.registerAPIFactory { system ->
            system.getComponent(CCComputerComponents.CONSOLE)?.let { access ->
                ComputerConsoleLuaApi(access, system)
            }
        }
        ComputerCraftAPI.registerAPIFactory { system ->
            system.getComponent(CCComputerComponents.CONSOLE)?.let { access ->
                TelemetryLuaApi(access, system)
            }
        }
        ComputerCraftAPI.registerAPIFactory { system ->
            system.getComponent(CCComputerComponents.CONSOLE)?.let(::ComputerControlLuaApi)
        }
        ComputerCraftAPI.registerAPIFactory { system ->
            system.getComponent(CCComputerComponents.CONSOLE)?.let(::ComputerWireLuaApi)
        }
        ComputerCraftAPI.registerAPIFactory { system ->
            system.getComponent(CCComputerComponents.CONSOLE)?.let(::ComputerWireAdminLuaApi)
        }
        registered = true
    }
}