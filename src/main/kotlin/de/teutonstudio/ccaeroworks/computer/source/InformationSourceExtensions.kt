package de.teutonstudio.ccaeroworks.computer.source

import de.teutonstudio.ccaeroworks.computer.ConsoleComputer
import java.util.concurrent.CopyOnWriteArrayList

object InformationSourceExtensions {
    private val providers = CopyOnWriteArrayList<(ConsoleComputer) -> List<InformationSourceView>>()
    fun register(provider: (ConsoleComputer) -> List<InformationSourceView>) { providers += provider }
    fun sources(owner: ConsoleComputer): List<InformationSourceView> =
        providers.flatMap { provider -> runCatching { provider(owner) }.getOrDefault(emptyList()) }
}
