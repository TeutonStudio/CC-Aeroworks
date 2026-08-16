package de.teutonstudio.ccaeroworks.computer.channel

import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity

/** A persistent logical path override keyed by the canonical channel id. */
data class ChannelPathDefinition(
    val targetId: String,
    val path: String
)

/**
 * Pure path rules shared by persistence, the server registry, Lua navigation and the client tree.
 * The path is relative to its namespace: `/wires` for wires and a concrete module for controls.
 */
object ChannelPath {
    const val MAX_SEGMENT_LENGTH = 32
    const val MAX_DEPTH = 8
    const val MAX_PATH_LENGTH = 192

    private val SEGMENT = Regex("[a-z][a-z0-9_-]{0,31}")

    fun normalize(raw: String): String {
        val value = raw.trim()
        require(value.length in 1..MAX_PATH_LENGTH) { "Channel path must contain between 1 and $MAX_PATH_LENGTH characters" }
        require(!value.startsWith('/') && !value.endsWith('/') && "//" !in value) {
            "Channel paths are relative and may not start/end with '/' or contain empty segments"
        }
        val segments = value.split('/')
        require(segments.size <= MAX_DEPTH) { "Channel paths support at most $MAX_DEPTH segments" }
        segments.forEach { segment ->
            require(SEGMENT.matches(segment)) {
                "Invalid channel path segment '$segment'. Use lowercase letters, digits, '_' or '-', starting with a letter"
            }
        }
        return segments.joinToString("/")
    }

    fun isValid(raw: String): Boolean = runCatching { normalize(raw) }.isSuccess

    fun leaf(path: String): String = path.substringAfterLast('/')

    /** Leaf paths must not duplicate or become the parent of another leaf in the same namespace. */
    fun conflicts(first: String, second: String): Boolean =
        first == second || first.startsWith("$second/") || second.startsWith("$first/")
}

data class ChannelPathChild<T>(
    val name: String,
    val path: String,
    val group: Boolean,
    val value: T?
)

/** Builds one directory level from flat logical paths without persisting synthetic groups. */
object ChannelPathTree {
    fun <T> children(entries: List<Pair<String, T>>, rawPrefix: String = ""): List<ChannelPathChild<T>> {
        val prefix = rawPrefix.trim('/')
        val groups = sortedSetOf<String>()
        val leaves = linkedMapOf<String, T>()
        entries.forEach { (rawPath, value) ->
            val path = rawPath.trim('/')
            if (path.isEmpty()) return@forEach
            val remainder = when {
                prefix.isEmpty() -> path
                path.startsWith("$prefix/") -> path.removePrefix("$prefix/")
                else -> return@forEach
            }
            if (remainder.isEmpty()) return@forEach
            val slash = remainder.indexOf('/')
            if (slash >= 0) groups += remainder.substring(0, slash)
            else leaves[remainder] = value
        }
        val base = prefix.takeIf(String::isNotEmpty)
        return buildList {
            groups.forEach { name ->
                add(ChannelPathChild(name, listOfNotNull(base, name).joinToString("/"), true, null))
            }
            leaves.entries.sortedBy { it.key }.forEach { (name, value) ->
                add(ChannelPathChild(name, listOfNotNull(base, name).joinToString("/"), false, value))
            }
        }
    }
}

/** Persistent logical naming only. It never owns signal values, DBW topology or control authority. */
class ChannelPathBank(
    private val owner: ComputerControlDeskBlockEntity
) {
    private val paths = linkedMapOf<String, String>()

    fun definitions(): List<ChannelPathDefinition> = paths.map { (targetId, path) -> ChannelPathDefinition(targetId, path) }

    fun pathFor(targetId: String): String? = paths[targetId]

    fun encodedDefinitions(): String {
        if (paths.isEmpty()) return ""
        return buildString {
            append("V|1\n")
            paths.forEach { (targetId, path) -> append("P|").append(targetId).append('|').append(path).append('\n') }
        }.trimEnd('\n')
    }

    fun loadEncodedDefinitions(encoded: String?) {
        paths.clear()
        if (encoded.isNullOrBlank()) return
        encoded.lineSequence().forEach { line ->
            if (paths.size >= MAX_OVERRIDES) return@forEach
            val parts = line.split('|', limit = 3)
            if (parts.firstOrNull() != "P" || parts.size != 3) return@forEach
            val targetId = parts[1]
            val path = parts[2]
            if (!ChannelGroupBank.isValidTarget(targetId) || !ChannelPath.isValid(path)) return@forEach
            paths.putIfAbsent(targetId, path)
        }
    }

    fun setPath(targetId: String, rawPath: String): ChannelPathDefinition {
        require(ChannelGroupBank.isValidTarget(targetId)) { "Invalid channel target '$targetId'" }
        val path = ChannelPath.normalize(rawPath)
        check(targetId in paths || paths.size < MAX_OVERRIDES) {
            "A ComputerControlDesk supports at most $MAX_OVERRIDES logical channel path overrides"
        }
        if (paths[targetId] == path) return ChannelPathDefinition(targetId, path)
        paths[targetId] = path
        changed()
        return ChannelPathDefinition(targetId, path)
    }

    fun clearPath(targetId: String): Boolean {
        val changed = paths.remove(targetId) != null
        if (changed) changed()
        return changed
    }

    private fun changed() = owner.setChanged()

    companion object {
        const val MAX_OVERRIDES = 256
    }
}

interface ChannelPathOwnerAccess {
    fun ccaeroworks_channelPaths(): ChannelPathBank
}

fun ComputerControlDeskBlockEntity.channelPaths(): ChannelPathBank =
    (this as ChannelPathOwnerAccess).ccaeroworks_channelPaths()
