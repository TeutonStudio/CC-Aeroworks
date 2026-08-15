package de.teutonstudio.ccaeroworks.client

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level

/**
 * Client-only logical DBW selection for one complete active ControlDesk multiblock.
 *
 * Drive By Wire itself stores only one physical source BlockPos. This session keeps the logical
 * multiblock identity separate from that transport detail and mirrors only the currently selected
 * physical endpoint into DBW's selectedSource/currentChannel fields.
 */
object DriveByWireDeskSelectionSession {
    private data class Active(
        val anchor: BlockPos,
        val revision: Long,
        val endpoint: DriveByWireDeskEndpoint
    )

    private var active: Active? = null

    fun begin(level: Level, clicked: BlockPos): DriveByWireDeskEndpoint? {
        val selection = DriveByWireDeskSelectionResolver.resolve(level, clicked) ?: return null
        val endpoint = selection.startAt(clicked) ?: return null
        active = Active(selection.anchor, selection.revision, endpoint)
        return endpoint
    }

    fun current(level: Level): DriveByWireDeskEndpoint? {
        val previous = active ?: return null
        val selection = DriveByWireDeskSelectionResolver.resolve(level, previous.anchor)
            ?: return clearAndNull()
        val endpoint = selection.endpoint(previous.endpoint.sourcePos, previous.endpoint.channel)
            ?: return clearAndNull()
        active = Active(selection.anchor, selection.revision, endpoint)
        return endpoint
    }

    fun cycle(level: Level, forward: Boolean): DriveByWireDeskEndpoint? {
        val previous = active ?: return null
        val selection = DriveByWireDeskSelectionResolver.resolve(level, previous.anchor)
            ?: return clearAndNull()
        val endpoint = selection.next(previous.endpoint.sourcePos, previous.endpoint.channel, forward)
            ?: return clearAndNull()
        active = Active(selection.anchor, selection.revision, endpoint)
        return endpoint
    }

    fun containsMember(level: Level, pos: BlockPos): Boolean {
        val state = active ?: return false
        val selection = DriveByWireDeskSelectionResolver.resolve(level, state.anchor)
            ?: return false
        return pos in selection.memberPositions
    }

    fun anchor(level: Level): BlockPos? {
        val state = active ?: return null
        return DriveByWireDeskSelectionResolver.resolve(level, state.anchor)?.anchor
    }

    fun isActive(): Boolean = active != null

    fun clear() {
        active = null
    }

    private fun clearAndNull(): DriveByWireDeskEndpoint? {
        clear()
        return null
    }
}
