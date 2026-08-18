package de.teutonstudio.ccaeroworks.computer

import com.mred231.aeroworks.content.controls.ConsoleBlockEntity
import dan200.computercraft.api.peripheral.IPeripheral
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMember
import de.teutonstudio.ccaeroworks.multiblock.ConsoleNetworkState
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import java.util.Locale

internal data class DeskNetworkNode(
    val member: ConsoleMember,
    val address: String
) {
    val id: String get() = member.id
    val pos: BlockPos get() = member.pos
    val desk: ConsoleBlockEntity get() = member.desk
}

internal data class PeripheralNetworkNode(
    val desk: DeskNetworkNode,
    val pos: BlockPos,
    val side: Direction,
    val address: String,
    val target: IPeripheral,
    val primaryType: String,
    val types: Set<String>,
    val aliases: Set<String>
) {
    fun matches(type: String): Boolean = PeripheralTypeNames.lookupKeys(type).any(aliases::contains)
}

internal data class PeripheralNetworkGraph(
    val networkId: String,
    val revision: Long,
    val state: ConsoleNetworkState,
    val desks: List<DeskNetworkNode>,
    val peripherals: List<PeripheralNetworkNode>,
    val dimension: String
)

internal object PeripheralTypeNames {
    private val separators = Regex("[\\s_-]+")

    fun lookupKeys(value: String): Set<String> {
        val lower = value.trim().lowercase(Locale.ROOT)
        if (lower.isEmpty()) return emptySet()
        val path = lower.substringAfter(':', lower)
        return linkedSetOf(lower, compact(lower), path, compact(path))
    }

    fun aliases(types: Iterable<String>): Set<String> = buildSet {
        types.forEach { type -> addAll(lookupKeys(type)) }
    }

    fun isControlDesk(value: String): Boolean {
        val lower = value.trim().lowercase(Locale.ROOT)
        if (lower.isEmpty()) return false
        val path = lower.substringAfter(':', lower)
        return compact(path) == "controldesk" || compact(lower) == "ccaeroworkscontroldesk"
    }

    private fun compact(value: String): String = separators.replace(value, "")
}
