package de.teutonstudio.ccaeroworks.radarcompat.compat.computercraft

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.LuaFunction
import de.teutonstudio.ccaeroworks.compat.computercraft.ControlDeskPeripheral
import de.teutonstudio.ccaeroworks.radarcompat.display.RadarDisplayBindingService

class RadarControlDeskPeripheral(desk: ConsoleBlockEntity) : ControlDeskPeripheral(desk) {
    @LuaFunction(mainThread = true)
    fun getRadarSources(): List<Map<String, Any>> = RadarDisplayBindingService.getRadarSources(desk())

    @LuaFunction(mainThread = true)
    fun setRadarSource(arguments: IArguments): Map<String, Any> =
        RadarDisplayBindingService.setRadarSource(desk(), arguments.get(0), arguments.getString(1))
}
