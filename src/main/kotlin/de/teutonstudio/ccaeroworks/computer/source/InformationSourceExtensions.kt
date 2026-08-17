package de.teutonstudio.ccaeroworks.computer.source

import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import java.util.concurrent.CopyOnWriteArrayList

object InformationSourceExtensions {
    private val providers = CopyOnWriteArrayList<(ComputerControlDeskBlockEntity) -> List<InformationSourceView>>()
    fun register(provider: (ComputerControlDeskBlockEntity) -> List<InformationSourceView>) { providers += provider }
    fun sources(owner: ComputerControlDeskBlockEntity): List<InformationSourceView> =
        providers.flatMap { provider -> runCatching { provider(owner) }.getOrDefault(emptyList()) }
}
