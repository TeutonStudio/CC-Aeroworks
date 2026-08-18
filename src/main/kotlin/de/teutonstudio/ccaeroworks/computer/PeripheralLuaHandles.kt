package de.teutonstudio.ccaeroworks.computer

import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.IDynamicLuaObject
import dan200.computercraft.api.lua.ILuaContext
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import dan200.computercraft.api.lua.MethodResult
import dan200.computercraft.api.lua.ObjectLuaTable
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskService
import de.teutonstudio.ccaeroworks.compat.computercraft.DisplayPixelBatchArguments

internal class PeripheralLuaHandle(private val binding: PeripheralBinding) : IDynamicLuaObject {
    private val names = buildList {
        addAll(binding.methodNames.sorted())
        if ("getPeripheralInfo" !in binding.methodNames) add("getPeripheralInfo")
    }.toTypedArray()

    override fun getMethodNames(): Array<String> = names

    override fun callMethod(context: ILuaContext, method: Int, arguments: IArguments): MethodResult {
        val name = names.getOrNull(method) ?: throw LuaException("Unknown peripheral method index $method")
        return if (name == "getPeripheralInfo" && name !in binding.methodNames) {
            MethodResult.of(binding.info())
        } else {
            binding.call(context, name, arguments)
        }
    }
}

internal class DeskLuaHandle(
    private val runtime: PeripheralNetworkRuntime,
    private val address: String
) {
    @LuaFunction(mainThread = true)
    fun getInfo(): Map<String, Any> = runtime.describeDesk(address)

    @LuaFunction(mainThread = true)
    fun getSocketCount(): Int = runtime.desk(address).socketCount()

    @LuaFunction(mainThread = true)
    fun getSockets(): List<Map<String, Any>> = AeroworksDeskService.getSockets(runtime.desk(address))

    @LuaFunction(mainThread = true)
    fun getModules(): List<Map<String, Any>> = AeroworksDeskService.getModules(runtime.desk(address))

    @LuaFunction(mainThread = true)
    fun getModule(arguments: IArguments): Map<String, Any>? =
        AeroworksDeskService.getModule(runtime.desk(address), arguments.get(0))

    @LuaFunction(mainThread = true)
    fun getInput(arguments: IArguments): Any =
        AeroworksDeskService.getInput(runtime.desk(address), arguments.get(0))

    @LuaFunction(mainThread = true)
    fun getInputs(): Map<Int, Any> = AeroworksDeskService.getInputs(runtime.desk(address))

    @LuaFunction(mainThread = true)
    fun getDisplays(): List<Map<String, Any>> = AeroworksDeskService.getDisplays(runtime.desk(address))

    @LuaFunction(mainThread = true)
    fun getDisplay(arguments: IArguments): Map<String, Any> =
        AeroworksDeskService.getDisplay(runtime.desk(address), arguments.get(0))

    @LuaFunction(mainThread = true)
    fun setDisplayText(arguments: IArguments): String =
        AeroworksDeskService.setDisplayText(runtime.desk(address), arguments.get(0), arguments.getString(1))

    @LuaFunction(mainThread = true)
    fun setDisplayNumber(arguments: IArguments): String =
        AeroworksDeskService.setDisplayNumber(
            runtime.desk(address),
            arguments.get(0),
            arguments.getDouble(1),
            arguments.optBoolean(2).orElse(false)
        )

    @LuaFunction(mainThread = true)
    fun clearDisplay(arguments: IArguments) =
        AeroworksDeskService.clearDisplay(runtime.desk(address), arguments.get(0))

    @LuaFunction(mainThread = true)
    fun clearDisplays(): Int = AeroworksDeskService.clearDisplays(runtime.desk(address))

    @LuaFunction(mainThread = true)
    fun getDisplaySize(arguments: IArguments): Map<String, Int> =
        AeroworksDeskService.getDisplaySize(runtime.desk(address), arguments.get(0))

    @LuaFunction(mainThread = true)
    fun getDisplayPixel(arguments: IArguments): Boolean = AeroworksDeskService.getDisplayPixel(
        runtime.desk(address), arguments.get(0), arguments.getInt(1), arguments.getInt(2)
    )

    @LuaFunction(mainThread = true)
    fun setDisplayPixel(arguments: IArguments): Boolean = AeroworksDeskService.setDisplayPixel(
        runtime.desk(address),
        arguments.get(0),
        arguments.getInt(1),
        arguments.getInt(2),
        arguments.getBoolean(3)
    )

    @LuaFunction(mainThread = true)
    fun setDisplayPixelBatch(arguments: IArguments): Int = AeroworksDeskService.setDisplayPixelBatch(
        runtime.desk(address),
        arguments.get(0),
        DisplayPixelBatchArguments.parse(arguments, 1),
        arguments.optBoolean(2).orElse(true)
    )

    @LuaFunction(mainThread = true)
    fun setDisplayPixels(arguments: IArguments): List<String> {
        val table = ObjectLuaTable(arguments.getTable(1))
        val rows = (1..table.length()).map(table::getString)
        return AeroworksDeskService.setDisplayPixels(runtime.desk(address), arguments.get(0), rows)
    }

    @LuaFunction(mainThread = true)
    fun clearDisplayPixels(arguments: IArguments) =
        AeroworksDeskService.clearDisplayPixels(runtime.desk(address), arguments.get(0))

    @LuaFunction(mainThread = true)
    fun getPeripherals(): Map<String, Any> = runtime.peripheralsForDesk(address)

    @LuaFunction(mainThread = true)
    fun find(type: String): Any? = runtime.find(type, address, false)

    @LuaFunction(mainThread = true)
    fun findAll(type: String): Map<String, Any> =
        runtime.find(type, address, true) as Map<String, Any>

    @LuaFunction(mainThread = true)
    fun wrap(arguments: IArguments): Any? = runtime.wrap(arguments, address)
}
