package de.teutonstudio.ccaeroworks.computer

import dan200.computercraft.api.lua.ILuaAPI
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import kotlin.math.sqrt

/** Telemetry which can be derived without consulting stationary world block entities. */
class MobileTelemetryLuaApi(
    private val access: MobileConsoleAccess
) : ILuaAPI {
    override fun getNames(): Array<String> = arrayOf("telemetry")
    override fun getModuleName(): String = "cc_aeroworks.mobile_telemetry"

    @LuaFunction(mainThread = true)
    fun list(): Map<String, Any> = linkedMapOf("contraption" to snapshot())

    @LuaFunction(mainThread = true)
    fun get(nameOrId: String): Map<String, Any>? {
        val entity = owner().entity
        return if (nameOrId.equals("contraption", true) || nameOrId == entity.uuid.toString()) snapshot() else null
    }

    @LuaFunction(mainThread = true)
    fun find(type: String): Map<String, Any> = if (
        type.equals("contraption", true) || type.equals("create:contraption", true)
    ) list() else emptyMap()

    @LuaFunction(mainThread = true)
    fun getStatus(): Map<String, Any> = linkedMapOf(
        "space" to "contraption",
        "available" to true,
        "sourceCount" to 1,
        "worldTargets" to "unavailable",
        "thirdPartyTargets" to "unavailable"
    )

    @LuaFunction(mainThread = true)
    fun getDocks(): Map<String, Any> = emptyMap()

    @LuaFunction(mainThread = true)
    fun getDock(nameOrId: String): Any? = null

    @LuaFunction(mainThread = true)
    fun rename(nameOrId: String, alias: String): Nothing = unavailable("rename")

    @LuaFunction(mainThread = true)
    fun clearName(nameOrId: String): Nothing = unavailable("clearName")

    @LuaFunction(mainThread = true)
    fun renameDock(nameOrId: String, alias: String): Nothing = unavailable("renameDock")

    @LuaFunction(mainThread = true)
    fun clearDockName(nameOrId: String): Nothing = unavailable("clearDockName")

    private fun snapshot(): Map<String, Any> {
        val owner = owner()
        val entity = owner.entity
        val velocity = entity.deltaMovement
        val rotation = entity.rotationState
        val inventory = entity.contraption.storage.allItems
        var occupiedSlots = 0
        for (slot in 0 until inventory.slots) if (!inventory.getStackInSlot(slot).isEmpty) occupiedSlots++
        return linkedMapOf(
            "id" to entity.uuid.toString(),
            "name" to "contraption",
            "kind" to "contraption",
            "sourceType" to "create:contraption",
            "space" to "contraption",
            "available" to true,
            "stale" to false,
            "position" to vector(entity.x, entity.y, entity.z),
            "rotation" to linkedMapOf(
                "x" to rotation.xRotation.toDouble(),
                "y" to rotation.yRotation.toDouble(),
                "z" to rotation.zRotation.toDouble(),
                "secondY" to rotation.secondYRotation.toDouble()
            ),
            "velocity" to vector(velocity.x, velocity.y, velocity.z),
            "speed" to sqrt(velocity.lengthSqr()),
            "mountedStorage" to linkedMapOf(
                "itemSlots" to inventory.slots,
                "occupiedItemSlots" to occupiedSlots,
                "fluidTanks" to entity.contraption.storage.fluids.tanks
            )
        )
    }

    private fun owner() = access.owner() ?: throw LuaException("The mobile control desk is no longer loaded")

    private fun unavailable(method: String): Nothing =
        throw LuaException("telemetry.$method is unavailable for stationary world targets on a moving contraption")

    private fun vector(x: Double, y: Double, z: Double): Map<String, Double> =
        linkedMapOf("x" to x, "y" to y, "z" to z)
}
