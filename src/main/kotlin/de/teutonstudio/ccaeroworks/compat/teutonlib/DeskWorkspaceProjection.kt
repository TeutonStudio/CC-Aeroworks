package de.teutonstudio.ccaeroworks.compat.teutonlib

import de.TeutonStudio.TeutonLib.api.computer.SourceView
import de.teutonstudio.ccaeroworks.computer.source.InformationSourceKinds
import de.teutonstudio.ccaeroworks.computer.source.InformationSourceView

/** Pure presentation rules shared by the server workspace and its unit tests. */
internal object DeskWorkspaceProjection {
    private val CONNECTION_ORDER = listOf("adjacent", "cable_network", "modem_network", "peripheral_link")

    fun categorize(source: SourceView): SourceView = SourceView(
        source.name,
        source.types,
        when (source.connection) {
            "local" -> "adjacent"
            "wired" -> "modem_network"
            "wireless" -> "peripheral_link"
            else -> source.connection
        },
        source.devicePosition,
        source.linkPosition
    )

    fun connectionGroups(sources: List<SourceView>): List<String> {
        val present = sources.mapTo(linkedSetOf(), SourceView::connection)
        return CONNECTION_ORDER.filter(present::remove) + present.sorted()
    }

    fun sourceDetail(source: InformationSourceView): String =
        if (source.kind == InformationSourceKinds.GPS)
            listOf(source.details, source.status).filter(String::isNotBlank).joinToString(" · ")
        else source.status
}
