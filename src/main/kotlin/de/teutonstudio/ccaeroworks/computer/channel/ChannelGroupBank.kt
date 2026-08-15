package de.teutonstudio.ccaeroworks.computer.channel

import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import de.teutonstudio.ccaeroworks.computer.control.ControlOverrideManager
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.WeakHashMap

data class ChannelGroupBinding(
    val name: String,
    val targetId: String
)

data class ChannelGroupDefinition(
    val id: UUID,
    val name: String,
    val bindings: List<ChannelGroupBinding>
)

/** Persistent user-defined logical channel groups. Physical channels remain owned by Aeroworks/WireChannelBank. */
class ChannelGroupBank internal constructor(
    private val owner: ComputerControlDeskBlockEntity
) {
    private val groups = mutableListOf<ChannelGroupDefinition>()

    fun definitions(): List<ChannelGroupDefinition> = groups.toList()

    fun encodedDefinitions(): String = buildString {
        groups.forEach { group ->
            append("G|").append(group.id).append('|').append(encode(group.name)).append('\n')
            group.bindings.forEach { binding ->
                append("B|").append(group.id).append('|')
                    .append(encode(binding.name)).append('|')
                    .append(encode(binding.targetId)).append('\n')
            }
        }
    }.trimEnd()

    fun loadEncodedDefinitions(encoded: String?) {
        groups.clear()
        if (encoded.isNullOrBlank()) return

        val rawGroups = linkedMapOf<UUID, Pair<String, MutableList<ChannelGroupBinding>>>()
        encoded.lineSequence().forEach { line ->
            val parts = line.split('|')
            when (parts.firstOrNull()) {
                "G" -> {
                    if (parts.size != 3 || rawGroups.size >= MAX_GROUPS) return@forEach
                    val id = runCatching { UUID.fromString(parts[1]) }.getOrNull() ?: return@forEach
                    val name = decode(parts[2]) ?: return@forEach
                    if (!isValidName(name) || rawGroups.values.any { it.first == name }) return@forEach
                    rawGroups[id] = name to mutableListOf()
                }
                "B" -> {
                    if (parts.size != 4) return@forEach
                    val id = runCatching { UUID.fromString(parts[1]) }.getOrNull() ?: return@forEach
                    val target = rawGroups[id] ?: return@forEach
                    if (target.second.size >= MAX_BINDINGS_PER_GROUP) return@forEach
                    val name = decode(parts[2]) ?: return@forEach
                    val targetId = decode(parts[3]) ?: return@forEach
                    if (!isValidName(name) || targetId.isBlank() || target.second.any { it.name == name }) return@forEach
                    target.second += ChannelGroupBinding(name, targetId)
                }
            }
        }
        rawGroups.forEach { (id, entry) ->
            groups += ChannelGroupDefinition(id, entry.first, entry.second.toList())
        }
    }

    fun create(rawName: String): ChannelGroupDefinition {
        val name = checkedName(rawName)
        require(groups.none { it.name == name }) { "Channel group '$name' already exists" }
        check(groups.size < MAX_GROUPS) { "A ComputerControlDesk supports at most $MAX_GROUPS channel groups" }
        val definition = ChannelGroupDefinition(UUID.randomUUID(), name, emptyList())
        groups += definition
        changed()
        return definition
    }

    fun remove(rawNameOrId: String): ChannelGroupDefinition {
        val definition = requiredGroup(rawNameOrId)
        groups.remove(definition)
        changed()
        return definition
    }

    fun rename(rawNameOrId: String, rawNewName: String): ChannelGroupDefinition {
        val existing = requiredGroup(rawNameOrId)
        val newName = checkedName(rawNewName)
        require(groups.none { it.id != existing.id && it.name == newName }) {
            "Channel group '$newName' already exists"
        }
        if (existing.name == newName) return existing
        val replacement = existing.copy(name = newName)
        groups[groups.indexOf(existing)] = replacement
        changed()
        return replacement
    }

    fun bind(rawGroup: String, rawName: String, targetId: String): ChannelGroupDefinition {
        require(targetId.isNotBlank()) { "Channel target must not be blank" }
        val group = requiredGroup(rawGroup)
        val name = checkedName(rawName)
        val bindings = group.bindings.toMutableList()
        val existing = bindings.indexOfFirst { it.name == name }
        val binding = ChannelGroupBinding(name, targetId)
        if (existing >= 0) bindings[existing] = binding
        else {
            check(bindings.size < MAX_BINDINGS_PER_GROUP) {
                "Channel group '${group.name}' supports at most $MAX_BINDINGS_PER_GROUP bindings"
            }
            bindings += binding
        }
        val replacement = group.copy(bindings = bindings)
        groups[groups.indexOf(group)] = replacement
        changed()
        return replacement
    }

    fun unbind(rawGroup: String, rawName: String): Boolean {
        val group = requiredGroup(rawGroup)
        val name = rawName.trim()
        val bindings = group.bindings.filterNot { it.name == name }
        if (bindings.size == group.bindings.size) return false
        groups[groups.indexOf(group)] = group.copy(bindings = bindings)
        changed()
        return true
    }

    fun find(rawNameOrId: String): ChannelGroupDefinition? {
        val key = rawNameOrId.trim()
        return groups.firstOrNull { it.name == key || it.id.toString() == key }
    }

    private fun requiredGroup(rawNameOrId: String): ChannelGroupDefinition =
        find(rawNameOrId) ?: throw NoSuchElementException("Unknown channel group '${rawNameOrId.trim()}'")

    private fun checkedName(rawName: String): String {
        val name = rawName.trim().lowercase(Locale.ROOT)
        require(isValidName(name)) {
            "Invalid channel/group name '$name'. Use lowercase letters, digits, '_' or '-', starting with a letter"
        }
        return name
    }

    private fun changed() = owner.markChannelGroupsChanged()

    companion object {
        const val MAX_GROUPS: Int = 32
        const val MAX_BINDINGS_PER_GROUP: Int = 64
        private val NAME = Regex("[a-z][a-z0-9_-]{0,31}")
        private val ENCODER = Base64.getUrlEncoder().withoutPadding()
        private val DECODER = Base64.getUrlDecoder()

        fun isValidName(name: String): Boolean = NAME.matches(name)

        private fun encode(value: String): String =
            ENCODER.encodeToString(value.toByteArray(StandardCharsets.UTF_8))

        private fun decode(value: String): String? = runCatching {
            String(DECODER.decode(value), StandardCharsets.UTF_8)
        }.getOrNull()
    }
}

/**
 * Keeps the group bank attached to the block entity while storing the encoded form in NeoForge's
 * persistent block-entity data. The GUI obtains it only through the server-authoritative snapshot.
 */
private object ChannelGroupStores {
    private const val PERSISTENT_KEY = "cc_aeroworks_channel_groups"
    private val banks = WeakHashMap<ComputerControlDeskBlockEntity, ChannelGroupBank>()

    @Synchronized
    fun bank(owner: ComputerControlDeskBlockEntity): ChannelGroupBank = banks.getOrPut(owner) {
        ChannelGroupBank(owner).also { bank ->
            bank.loadEncodedDefinitions(owner.persistentData.getString(PERSISTENT_KEY).takeIf(String::isNotEmpty))
        }
    }

    @Synchronized
    fun persist(owner: ComputerControlDeskBlockEntity) {
        val encoded = bank(owner).encodedDefinitions()
        if (encoded.isEmpty()) owner.persistentData.remove(PERSISTENT_KEY)
        else owner.persistentData.putString(PERSISTENT_KEY, encoded)
        owner.setChanged()
    }
}

internal val ComputerControlDeskBlockEntity.channelGroups: ChannelGroupBank
    get() = ChannelGroupStores.bank(this)

internal fun ComputerControlDeskBlockEntity.markChannelGroupsChanged() {
    ChannelGroupStores.persist(this)
}

/** Unified discovery/resolution layer for physical controls, wire outputs and user aliases. */
object ComputerChannelRegistry {
    fun channels(owner: ComputerControlDeskBlockEntity): List<Map<String, Any>> =
        controlChannels(owner) + wireChannels(owner)

    fun ls(owner: ComputerControlDeskBlockEntity, rawPath: String = "/"): List<Map<String, Any>> {
        val path = normalize(rawPath)
        if (path == "/") return listOf(
            directory("modules", "/modules", "system"),
            directory("wires", "/wires", "system"),
            directory("groups", "/groups", "user")
        )

        if (path == "/modules") {
            return controlChannels(owner)
                .groupBy { it["moduleKey"].toString() }
                .map { (key, entries) ->
                    linkedMapOf<String, Any>(
                        "name" to key,
                        "path" to "/modules/$key",
                        "nodeType" to "group",
                        "groupType" to "module",
                        "mutable" to false,
                        "label" to entries.first()["moduleLabel"].toString(),
                        "size" to entries.size
                    )
                }
        }

        if (path.startsWith("/modules/")) {
            val segments = parts(path)
            if (segments.size == 2) {
                return controlChannels(owner).filter { it["moduleKey"] == segments[1] }
            }
        }

        if (path == "/wires") return wireChannels(owner)

        if (path == "/groups") {
            return owner.channelGroups.definitions().map(::groupNode)
        }

        if (path.startsWith("/groups/")) {
            val segments = parts(path)
            if (segments.size == 2) {
                val group = owner.channelGroups.find(segments[1]) ?: return emptyList()
                return group.bindings.map { binding -> bindingNode(owner, group, binding) }
            }
        }

        return emptyList()
    }

    fun stat(owner: ComputerControlDeskBlockEntity, reference: String): Map<String, Any>? {
        val raw = reference.trim()
        if (raw.isEmpty()) return null
        if (!raw.startsWith('/')) return channels(owner).firstOrNull { it["id"] == raw }
        val path = normalize(raw)
        if (path == "/") return directory("/", "/", "root")
        if (path == "/modules") return directory("modules", path, "system")
        if (path == "/wires") return directory("wires", path, "system")
        if (path == "/groups") return directory("groups", path, "user")

        val segments = parts(path)
        if (segments.firstOrNull() == "modules") {
            if (segments.size == 2) {
                return ls(owner, "/modules").firstOrNull { it["name"] == segments[1] }
            }
            if (segments.size == 3) {
                return controlChannels(owner).firstOrNull {
                    it["moduleKey"] == segments[1] && it["name"] == segments[2]
                }
            }
        }
        if (segments.firstOrNull() == "wires" && segments.size == 2) {
            return wireChannels(owner).firstOrNull { it["name"] == segments[1] }
        }
        if (segments.firstOrNull() == "groups") {
            val group = segments.getOrNull(1)?.let(owner.channelGroups::find) ?: return null
            if (segments.size == 2) return groupNode(group)
            if (segments.size == 3) {
                val binding = group.bindings.firstOrNull { it.name == segments[2] } ?: return null
                return bindingNode(owner, group, binding)
            }
        }
        return null
    }

    fun resolveChannel(owner: ComputerControlDeskBlockEntity, reference: String): Map<String, Any> =
        stat(owner, reference)?.takeIf { it["nodeType"] == "channel" && it["available"] != false }
            ?: throw NoSuchElementException("Unknown or unavailable channel '$reference'")

    fun read(owner: ComputerControlDeskBlockEntity, reference: String): Int {
        val channel = resolveChannel(owner, reference)
        return (channel["value"] as? Number)?.toInt()
            ?: throw IllegalStateException("Channel '$reference' has no numeric value")
    }

    private fun controlChannels(owner: ComputerControlDeskBlockEntity): List<Map<String, Any>> =
        runCatching { ControlOverrideManager.listChannels(owner) }.getOrElse { emptyList() }.mapNotNull { raw ->
            val desk = raw["desk"]?.toString() ?: return@mapNotNull null
            val deskIndex = (raw["deskIndex"] as? Number)?.toInt() ?: return@mapNotNull null
            val socket = (raw["socket"] as? Number)?.toInt() ?: return@mapNotNull null
            val socketName = raw["socketName"]?.toString() ?: socket.toString()
            val module = raw["module"]?.toString() ?: return@mapNotNull null
            val channel = raw["channel"]?.toString() ?: return@mapNotNull null
            val modulePath = module.substringAfter(':').replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val moduleKey = "desk_${deskIndex}_${socketName}_$modulePath"
            val id = "control:$desk:$socket:$module:$channel"
            linkedMapOf<String, Any>(
                "id" to id,
                "name" to channel,
                "path" to "/modules/$moduleKey/$channel",
                "nodeType" to "channel",
                "channelKind" to "control",
                "available" to true,
                "writable" to true,
                "desk" to desk,
                "deskIndex" to deskIndex,
                "socket" to socket,
                "socketName" to socketName,
                "module" to module,
                "moduleKey" to moduleKey,
                "moduleLabel" to "${module.substringAfter(':').replace('_', ' ')} · Desk $deskIndex / $socketName",
                "channel" to channel,
                "value" to ((raw["value"] as? Number)?.toInt() ?: 0),
                "overridden" to (raw["overridden"] as? Boolean ?: false)
            ).apply {
                raw["commanded"]?.let { put("commanded", it) }
                raw["owner"]?.let { put("overrideOwner", it) }
                raw["mode"]?.let { put("overrideMode", it) }
            }
        }

    private fun wireChannels(owner: ComputerControlDeskBlockEntity): List<Map<String, Any>> =
        owner.wireBank.describeChannels().mapNotNull { (name, raw) ->
            val data = raw as? Map<*, *> ?: return@mapNotNull null
            val uuid = data["id"]?.toString() ?: return@mapNotNull null
            linkedMapOf<String, Any>(
                "id" to "wire:$uuid",
                "name" to name,
                "path" to "/wires/$name",
                "nodeType" to "channel",
                "channelKind" to "wire",
                "available" to true,
                "writable" to true,
                "wireName" to name,
                "value" to ((data["value"] as? Number)?.toInt() ?: 0),
                "backend" to data["backend"].toString(),
                "connected" to (data["connected"] as? Boolean ?: false),
                "connections" to ((data["connections"] as? Number)?.toInt() ?: 0),
                "enabled" to (data["enabled"] as? Boolean ?: false)
            )
        }

    private fun bindingNode(
        owner: ComputerControlDeskBlockEntity,
        group: ChannelGroupDefinition,
        binding: ChannelGroupBinding
    ): Map<String, Any> {
        val resolved = channels(owner).firstOrNull { it["id"] == binding.targetId }
        return linkedMapOf<String, Any>().apply {
            resolved?.forEach { (key, value) -> put(key, value) }
            put("id", resolved?.get("id") ?: binding.targetId)
            put("targetId", binding.targetId)
            put("name", binding.name)
            put("path", "/groups/${group.name}/${binding.name}")
            put("nodeType", "channel")
            put("group", group.name)
            put("groupId", group.id.toString())
            put("available", resolved != null)
            if (resolved == null) put("channelKind", "missing")
        }
    }

    private fun groupNode(group: ChannelGroupDefinition): Map<String, Any> = linkedMapOf(
        "id" to group.id.toString(),
        "name" to group.name,
        "path" to "/groups/${group.name}",
        "nodeType" to "group",
        "groupType" to "user",
        "mutable" to true,
        "size" to group.bindings.size
    )

    private fun directory(name: String, path: String, type: String): Map<String, Any> = linkedMapOf(
        "name" to name,
        "path" to path,
        "nodeType" to "group",
        "groupType" to type,
        "mutable" to false
    )

    private fun normalize(raw: String): String {
        val clean = raw.trim().replace(Regex("/+"), "/")
        if (clean.isEmpty() || clean == "/") return "/"
        return "/" + clean.trim('/').lowercase(Locale.ROOT)
    }

    private fun parts(path: String): List<String> = path.trim('/').split('/').filter(String::isNotEmpty)
}
