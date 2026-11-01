package de.teutonstudio.ccaeroworks.client.hud

/** A mounted root and every attached child share one flattened console socket. */
object ControlHudModules {
    data class AxisWidget(val socket: Int, val channels: List<Int>)
    data class Module(val socket: Int, val axes: List<List<Int>>, val buttons: List<Int>)

    fun combine(axes: List<AxisWidget>, buttons: Map<Int, List<Int>>): List<Module> {
        val bySocket = axes.filter { it.channels.isNotEmpty() }.groupBy { it.socket }
        return (bySocket.keys + buttons.filterValues { it.isNotEmpty() }.keys).sorted().map { socket ->
            Module(socket, bySocket[socket].orEmpty().map { it.channels }, buttons[socket].orEmpty())
        }
    }
}
