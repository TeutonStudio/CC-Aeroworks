package de.teutonstudio.ccaeroworks.display

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState

/** The embedded owner controls touch even when an external peripheral is listening. */
object DisplayTouchAccess {
    fun enabled(desk: ConsoleBlockEntity): Boolean {
        return enabledForNetwork(desk)
    }

    fun enabledForNetwork(desk: ConsoleBlockEntity): Boolean {
        val level = desk.level ?: return false
        val network = ConsoleMultiblockManager.resolve(level, desk.blockPos)
        val advanced = network.owner?.isAdvanced == true || network.terminal?.isAdvanced == true
        return enabled(network.state, advanced) &&
            network.members.any { it.desk === desk }
    }

    internal fun enabled(state: ConsoleNetworkState, advanced: Boolean): Boolean =
        state in listOf(ConsoleNetworkState.ACTIVE, ConsoleNetworkState.TERMINAL) && advanced
}
