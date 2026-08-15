package de.teutonstudio.ccaeroworks.computer.source

import dan200.computercraft.api.ComputerCraftAPI
import dan200.computercraft.api.network.Packet
import dan200.computercraft.api.network.PacketNetwork
import dan200.computercraft.api.network.PacketReceiver
import dan200.computercraft.api.network.PacketSender
import dan200.computercraft.api.peripheral.PeripheralCapability
import dan200.computercraft.shared.peripheral.modem.wireless.WirelessModemPeripheral
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockManager
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

internal data class GpsFix(
    val x: Double,
    val y: Double,
    val z: Double,
    val hostCount: Int,
    val resolvedTick: Long
)

internal data class GpsSourceStatus(
    val fix: GpsFix,
    val status: String,
    val ageTicks: Long
)

/**
 * Passive-on-demand CC:Tweaked GPS discovery for the ComputerControlDesk information-source page.
 *
 * A source request starts a real GPS PING over CC:Tweaked's wireless packet network when the
 * embedded computer has a directly attached wireless modem. The probe uses its own reply channel,
 * so it does not consume or inject events into the user's CraftOS process. Replies are collected
 * for up to two seconds and trilaterated with the same geometry used by CC:Tweaked's gps.lua.
 */
object GpsSourceTracker {
    private data class Tracked(
        var owner: ComputerControlDeskBlockEntity,
        var active: Probe? = null,
        var fix: GpsFix? = null,
        var lastProbeTick: Long = Long.MIN_VALUE,
        var lastRequestTick: Long = Long.MIN_VALUE
    )

    private val tracked = linkedMapOf<java.util.UUID, Tracked>()

    @Synchronized
    fun request(owner: ComputerControlDeskBlockEntity) {
        val level = owner.level as? ServerLevel ?: return
        val now = level.gameTime
        val id = owner.deskId
        val state = tracked[id]?.takeIf { it.owner === owner } ?: Tracked(owner).also { replacement ->
            tracked.remove(id)?.active?.close()
            tracked[id] = replacement
        }
        state.lastRequestTick = now
        settle(state, now)
        if (state.active != null) return
        if (state.lastProbeTick != Long.MIN_VALUE && now - state.lastProbeTick < PROBE_INTERVAL_TICKS) return

        val modem = findWirelessModem(owner)
        state.lastProbeTick = now
        if (modem == null) {
            state.fix = null
            return
        }

        val network = ComputerCraftAPI.getWirelessNetwork(level.server) ?: return
        val modemLevel = modem.level ?: level
        val modemPosition = modem.position ?: owner.blockPos.center
        val replyChannel = replyChannel(id, now)
        val probe = Probe(
            network = network,
            level = modemLevel,
            position = modemPosition,
            range = modem.range,
            interdimensional = modem.isInterdimensional,
            replyChannel = replyChannel,
            startedTick = now,
            senderId = "cc_aeroworks_gps_${id.toString().take(8)}"
        )
        state.active = probe
        network.addReceiver(probe)

        val packet = Packet(GPS_CHANNEL, replyChannel, GPS_PING, probe)
        if (probe.interdimensional) {
            network.transmitInterdimensional(packet)
        } else {
            network.transmitSameDimension(packet, probe.range)
        }
    }

    @Synchronized
    internal fun current(owner: ComputerControlDeskBlockEntity): GpsSourceStatus? {
        val level = owner.level as? ServerLevel ?: return null
        val state = tracked[owner.deskId]?.takeIf { it.owner === owner } ?: return null
        settle(state, level.gameTime)
        val fix = state.fix ?: return null
        val age = (level.gameTime - fix.resolvedTick).coerceAtLeast(0L)
        if (age > DROP_AFTER_TICKS) return null
        return GpsSourceStatus(
            fix = fix,
            status = if (age <= READY_AFTER_TICKS) "ready" else "stale",
            ageTicks = age
        )
    }

    @SubscribeEvent
    @Synchronized
    fun onServerTick(event: ServerTickEvent.Post) {
        val iterator = tracked.iterator()
        while (iterator.hasNext()) {
            val (_, state) = iterator.next()
            val level = state.owner.level as? ServerLevel
            if (state.owner.isRemoved || level == null) {
                state.active?.close()
                iterator.remove()
                continue
            }
            val now = level.gameTime
            settle(state, now)
            if (state.lastRequestTick != Long.MIN_VALUE && now - state.lastRequestTick > TRACKER_IDLE_TICKS) {
                state.active?.close()
                iterator.remove()
            }
        }
    }

    private fun settle(state: Tracked, now: Long) {
        val probe = state.active ?: return
        val resolved = probe.resolved
        if (resolved != null) {
            probe.close()
            state.active = null
            state.fix = GpsFix(
                x = resolved.x,
                y = resolved.y,
                z = resolved.z,
                hostCount = probe.hostCount,
                resolvedTick = now
            )
            return
        }
        if (now - probe.startedTick >= PROBE_TIMEOUT_TICKS) {
            probe.close()
            state.active = null
        }
    }

    private fun findWirelessModem(owner: ComputerControlDeskBlockEntity): WirelessModemPeripheral? {
        val level = owner.level as? ServerLevel ?: return null
        val deskMembers = ConsoleMultiblockManager.resolve(level, owner.blockPos)
            .members
            .mapTo(hashSetOf()) { it.pos }
        for (side in Direction.values()) {
            val targetPos = owner.blockPos.relative(side)
            if (targetPos in deskMembers || !level.isLoaded(targetPos)) continue
            val peripheral = level.getCapability(
                PeripheralCapability.get(),
                targetPos,
                side.opposite
            ) ?: continue
            val wireless = peripheral as? WirelessModemPeripheral ?: continue
            return wireless
        }
        return null
    }

    private fun replyChannel(id: java.util.UUID, tick: Long): Int {
        val mixed = (id.mostSignificantBits xor id.leastSignificantBits xor tick).hashCode()
        return REPLY_CHANNEL_MIN + Math.floorMod(mixed, REPLY_CHANNEL_MAX - REPLY_CHANNEL_MIN + 1)
    }

    private class Probe(
        private val network: PacketNetwork,
        private val level: Level,
        private val position: Vec3,
        val range: Double,
        val interdimensional: Boolean,
        private val replyChannel: Int,
        val startedTick: Long,
        private val senderId: String
    ) : PacketReceiver, PacketSender {
        private data class HostFix(val position: Vec3, val distance: Double)

        private val fixes = arrayListOf<HostFix>()
        private val seenHosts = arrayListOf<Vec3>()
        private var pos1: Vec3? = null
        private var pos2: Vec3? = null
        var resolved: Vec3? = null
            private set
        var hostCount: Int = 0
            private set
        private var closed = false

        override fun getLevel(): Level = level
        override fun getPosition(): Vec3 = position
        override fun getRange(): Double = range
        override fun isInterdimensional(): Boolean = interdimensional
        override fun getSenderID(): String = senderId

        override fun receiveSameDimension(packet: Packet, distance: Double) {
            if (closed || resolved != null) return
            if (packet.channel() != replyChannel || packet.replyChannel() != GPS_CHANNEL) return
            val hostPosition = coordinates(packet.payload()) ?: return
            record(HostFix(hostPosition, distance))
        }

        override fun receiveDifferentDimension(packet: Packet) = Unit

        fun close() {
            if (closed) return
            closed = true
            network.removeReceiver(this)
        }

        private fun record(fix: HostFix) {
            if (seenHosts.none { it.distanceTo(fix.position) < 1.0 }) {
                seenHosts += fix.position
                hostCount = seenHosts.size
            }

            if (fix.distance == 0.0) {
                resolved = roundVec(fix.position)
                return
            }

            var insertionIndex = min(2, fixes.size)
            fixes.forEachIndexed { index, older ->
                if (older.position.distanceTo(fix.position) < 1.0) {
                    insertionIndex = index
                    return@forEachIndexed
                }
            }
            if (insertionIndex < fixes.size) {
                fixes[insertionIndex] = fix
            } else {
                fixes += fix
            }

            if (fixes.size < 3) return
            if (pos1 == null) {
                val pair = trilaterate(fixes[0], fixes[1], fixes[2]) ?: return
                pos1 = pair.first
                pos2 = pair.second
            } else {
                val narrowed = narrow(pos1!!, pos2 ?: return, fixes[2])
                pos1 = narrowed.first
                pos2 = narrowed.second
            }
            if (pos1 != null && pos2 == null) resolved = roundVec(pos1!!)
        }

        private fun trilaterate(a: HostFix, b: HostFix, c: HostFix): Pair<Vec3, Vec3?>? {
            val a2b = b.position.subtract(a.position)
            val a2c = c.position.subtract(a.position)
            if (a2b.lengthSqr() < EPSILON || a2c.lengthSqr() < EPSILON) return null
            val a2bNormal = a2b.normalize()
            val a2cNormal = a2c.normalize()
            if (abs(a2bNormal.dot(a2cNormal)) > 0.999) return null

            val d = a2b.length()
            if (d < EPSILON) return null
            val ex = a2bNormal
            val i = ex.dot(a2c)
            val eyRaw = a2c.subtract(ex.scale(i))
            if (eyRaw.lengthSqr() < EPSILON) return null
            val ey = eyRaw.normalize()
            val j = ey.dot(a2c)
            if (abs(j) < EPSILON) return null
            val ez = ex.cross(ey)

            val r1 = a.distance
            val r2 = b.distance
            val r3 = c.distance
            val x = (r1 * r1 - r2 * r2 + d * d) / (2.0 * d)
            val y = (r1 * r1 - r3 * r3 - x * x + (x - i) * (x - i) + j * j) / (2.0 * j)
            val base = a.position.add(ex.scale(x)).add(ey.scale(y))
            val zSquared = r1 * r1 - x * x - y * y
            if (zSquared > 0.0) {
                val z = sqrt(zSquared)
                val first = roundVec(base.add(ez.scale(z)))
                val second = roundVec(base.subtract(ez.scale(z)))
                return if (sameRounded(first, second)) first to null else first to second
            }
            return roundVec(base) to null
        }

        private fun narrow(first: Vec3, second: Vec3, fix: HostFix): Pair<Vec3, Vec3?> {
            val firstError = abs(first.distanceTo(fix.position) - fix.distance)
            val secondError = abs(second.distanceTo(fix.position) - fix.distance)
            return when {
                abs(firstError - secondError) < 0.01 -> first to second
                firstError < secondError -> roundVec(first) to null
                else -> roundVec(second) to null
            }
        }
    }

    private fun coordinates(payload: Any?): Vec3? {
        val values = when (payload) {
            is List<*> -> listOf(payload.getOrNull(0), payload.getOrNull(1), payload.getOrNull(2))
            is Array<*> -> listOf(payload.getOrNull(0), payload.getOrNull(1), payload.getOrNull(2))
            is Map<*, *> -> listOf(
                indexed(payload, 1),
                indexed(payload, 2),
                indexed(payload, 3)
            )
            else -> return null
        }
        val x = (values[0] as? Number)?.toDouble() ?: return null
        val y = (values[1] as? Number)?.toDouble() ?: return null
        val z = (values[2] as? Number)?.toDouble() ?: return null
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return null
        return Vec3(x, y, z)
    }

    private fun indexed(map: Map<*, *>, index: Int): Any? =
        map[index] ?: map[index.toLong()] ?: map[index.toDouble()]

    private fun roundVec(value: Vec3): Vec3 = Vec3(
        round(value.x * 100.0) / 100.0,
        round(value.y * 100.0) / 100.0,
        round(value.z * 100.0) / 100.0
    )

    private fun sameRounded(first: Vec3, second: Vec3): Boolean =
        first.x == second.x && first.y == second.y && first.z == second.z

    private const val GPS_CHANNEL = 65534
    private const val GPS_PING = "PING"
    private const val PROBE_TIMEOUT_TICKS = 40L
    private const val PROBE_INTERVAL_TICKS = 100L
    private const val READY_AFTER_TICKS = 100L
    private const val DROP_AFTER_TICKS = 300L
    private const val TRACKER_IDLE_TICKS = 400L
    private const val REPLY_CHANNEL_MIN = 48000
    private const val REPLY_CHANNEL_MAX = 65000
    private const val EPSILON = 1.0E-12
}
