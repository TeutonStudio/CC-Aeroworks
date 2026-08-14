package de.teutonstudio.ccaeroworks.computer

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import net.minecraft.server.level.ServerLevel

/** Publishes a cheap invalidation event so CraftOS display-source routing can refresh bindings. */
object DisplayBindingEvents {
    fun notifyChanged(desk: ConsoleBlockEntity, socket: Int) {
        val level = desk.level as? ServerLevel ?: return
        val network = ConsoleMultiblockManager.resolve(level, desk.blockPos)
        if (network.state != ConsoleNetworkState.ACTIVE) return
        val owner = network.owner ?: return
        val member = network.members.firstOrNull { it.desk === desk } ?: return
        owner.getServerComputer()?.queueEvent(
            CCAeroworks.DISPLAY_BINDING_CHANGED_EVENT,
            arrayOf(member.id, member.index, socket)
        )
    }
}
