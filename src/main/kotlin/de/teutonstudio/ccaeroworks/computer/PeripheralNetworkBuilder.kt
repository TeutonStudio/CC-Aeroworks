package de.teutonstudio.ccaeroworks.computer

import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.api.peripheral.PeripheralCapability
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

internal object PeripheralNetworkBuilder {
    fun build(owner: ComputerControlDeskBlockEntity): PeripheralNetworkGraph {
        val level = owner.level as? ServerLevel
            ?: throw LuaException("The computer control desk is not in a server level")
        val snapshot = ConsoleMultiblockManager.resolve(level, owner.blockPos)
        when (snapshot.state) {
            ConsoleNetworkState.TOO_LARGE ->
                throw LuaException("The control desk network exceeds 64 desks")
            ConsoleNetworkState.PARTIALLY_LOADED ->
                throw LuaException("The control desk network is only partially loaded")
            ConsoleNetworkState.CONFLICT ->
                throw LuaException("Multiple computer control desks are connected")
            else -> Unit
        }
        if (snapshot.owner !== owner) {
            throw LuaException("This computer does not own the connected control desk network")
        }

        val dimension = level.dimension().location().toString()
        val deskPositions = snapshot.members.mapTo(hashSetOf()) { it.pos }
        val desks = snapshot.members.map { member -> DeskNetworkNode(member, address(member.pos)) }
        val networkId = stableNetworkId(dimension, desks)
        val peripherals = mutableListOf<PeripheralNetworkNode>()
        for (desk in desks) {
            for (side in SCAN_DIRECTIONS) {
                val targetPos = desk.pos.relative(side)
                if (targetPos in deskPositions || !level.isLoaded(targetPos)) continue
                val peripheral = level.getCapability(
                    PeripheralCapability.get(),
                    targetPos,
                    side.opposite
                ) ?: continue
                val types = linkedSetOf(peripheral.type).apply {
                    addAll(peripheral.additionalTypes)
                }
                val duplicate = peripherals.any { existing ->
                    existing.pos == targetPos &&
                        existing.types == types &&
                        equivalent(existing.target, peripheral)
                }
                if (duplicate) continue

                peripherals += PeripheralNetworkNode(
                    desk = desk,
                    pos = targetPos.immutable(),
                    side = side,
                    address = "${desk.address}/${side.name.lowercase(Locale.ROOT)}",
                    target = peripheral,
                    primaryType = peripheral.type,
                    types = types,
                    aliases = PeripheralTypeNames.aliases(types)
                )
            }
        }
        return PeripheralNetworkGraph(
            networkId = networkId,
            revision = snapshot.revision,
            state = snapshot.state,
            desks = desks,
            peripherals = peripherals,
            dimension = dimension
        )
    }

    fun address(pos: BlockPos): String = "${pos.x},${pos.y},${pos.z}"

    private val SCAN_DIRECTIONS = listOf(
        Direction.NORTH,
        Direction.SOUTH,
        Direction.EAST,
        Direction.WEST,
        Direction.UP,
        Direction.DOWN
    )

    private fun stableNetworkId(dimension: String, desks: List<DeskNetworkNode>): String {
        val identity = buildString {
            append(dimension)
            desks.map(DeskNetworkNode::id).sorted().forEach { id ->
                append('|').append(id)
            }
        }
        return UUID.nameUUIDFromBytes(identity.toByteArray(StandardCharsets.UTF_8)).toString()
    }

    private fun equivalent(first: IPeripheral, second: IPeripheral): Boolean =
        first === second || runCatching { first.equals(second) && second.equals(first) }.getOrDefault(false)
}
