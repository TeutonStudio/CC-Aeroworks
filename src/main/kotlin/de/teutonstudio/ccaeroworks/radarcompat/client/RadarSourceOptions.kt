package de.teutonstudio.ccaeroworks.radarcompat.client

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.client.DisplaySourceSelection
import de.teutonstudio.ccaeroworks.client.DisplaySourceSelectorExtension
import de.teutonstudio.ccaeroworks.client.DisplaySourceSelectorExtensions
import de.teutonstudio.ccaeroworks.client.SourceSelectorIcon
import de.teutonstudio.ccaeroworks.client.SourceSelectorOption
import de.teutonstudio.ccaeroworks.client.SourceSelectorPresentation
import de.teutonstudio.ccaeroworks.display.DisplayBindings
import de.teutonstudio.ccaeroworks.radarcompat.compat.aeroworks.RadarDeskAccess
import de.teutonstudio.ccaeroworks.radarcompat.display.RadarDisplayBindings
import de.teutonstudio.ccaeroworks.radarcompat.display.RadarSourceDescriptor
import de.teutonstudio.ccaeroworks.radarcompat.display.RadarSourceRegistry
import de.teutonstudio.ccaeroworks.radarcompat.network.SetRadarDisplaySourcePayload
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.neoforge.network.PacketDistributor

internal data class RadarSourceChoice(val ingressPos: BlockPos, val descriptor: RadarSourceDescriptor?)
internal fun radarSourceKey(ingressPos: BlockPos): String =
    "extension:${RadarDisplayBindings.TYPE}:${ingressPos.x},${ingressPos.y},${ingressPos.z}"

internal fun radarSourceOption(
    deskPos: BlockPos,
    socket: Int,
    choice: RadarSourceChoice
): SourceSelectorOption<DisplaySourceSelection> {
    val descriptor = choice.descriptor
    val stack = networkControllerStack()
    val title = when {
        choice.ingressPos == deskPos -> Component.translatable("radar_source.cc_aeroworks.local")
        descriptor != null -> Component.translatable("radar_source.cc_aeroworks.desk", descriptor.memberIndex)
        else -> Component.translatable("radar_source.cc_aeroworks.unavailable")
    }
    val position = descriptor?.ingressPos ?: choice.ingressPos
    val status = descriptor?.status?.let {
        Component.translatable("radar_status.cc_aeroworks.${it.name.lowercase(java.util.Locale.ROOT)}")
    } ?: Component.translatable("radar_source.cc_aeroworks.unavailable")
    val subtitle = Component.translatable(
        "radar_source.cc_aeroworks.position_status", position.x, position.y, position.z, status
    )
    return SourceSelectorOption(
        key = radarSourceKey(choice.ingressPos),
        value = DisplaySourceSelection {
            PacketDistributor.sendToServer(
                SetRadarDisplaySourcePayload(deskPos, socket, choice.ingressPos)
            )
        },
        presentation = SourceSelectorPresentation(title, subtitle, SourceSelectorIcon.Item(stack)),
        selectable = descriptor != null
    )
}

/** Keep unavailable stored selections visible instead of silently displaying another source. */
internal fun radarSourceChoices(
    descriptors: List<RadarSourceDescriptor>,
    selectedIngress: BlockPos?
): List<RadarSourceChoice> = buildList {
    descriptors.distinctBy { it.ingressPos }.forEach {
        add(RadarSourceChoice(it.ingressPos, it))
    }
    if (selectedIngress != null && none { it.ingressPos == selectedIngress }) {
        add(RadarSourceChoice(selectedIngress, descriptors.firstOrNull { it.ingressPos == selectedIngress }))
    }
}

internal fun registerRadarDisplaySourceOptions() {
    DisplaySourceSelectorExtensions.register(
        DisplaySourceSelectorExtension(
            selectedKey = { binding ->
                RadarDisplayBindings.source(binding)?.ingressPos?.let(::radarSourceKey)
            },
            options = { desk, socket, _ -> radarDisplaySourceOptions(desk, socket) }
        )
    )
}

private fun radarDisplaySourceOptions(
    desk: ConsoleBlockEntity,
    socket: Int
): List<SourceSelectorOption<DisplaySourceSelection>> {
    if (RadarDeskAccess.radarCapableType(desk, socket) == null) return emptyList()
    val selectedIngress = RadarDisplayBindings.source(DisplayBindings.get(desk, socket))?.ingressPos
    return radarSourceChoices(RadarSourceRegistry.sources(desk), selectedIngress)
        .map { radarSourceOption(desk.blockPos, socket, it) }
}

private fun networkControllerStack(): ItemStack {
    val block = BuiltInRegistries.BLOCK.getOptional(NETWORK_CONTROLLER_ID).orElse(null)
    return if (block == null) ItemStack(Items.COMPASS) else ItemStack(block).takeUnless(ItemStack::isEmpty) ?: ItemStack(Items.COMPASS)
}
private val NETWORK_CONTROLLER_ID = ResourceLocation.fromNamespaceAndPath("create_radar", "network_filterer")
