package de.teutonstudio.ccaeroworks.compat.computercraft

import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.ObjectLuaTable
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskService

/** Parse {{x=..., y=...}, ...} without converting the complete display raster through Lua. */
object DisplayPixelBatchArguments {
    @Throws(LuaException::class)
    fun parse(arguments: IArguments, index: Int): List<Pair<Int, Int>> {
        val table = ObjectLuaTable(arguments.getTable(index))
        val length = table.length()
        if (length > AeroworksDeskService.MAX_PIXEL_BATCH_POINTS) {
            throw LuaException("pixel batch exceeds ${AeroworksDeskService.MAX_PIXEL_BATCH_POINTS} points")
        }
        return (1..length).map { pointIndex ->
            val point = ObjectLuaTable(table.getTable(pointIndex))
            point.getInt("x") to point.getInt("y")
        }
    }
}
