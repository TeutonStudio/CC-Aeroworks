package de.teutonstudio.ccaeroworks.computer.channel

import de.TeutonStudio.TeutonLib.api.computer.channel.PersistentChannelGroupBank
import de.teutonstudio.ccaeroworks.computer.ConsoleComputer

typealias ChannelGroupBinding = de.TeutonStudio.TeutonLib.api.computer.channel.ChannelGroupBinding
typealias ChannelGroupDefinition = de.TeutonStudio.TeutonLib.api.computer.channel.ChannelGroupDefinition

/** Aeroworks validation and persistence adapter over TeutonLib's generic group bank. */
class ChannelGroupBank(owner: ConsoleComputer) : PersistentChannelGroupBank(
    maxGroups = MAX_GROUPS,
    maxBindings = MAX_BINDINGS,
    validName = ::isValidName,
    validTarget = ::isValidTarget,
    changed = owner::setChanged
) {
    companion object {
        const val MAX_GROUPS = 32
        const val MAX_BINDINGS = 64
        private val NAME = Regex("[a-z][a-z0-9_-]{0,31}")

        fun isValidName(name: String): Boolean = NAME.matches(name)
        fun isValidTarget(target: String): Boolean =
            target.length in 6..512 && (target.startsWith("control:") || target.startsWith("wire:")) && '|' !in target
    }
}
