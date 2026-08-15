package de.teutonstudio.ccaeroworks.computer.channel

import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import java.util.UUID

data class ChannelGroupBinding(
    val alias: String,
    val targetId: String
)

data class ChannelGroupDefinition(
    val id: UUID,
    val name: String,
    val bindings: List<ChannelGroupBinding>
)

/** Persistent logical wiring. Bindings reference stable channel ids and survive missing hardware. */
class ChannelGroupBank(
    private val owner: ComputerControlDeskBlockEntity
) {
    private val groups = mutableListOf<ChannelGroupDefinition>()

    fun definitions(): List<ChannelGroupDefinition> = groups.map { it.copy(bindings = it.bindings.toList()) }

    fun encodedDefinitions(): String = buildString {
        groups.forEach { group ->
            append("G|").append(group.id).append('|').append(group.name).append('\n')
            group.bindings.forEach { binding ->
                append("B|").append(group.id).append('|').append(binding.alias).append('|')
                    .append(binding.targetId).append('\n')
            }
        }
    }.trimEnd('\n')

    fun loadEncodedDefinitions(encoded: String?) {
        groups.clear()
        if (encoded.isNullOrBlank()) return
        val parsed = linkedMapOf<UUID, Pair<String, MutableList<ChannelGroupBinding>>>()
        encoded.lineSequence().forEach { line ->
            val parts = line.split('|')
            when (parts.firstOrNull()) {
                "G" -> {
                    if (parts.size != 3 || parsed.size >= MAX_GROUPS) return@forEach
                    val id = runCatching { UUID.fromString(parts[1]) }.getOrNull() ?: return@forEach
                    val name = parts[2]
                    if (!isValidName(name) || parsed.values.any { it.first == name }) return@forEach
                    parsed.putIfAbsent(id, name to arrayListOf())
                }
                "B" -> {
                    if (parts.size != 4) return@forEach
                    val id = runCatching { UUID.fromString(parts[1]) }.getOrNull() ?: return@forEach
                    val entry = parsed[id] ?: return@forEach
                    if (entry.second.size >= MAX_BINDINGS) return@forEach
                    val alias = parts[2]
                    val target = parts[3]
                    if (!isValidName(alias) || !isValidTarget(target) || entry.second.any { it.alias == alias }) return@forEach
                    entry.second += ChannelGroupBinding(alias, target)
                }
            }
        }
        parsed.forEach { (id, entry) -> groups += ChannelGroupDefinition(id, entry.first, entry.second.toList()) }
    }

    fun addGroup(rawName: String): ChannelGroupDefinition {
        val name = checkedName(rawName)
        require(groups.none { it.name == name }) { "Channel group '$name' already exists" }
        check(groups.size < MAX_GROUPS) { "A ComputerControlDesk supports at most $MAX_GROUPS channel groups" }
        return ChannelGroupDefinition(UUID.randomUUID(), name, emptyList()).also {
            groups += it
            changed()
        }
    }

    fun renameGroup(id: UUID, rawName: String): ChannelGroupDefinition {
        val old = group(id)
        val name = checkedName(rawName)
        require(groups.none { it.id != id && it.name == name }) { "Channel group '$name' already exists" }
        if (old.name == name) return old
        val replacement = old.copy(name = name)
        groups[groups.indexOf(old)] = replacement
        changed()
        return replacement
    }

    fun removeGroup(id: UUID): ChannelGroupDefinition {
        val removed = group(id)
        groups.remove(removed)
        changed()
        return removed
    }

    fun bind(id: UUID, rawAlias: String, targetId: String): ChannelGroupDefinition {
        val group = group(id)
        val alias = checkedName(rawAlias)
        require(isValidTarget(targetId)) { "Invalid channel target '$targetId'" }
        val existing = group.bindings.indexOfFirst { it.alias == alias }
        val bindings = group.bindings.toMutableList()
        if (existing >= 0) bindings[existing] = ChannelGroupBinding(alias, targetId)
        else {
            check(bindings.size < MAX_BINDINGS) { "Channel group '${group.name}' supports at most $MAX_BINDINGS bindings" }
            bindings += ChannelGroupBinding(alias, targetId)
        }
        val replacement = group.copy(bindings = bindings)
        groups[groups.indexOf(group)] = replacement
        changed()
        return replacement
    }

    fun unbind(id: UUID, rawAlias: String): ChannelGroupDefinition {
        val group = group(id)
        val alias = rawAlias.trim()
        require(group.bindings.any { it.alias == alias }) { "Unknown binding '$alias' in group '${group.name}'" }
        val replacement = group.copy(bindings = group.bindings.filterNot { it.alias == alias })
        groups[groups.indexOf(group)] = replacement
        changed()
        return replacement
    }

    fun group(id: UUID): ChannelGroupDefinition = groups.firstOrNull { it.id == id }
        ?: throw NoSuchElementException("Unknown channel group '$id'")

    fun group(rawName: String): ChannelGroupDefinition {
        val name = rawName.trim()
        return groups.firstOrNull { it.name == name }
            ?: throw NoSuchElementException("Unknown channel group '$name'")
    }

    private fun changed() {
        owner.setChanged()
    }

    private fun checkedName(raw: String): String {
        val value = raw.trim()
        require(isValidName(value)) { "Invalid name '$value'. Use lowercase letters, digits, '_' or '-', starting with a letter" }
        return value
    }

    companion object {
        const val MAX_GROUPS = 32
        const val MAX_BINDINGS = 64
        private val NAME = Regex("[a-z][a-z0-9_-]{0,31}")

        fun isValidName(name: String): Boolean = NAME.matches(name)
        fun isValidTarget(target: String): Boolean =
            target.length in 6..512 && (target.startsWith("control:") || target.startsWith("wire:")) && '|' !in target
    }
}
