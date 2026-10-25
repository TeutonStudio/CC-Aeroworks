package de.teutonstudio.ccaeroworks.ctfcompat.client

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.ctfcompat.control.TrainControlBinding
import de.teutonstudio.ccaeroworks.ctfcompat.control.TrainControlBindings
import de.teutonstudio.ccaeroworks.ctfcompat.network.DashboardSourceStatusPayload
import de.teutonstudio.ccaeroworks.ctfcompat.network.RequestDashboardSourcePayload
import de.teutonstudio.ccaeroworks.display.DisplayBindings
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.core.BlockPos
import net.neoforged.neoforge.network.PacketDistributor

object DashboardSourceClientState {
    private data class Key(val pos: BlockPos, val socket: Int)
    private data class PendingBinding(val binding: TrainControlBinding, val expiresAt: Long)

    private val statuses = DashboardSourceStatusCache(System::nanoTime) { pos, socket ->
        PacketDistributor.sendToServer(RequestDashboardSourcePayload(pos, socket))
    }
    private val pending = ConcurrentHashMap<Key, PendingBinding>()

    fun accept(payload: DashboardSourceStatusPayload) {
        statuses.accept(payload)
    }

    fun ensureRequested(pos: BlockPos, socket: Int) {
        statuses.ensureRequested(pos, socket)
    }

    fun status(pos: BlockPos, socket: Int): DashboardSourceStatusPayload? =
        statuses.status(pos, socket)

    fun binding(desk: ConsoleBlockEntity, socket: Int): TrainControlBinding? {
        val key = Key(desk.blockPos, socket)
        val candidate = pending[key]
        if (candidate != null) {
            if (candidate.expiresAt > System.nanoTime()) return candidate.binding
            pending.remove(key, candidate)
        }
        return TrainControlBindings.from(DisplayBindings.get(desk, socket))
    }

    fun setPending(pos: BlockPos, socket: Int, binding: TrainControlBinding) {
        pending[Key(pos.immutable(), socket)] = PendingBinding(binding, System.nanoTime() + PENDING_TTL)
    }

    fun clearPending(pos: BlockPos, socket: Int) {
        pending.remove(Key(pos, socket))
    }

    private const val PENDING_TTL = 2_000_000_000L
}
