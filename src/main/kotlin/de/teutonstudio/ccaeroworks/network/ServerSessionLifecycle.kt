package de.teutonstudio.ccaeroworks.network

import de.teutonstudio.ccaeroworks.computer.ControlDeskUiSwitchState
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent

/** Keeps every player-scoped server session store on the same cleanup lifecycle. */
object ServerSessionLifecycle {
    @SubscribeEvent
    fun onPlayerLogout(event: PlayerEvent.PlayerLoggedOutEvent) {
        clearPlayer(event.entity.uuid)
    }

    @SubscribeEvent
    fun onPlayerChangedDimension(event: PlayerEvent.PlayerChangedDimensionEvent) {
        clearPlayer(event.entity.uuid)
    }

    @SubscribeEvent
    fun onServerTick(event: ServerTickEvent.Post) {
        expire(event.server.overworld().gameTime)
    }

    @SubscribeEvent
    fun onServerStopped(event: ServerStoppedEvent) {
        clearAll()
    }

    internal fun clearPlayer(playerId: java.util.UUID) {
        DisplayPointerActionPayload.clearPlayerState(playerId)
        DisplayDrawPayload.clearPlayerState(playerId)
        ControlDeskUiSwitchState.clearPlayerSession(playerId)
    }

    internal fun expire(tick: Long) {
        DisplayPointerActionPayload.expirePlayerState(tick)
        DisplayDrawPayload.expirePlayerState(tick)
        ControlDeskUiSwitchState.expireSessions(tick)
    }

    internal fun clearAll() {
        DisplayPointerActionPayload.clearAllPlayerState()
        DisplayDrawPayload.clearAllPlayerState()
        ControlDeskUiSwitchState.clearAllSessions()
    }
}
