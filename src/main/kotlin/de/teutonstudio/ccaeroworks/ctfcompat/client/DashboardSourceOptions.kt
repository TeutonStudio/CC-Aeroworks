package de.teutonstudio.ccaeroworks.ctfcompat.client

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import createtrainfunctionality.StickerContent
import de.teutonstudio.ccaeroworks.client.DisplaySourceExtraRow
import de.teutonstudio.ccaeroworks.client.DisplaySourceExtraRowsExtension
import de.teutonstudio.ccaeroworks.client.DisplaySourceSelection
import de.teutonstudio.ccaeroworks.client.DisplaySourceSelectorExtension
import de.teutonstudio.ccaeroworks.client.DisplaySourceSelectorExtensions
import de.teutonstudio.ccaeroworks.client.SourceSelectorIcon
import de.teutonstudio.ccaeroworks.client.SourceSelectorOption
import de.teutonstudio.ccaeroworks.client.SourceSelectorPresentation
import de.teutonstudio.ccaeroworks.ctfcompat.control.DashboardSourceStatus
import de.teutonstudio.ccaeroworks.ctfcompat.control.TrainControlBinding
import de.teutonstudio.ccaeroworks.ctfcompat.control.TrainControlBindings
import de.teutonstudio.ccaeroworks.ctfcompat.control.TrainFunction
import de.teutonstudio.ccaeroworks.ctfcompat.network.SetDashboardSourcePayload
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.PacketDistributor

internal fun registerDashboardSourceOptions() {
    DisplaySourceSelectorExtensions.register(
        DisplaySourceSelectorExtension(
            selectedKey = { binding -> TrainControlBindings.from(binding)?.let(::trainSourceKey) },
            options = { desk, socket, _ -> dashboardSourceOptions(desk, socket) }
        )
    )
    DisplaySourceSelectorExtensions.registerRows(
        DisplaySourceExtraRowsExtension { desk, socket -> dashboardRows(desk, socket) }
    )
    DisplaySourceSelectorExtensions.onSelection { desk, socket, key ->
        if (!key.startsWith("extension:${TrainControlBindings.TYPE}:")) {
            DashboardSourceClientState.clearPending(desk.blockPos, socket)
        }
    }
}

internal fun trainSourceKey(binding: TrainControlBinding): String =
    "extension:${TrainControlBindings.TYPE}:${binding.controllerId}"

private fun dashboardSourceOptions(
    desk: ConsoleBlockEntity,
    socket: Int
): List<SourceSelectorOption<DisplaySourceSelection>> {
    DashboardSourceClientState.ensureRequested(desk.blockPos, socket)
    val status = DashboardSourceClientState.status(desk.blockPos, socket)
    val selected = DashboardSourceClientState.binding(desk, socket)
    val availableId = status?.controllerId?.takeIf { status.status == DashboardSourceStatus.AVAILABLE }
    if (availableId == null && selected == null) {
        return if (status?.status in setOf(
                DashboardSourceStatus.AMBIGUOUS,
                DashboardSourceStatus.FREQUENCY,
                DashboardSourceStatus.INCOMPLETE
            )
        ) listOf(dashboardUnavailableOption(status!!.status)) else emptyList()
    }

    return buildList {
        if (selected != null && selected.controllerId != availableId) {
            add(
                dashboardOption(
                    desk, socket, selected, status?.status,
                    selectable = false, resetAssignments = false
                )
            )
        }
        if (availableId != null) {
            val current = selected?.takeIf { it.controllerId == availableId }
            add(
                dashboardOption(
                    desk,
                    socket,
                    current ?: TrainControlBinding(availableId, emptyMap()),
                    DashboardSourceStatus.AVAILABLE,
                    selectable = true,
                    resetAssignments = current == null
                )
            )
        }
    }
}

private fun dashboardUnavailableOption(
    status: DashboardSourceStatus
): SourceSelectorOption<DisplaySourceSelection> = SourceSelectorOption(
    key = "extension:${TrainControlBindings.TYPE}:unavailable:${status.name.lowercase()}",
    value = DisplaySourceSelection {},
    presentation = SourceSelectorPresentation(
        Component.translatable("ctf_source.cc_aeroworks.dashboard_controller"),
        Component.translatable("ctf_source.cc_aeroworks.status.${status.name.lowercase()}"),
        dashboardIcon()
    ),
    selectable = false
)

private fun dashboardOption(
    desk: ConsoleBlockEntity,
    socket: Int,
    binding: TrainControlBinding,
    status: DashboardSourceStatus?,
    selectable: Boolean,
    resetAssignments: Boolean
): SourceSelectorOption<DisplaySourceSelection> {
    val subtitle = Component.translatable(
        "ctf_source.cc_aeroworks.status.${(status ?: DashboardSourceStatus.UNAVAILABLE).name.lowercase()}"
    )
    return SourceSelectorOption(
        key = trainSourceKey(binding),
        value = DisplaySourceSelection {
            val selected = if (resetAssignments) binding.copy(assignments = emptyMap()) else binding
            DashboardSourceClientState.setPending(desk.blockPos, socket, selected)
            PacketDistributor.sendToServer(
                SetDashboardSourcePayload(desk.blockPos, socket, selected.controllerId, selected.assignments)
            )
        },
        presentation = SourceSelectorPresentation(
            Component.translatable("ctf_source.cc_aeroworks.dashboard_controller"),
            subtitle,
            dashboardIcon()
        ),
        selectable = selectable
    )
}

private fun dashboardRows(desk: ConsoleBlockEntity, socket: Int): List<DisplaySourceExtraRow> {
    DashboardSourceClientState.ensureRequested(desk.blockPos, socket)
    val binding = DashboardSourceClientState.binding(desk, socket) ?: return emptyList()
    val module = desk.module(socket) ?: return emptyList()
    return module.channels().map { channel ->
        val channelId = channel.id()
        DisplaySourceExtraRow(
            message = Component.translatable("ctf_source.cc_aeroworks.channel", channelId),
            selectedKey = {
                DashboardSourceClientState.binding(desk, socket)
                    ?.assignments?.get(channelId)?.name?.lowercase().orEmpty()
            },
            options = { channelOptions(desk, socket, binding.controllerId, channelId, channel.kind()) }
        )
    }
}

private fun channelOptions(
    desk: ConsoleBlockEntity,
    socket: Int,
    controllerId: java.util.UUID,
    channelId: String,
    kind: com.mred231.aeroworks.content.controls.channel.ControlChannel.Kind
): List<SourceSelectorOption<DisplaySourceSelection>> {
    val binding = DashboardSourceClientState.binding(desk, socket)
        ?: TrainControlBinding(controllerId, emptyMap())
    val status = DashboardSourceClientState.status(desk.blockPos, socket)
    val available = status?.status == DashboardSourceStatus.AVAILABLE && status.controllerId == binding.controllerId
    val functions = TrainFunction.entries.filter { TrainControlBindings.supports(kind, it) }
    return buildList {
        add(channelOption(desk, socket, binding, channelId, null, available))
        functions.forEach { add(channelOption(desk, socket, binding, channelId, it, available)) }
    }
}

private fun channelOption(
    desk: ConsoleBlockEntity,
    socket: Int,
    binding: TrainControlBinding,
    channelId: String,
    function: TrainFunction?,
    selectable: Boolean
): SourceSelectorOption<DisplaySourceSelection> {
    val key = function?.name?.lowercase().orEmpty()
    val title = Component.translatable(
        if (function == null) "ctf_source.cc_aeroworks.function.none"
        else "ctf_source.cc_aeroworks.function.$key"
    )
    return SourceSelectorOption(
        key = key,
        value = DisplaySourceSelection {
            val assignments = binding.assignments.toMutableMap()
            if (function == null) assignments.remove(channelId) else assignments[channelId] = function
            val updated = binding.copy(assignments = assignments)
            DashboardSourceClientState.setPending(desk.blockPos, socket, updated)
            PacketDistributor.sendToServer(
                SetDashboardSourcePayload(desk.blockPos, socket, updated.controllerId, updated.assignments)
            )
        },
        presentation = SourceSelectorPresentation(
            Component.translatable("ctf_source.cc_aeroworks.channel_function", channelId, title),
            Component.translatable(
                if (selectable) "ctf_source.cc_aeroworks.assignment_ready"
                else "ctf_source.cc_aeroworks.assignment_suspended"
            ),
            dashboardIcon()
        ),
        selectable = selectable
    )
}

private fun dashboardIcon(): SourceSelectorIcon =
    SourceSelectorIcon.Item(ItemStack(StickerContent.DASHBOARD_CONTROLLER_ITEM.get()))
