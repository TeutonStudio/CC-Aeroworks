package de.teutonstudio.ccaeroworks.telemetry

import com.simibubi.create.api.registry.CreateBuiltInRegistries
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext
import com.simibubi.create.content.redstone.smartObserver.SmartObserverBlockEntity
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.TankManipulationBehaviour
import de.teutonstudio.ccaeroworks.config.CCServerConfig
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.fluids.FluidStack

internal object CreateTelemetryDecoder {
    fun decode(context: DisplayLinkContext, text: List<MutableComponent>): DecodedTelemetry {
        val activeSource = context.blockEntity().activeSource
        val sourceId = activeSource?.let(CreateBuiltInRegistries.DISPLAY_SOURCE::getKey)
        val sourceType = sourceId?.toString() ?: "unknown:${activeSource?.javaClass?.simpleName ?: "none"}"
        val displayText = text.map { it.string }

        return when (sourceId?.namespace to sourceId?.path) {
            "create" to "fill_level" -> decodeFillLevel(context, sourceType, displayText)
            "create" to "count_items" -> decodeItemCount(context, sourceType, displayText)
            "create" to "list_items" -> decodeItemList(context, sourceType, displayText)
            "create" to "count_fluids" -> decodeFluidAmount(context, sourceType, displayText)
            "create" to "list_fluids" -> decodeFluidList(context, sourceType, displayText)
            else -> unsupported(sourceType, displayText)
        }
    }

    private fun decodeFillLevel(
        context: DisplayLinkContext,
        sourceType: String,
        displayText: List<String>
    ): DecodedTelemetry {
        val source = context.getSourceBlockEntity() as? ThresholdSwitchBlockEntity
            ?: return unsupported(sourceType, displayText)
        val current = source.getStockLevel().toLong()
        val minimum = source.getMinLevel().toLong()
        val maximum = source.getMaxLevel().toLong()
        if (current < 0L || minimum < 0L || maximum < minimum) {
            return unsupported(sourceType, displayText)
        }
        val contentType = when (source.getTypeOfCurrentTarget()) {
            ThresholdSwitchBlockEntity.ThresholdType.ITEM -> "item"
            ThresholdSwitchBlockEntity.ThresholdType.FLUID -> "fluid"
            ThresholdSwitchBlockEntity.ThresholdType.CUSTOM -> "custom"
            ThresholdSwitchBlockEntity.ThresholdType.UNSUPPORTED -> "unsupported"
        }
        return DecodedTelemetry(
            sourceType = sourceType,
            supported = true,
            payload = FillLevelTelemetryPayload(contentType, current, minimum, maximum),
            displayText = displayText
        )
    }

    private fun decodeItemCount(
        context: DisplayLinkContext,
        sourceType: String,
        displayText: List<String>
    ): DecodedTelemetry {
        val source = context.getSourceBlockEntity() as? SmartObserverBlockEntity
            ?: return unsupported(sourceType, displayText)
        val inventory = source.getBehaviour(InvManipulationBehaviour.TYPE)?.inventory
            ?: return unsupported(sourceType, displayText)
        val filtering = source.getBehaviour(FilteringBehaviour.TYPE)
            ?: return unsupported(sourceType, displayText)
        var count = 0L
        for (slot in 0 until inventory.slots) {
            val stack = inventory.extractItem(slot, inventory.getSlotLimit(slot), true)
            if (stack.isEmpty || !filtering.test(stack)) continue
            count += stack.count.toLong()
        }
        return DecodedTelemetry(
            sourceType = sourceType,
            supported = true,
            payload = ItemCountTelemetryPayload(count),
            displayText = displayText
        )
    }

    private fun decodeItemList(
        context: DisplayLinkContext,
        sourceType: String,
        displayText: List<String>
    ): DecodedTelemetry {
        val source = context.getSourceBlockEntity() as? SmartObserverBlockEntity
            ?: return unsupported(sourceType, displayText)
        val inventory = source.getBehaviour(InvManipulationBehaviour.TYPE)?.inventory
            ?: return unsupported(sourceType, displayText)
        val filtering = source.getBehaviour(FilteringBehaviour.TYPE)
            ?: return unsupported(sourceType, displayText)

        data class Bucket(val stack: ItemStack, var count: Long)
        val buckets = mutableListOf<Bucket>()
        for (slot in 0 until inventory.slots) {
            val stack = inventory.getStackInSlot(slot)
            if (stack.isEmpty || !filtering.test(stack)) continue
            val existing = buckets.firstOrNull { ItemStack.isSameItemSameComponents(it.stack, stack) }
            if (existing != null) {
                existing.count += stack.count.toLong()
            } else {
                buckets += Bucket(stack.copyWithCount(1), stack.count.toLong())
            }
        }

        val sorted = buckets.sortedByDescending { it.count }
        val limit = CCServerConfig.telemetryMaxListEntriesValue()
        val entries = sorted.take(limit).map { bucket ->
            TelemetryItemEntry(
                id = BuiltInRegistries.ITEM.getKey(bucket.stack.item).toString(),
                name = bucket.stack.hoverName.string,
                count = bucket.count
            )
        }
        return DecodedTelemetry(
            sourceType = sourceType,
            supported = true,
            payload = ItemListTelemetryPayload(
                totalCount = buckets.sumOf { it.count },
                entryCount = buckets.size,
                entries = entries,
                truncated = buckets.size > entries.size
            ),
            displayText = displayText
        )
    }

    private fun decodeFluidAmount(
        context: DisplayLinkContext,
        sourceType: String,
        displayText: List<String>
    ): DecodedTelemetry {
        val source = context.getSourceBlockEntity() as? SmartObserverBlockEntity
            ?: return unsupported(sourceType, displayText)
        val tank = source.getBehaviour(TankManipulationBehaviour.OBSERVE)?.inventory
            ?: return unsupported(sourceType, displayText)
        val filtering = source.getBehaviour(FilteringBehaviour.TYPE)
            ?: return unsupported(sourceType, displayText)
        var amount = 0L
        for (slot in 0 until tank.tanks) {
            val stack = tank.getFluidInTank(slot)
            if (stack.isEmpty || !filtering.test(stack)) continue
            amount += stack.amount.toLong()
        }
        return DecodedTelemetry(
            sourceType = sourceType,
            supported = true,
            payload = FluidAmountTelemetryPayload(amount),
            displayText = displayText
        )
    }

    private fun decodeFluidList(
        context: DisplayLinkContext,
        sourceType: String,
        displayText: List<String>
    ): DecodedTelemetry {
        val source = context.getSourceBlockEntity() as? SmartObserverBlockEntity
            ?: return unsupported(sourceType, displayText)
        val tank = source.getBehaviour(TankManipulationBehaviour.OBSERVE)?.inventory
            ?: return unsupported(sourceType, displayText)
        val filtering = source.getBehaviour(FilteringBehaviour.TYPE)
            ?: return unsupported(sourceType, displayText)

        data class Bucket(val sample: FluidStack, var amount: Long)
        val buckets = linkedMapOf<String, Bucket>()
        for (slot in 0 until tank.tanks) {
            val stack = tank.getFluidInTank(slot)
            if (stack.isEmpty || !filtering.test(stack)) continue
            val id = BuiltInRegistries.FLUID.getKey(stack.fluid).toString()
            val existing = buckets[id]
            if (existing != null) {
                existing.amount += stack.amount.toLong()
            } else {
                buckets[id] = Bucket(stack.copyWithAmount(1), stack.amount.toLong())
            }
        }

        val sorted = buckets.entries.sortedByDescending { it.value.amount }
        val limit = CCServerConfig.telemetryMaxListEntriesValue()
        val entries = sorted.take(limit).map { (id, bucket) ->
            TelemetryFluidEntry(
                id = id,
                name = bucket.sample.hoverName.string,
                amount = bucket.amount
            )
        }
        return DecodedTelemetry(
            sourceType = sourceType,
            supported = true,
            payload = FluidListTelemetryPayload(
                totalAmount = buckets.values.sumOf { it.amount },
                entryCount = buckets.size,
                entries = entries,
                truncated = buckets.size > entries.size
            ),
            displayText = displayText
        )
    }

    private fun unsupported(sourceType: String, displayText: List<String>): DecodedTelemetry =
        DecodedTelemetry(
            sourceType = sourceType,
            supported = false,
            payload = UnsupportedTelemetryPayload(displayText),
            displayText = displayText
        )
}
