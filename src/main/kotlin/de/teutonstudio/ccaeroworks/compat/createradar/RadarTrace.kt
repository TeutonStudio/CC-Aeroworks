package de.teutonstudio.ccaeroworks.compat.createradar

import de.teutonstudio.ccaeroworks.CCAeroworks
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.Level
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Temporary, deliberately verbose runtime tracing for the Create: Radars bridge.
 *
 * The trace is designed for one diagnostic play session. Every line carries the
 * same process-local session id, a monotonically increasing sequence number,
 * logical side, dimension, game tick and desk position so server state, packet
 * transport and client rendering can be reconstructed from a single latest.log.
 */
object RadarTrace {
    const val PREFIX: String = "[CCA-RADAR-TRACE]"

    private val sessionId: String = UUID.randomUUID().toString().substring(0, 8)
    private val sequence = AtomicLong()
    private val lastPeriodicTick = ConcurrentHashMap<String, Long>()

    @JvmStatic
    fun event(stage: String, level: Level?, desk: BlockPos?, message: String) {
        val seq = sequence.incrementAndGet()
        val side = when {
            level == null -> "NO_LEVEL"
            level.isClientSide -> "CLIENT"
            else -> "SERVER"
        }
        val dimension = runCatching { level?.dimension()?.location()?.toString() }.getOrNull() ?: "-"
        val tick = level?.gameTime ?: -1L
        CCAeroworks.LOGGER.info(
            "{} session={} seq={} stage={} side={} thread={} dim={} tick={} desk={} :: {}",
            PREFIX,
            sessionId,
            seq,
            stage,
            side,
            Thread.currentThread().name,
            dimension,
            tick,
            desk,
            message
        )
    }

    /** Emit at most once per [intervalTicks] for the same stage/side/dimension/desk. */
    @JvmStatic
    fun periodic(
        stage: String,
        level: Level?,
        desk: BlockPos?,
        intervalTicks: Long,
        message: String
    ) {
        val tick = level?.gameTime ?: -1L
        val side = if (level?.isClientSide == true) "C" else "S"
        val dimension = runCatching { level?.dimension()?.location()?.toString() }.getOrNull() ?: "-"
        val key = "$stage|$side|$dimension|$desk"
        val previous = lastPeriodicTick[key]
        if (previous != null && tick >= previous && tick - previous < intervalTicks) return
        lastPeriodicTick[key] = tick
        event(stage, level, desk, message)
    }

    @JvmStatic
    fun tag(tag: CompoundTag?, maxChars: Int = 16000): String {
        if (tag == null) return "<null>"
        val encoded = runCatching { tag.toString() }.getOrElse { "<tag-toString-failed:${it.javaClass.simpleName}:${it.message}>" }
        val body = if (encoded.length <= maxChars) encoded else encoded.take(maxChars) + "...<truncated ${encoded.length - maxChars} chars>"
        return "keys=${tag.allKeys.sorted()} snbt=$body"
    }

    @JvmStatic
    fun throwable(throwable: Throwable): String = buildString {
        append(throwable.javaClass.name)
        append(": ")
        append(throwable.message.orEmpty())
        throwable.cause?.let {
            append(" cause=")
            append(it.javaClass.name)
            append(":")
            append(it.message.orEmpty())
        }
    }
}
