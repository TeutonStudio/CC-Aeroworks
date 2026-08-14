package de.teutonstudio.ccaeroworks.computer.control

/**
 * Marks writes which originate from the ComputerControlDesk override manager.
 *
 * Aeroworks funnels controller changes through ConsoleBlockEntity#setChannelFromController. The
 * override mixin guards that method against normal/manual writes while a HARD override owns the
 * channel. Writes performed by the manager itself must pass the same Aeroworks path so its normal
 * value storage, dirty tracking and client synchronization remain authoritative.
 */
object ControlWriteContext {
    private val computerOverrideDepth = ThreadLocal.withInitial { 0 }

    @JvmStatic
    fun isComputerOverrideWrite(): Boolean = computerOverrideDepth.get() > 0

    fun <T> computerOverride(block: () -> T): T {
        computerOverrideDepth.set(computerOverrideDepth.get() + 1)
        return try {
            block()
        } finally {
            val next = computerOverrideDepth.get() - 1
            if (next <= 0) computerOverrideDepth.remove()
            else computerOverrideDepth.set(next)
        }
    }
}
