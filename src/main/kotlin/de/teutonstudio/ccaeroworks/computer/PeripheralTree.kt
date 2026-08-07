package de.teutonstudio.ccaeroworks.computer

import net.minecraft.core.Direction
import java.util.Locale

/**
 * Builds a stable, human-readable tree view on top of the live peripheral graph.
 *
 * The graph remains the authoritative runtime model. This view only groups existing
 * handles beneath their owning control desk and does not create a second attachment layer.
 */
internal fun PeripheralNetworkRuntime.describeTree(): Map<String, Any> {
    val current = graph()
    val peripheralsByDesk = current.peripherals.groupBy { it.desk.address }

    return current.desks.associateTo(linkedMapOf()) { desk ->
        val children = linkedMapOf<String, Any>()
        peripheralsByDesk[desk.address]
            .orEmpty()
            .sortedWith(compareBy<PeripheralNetworkNode>({ sideOrder(it.side) }, { it.address }))
            .forEach { node ->
                val info = linkedMapOf<String, Any>()
                info.putAll(describePeripheral(node))
                info["x"] = node.pos.x
                info["y"] = node.pos.y
                info["z"] = node.pos.z
                info["handle"] = peripheralHandle(node)
                children[node.side.name.lowercase(Locale.ROOT)] = info
            }

        val info = linkedMapOf<String, Any>()
        info.putAll(describeDesk(desk.address))
        info["handle"] = deskHandle(desk)
        info["peripherals"] = children
        desk.address to info
    }
}

private fun sideOrder(direction: Direction): Int = when (direction) {
    Direction.NORTH -> 0
    Direction.SOUTH -> 1
    Direction.EAST -> 2
    Direction.WEST -> 3
    Direction.UP -> 4
    Direction.DOWN -> 5
}
