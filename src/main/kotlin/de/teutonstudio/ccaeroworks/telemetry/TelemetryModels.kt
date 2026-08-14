package de.teutonstudio.ccaeroworks.telemetry

import net.minecraft.core.BlockPos

internal enum class TelemetryEndpointKind(val serializedName: String) {
    COMPUTER("computer"),
    DOCK("dock")
}

internal sealed interface TelemetryPayload {
    val kind: String
    fun toLua(): Map<String, Any>
}

internal data class FillLevelTelemetryPayload(
    val contentType: String,
    val current: Long,
    val minimum: Long,
    val maximum: Long
) : TelemetryPayload {
    override val kind: String = "fill_level"

    override fun toLua(): Map<String, Any> {
        val fraction = if (maximum > minimum) {
            ((current - minimum).toDouble() / (maximum - minimum).toDouble()).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        return linkedMapOf(
            "contentType" to contentType,
            "current" to current,
            "minimum" to minimum,
            "maximum" to maximum,
            "fraction" to fraction,
            "percent" to fraction * 100.0
        )
    }
}

internal data class ItemCountTelemetryPayload(
    val count: Long
) : TelemetryPayload {
    override val kind: String = "item_count"
    override fun toLua(): Map<String, Any> = linkedMapOf("count" to count)
}

internal data class TelemetryItemEntry(
    val id: String,
    val name: String,
    val count: Long
) {
    fun toLua(): Map<String, Any> = linkedMapOf(
        "id" to id,
        "name" to name,
        "count" to count
    )
}

internal data class ItemListTelemetryPayload(
    val totalCount: Long,
    val entryCount: Int,
    val entries: List<TelemetryItemEntry>,
    val truncated: Boolean
) : TelemetryPayload {
    override val kind: String = "item_list"
    override fun toLua(): Map<String, Any> = linkedMapOf(
        "totalCount" to totalCount,
        "entryCount" to entryCount,
        "entries" to entries.map(TelemetryItemEntry::toLua),
        "truncated" to truncated
    )
}

internal data class FluidAmountTelemetryPayload(
    val amount: Long
) : TelemetryPayload {
    override val kind: String = "fluid_amount"
    override fun toLua(): Map<String, Any> = linkedMapOf(
        "amount" to amount,
        "buckets" to amount / 1000.0
    )
}

internal data class TelemetryFluidEntry(
    val id: String,
    val name: String,
    val amount: Long
) {
    fun toLua(): Map<String, Any> = linkedMapOf(
        "id" to id,
        "name" to name,
        "amount" to amount,
        "buckets" to amount / 1000.0
    )
}

internal data class FluidListTelemetryPayload(
    val totalAmount: Long,
    val entryCount: Int,
    val entries: List<TelemetryFluidEntry>,
    val truncated: Boolean
) : TelemetryPayload {
    override val kind: String = "fluid_list"
    override fun toLua(): Map<String, Any> = linkedMapOf(
        "totalAmount" to totalAmount,
        "entryCount" to entryCount,
        "entries" to entries.map(TelemetryFluidEntry::toLua),
        "truncated" to truncated
    )
}

internal data class UnsupportedTelemetryPayload(
    val lines: List<String>
) : TelemetryPayload {
    override val kind: String = "unsupported"
    override fun toLua(): Map<String, Any> = linkedMapOf("lines" to lines)
}

internal data class DecodedTelemetry(
    val sourceType: String,
    val supported: Boolean,
    val payload: TelemetryPayload,
    val displayText: List<String>
)

internal data class TelemetrySourceState(
    val id: String,
    val sourceType: String,
    val sourcePos: BlockPos,
    val linkPos: BlockPos,
    val supported: Boolean,
    val payload: TelemetryPayload,
    val displayText: List<String>,
    val createLabel: String?,
    val lastSeenTick: Long,
    val revision: Long
) {
    fun toLua(
        dimension: String,
        subLevelId: String?,
        alias: String?,
        gameTime: Long,
        staleAfterTicks: Long
    ): Map<String, Any> {
        val age = (gameTime - lastSeenTick).coerceAtLeast(0L)
        return linkedMapOf<String, Any>(
            "id" to id,
            "sourceType" to sourceType,
            "kind" to payload.kind,
            "supported" to supported,
            "available" to true,
            "stale" to (age > staleAfterTicks),
            "lastSeenTick" to lastSeenTick,
            "ageTicks" to age,
            "revision" to revision,
            "sourcePosition" to position(sourcePos, dimension, subLevelId),
            "linkPosition" to position(linkPos, dimension, subLevelId),
            "value" to payload.toLua(),
            "displayText" to displayText
        ).apply {
            alias?.let { put("alias", it) }
            createLabel?.let { put("createLabel", it) }
            subLevelId?.let { put("subLevelId", it) }
        }
    }
}

internal data class TelemetryEndpointState(
    val id: String,
    val kind: TelemetryEndpointKind,
    val targetPos: BlockPos,
    val dimension: String,
    val subLevelId: String?,
    val sources: LinkedHashMap<String, TelemetrySourceState> = linkedMapOf()
)

internal fun position(pos: BlockPos, dimension: String, subLevelId: String? = null): Map<String, Any> =
    linkedMapOf<String, Any>(
        "x" to pos.x,
        "y" to pos.y,
        "z" to pos.z,
        "dimension" to dimension
    ).apply {
        subLevelId?.let { put("subLevelId", it) }
    }
