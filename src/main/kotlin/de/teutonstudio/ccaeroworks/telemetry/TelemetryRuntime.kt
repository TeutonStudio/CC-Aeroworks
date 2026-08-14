package de.teutonstudio.ccaeroworks.telemetry

import com.simibubi.create.api.registry.CreateBuiltInRegistries
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext
import de.teutonstudio.ccaeroworks.computer.ComputerControlDeskBlockEntity
import de.teutonstudio.ccaeroworks.config.CCServerConfig
import dev.ryanhcode.sable.Sable
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.MutableComponent
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.neoforged.fml.ModList
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.WeakHashMap

internal object TelemetryRuntime {
    private val endpointsByLevel = WeakHashMap<ServerLevel, LinkedHashMap<String, TelemetryEndpointState>>()
    private val lastValidationTick = WeakHashMap<ServerLevel, Long>()

    @Synchronized
    fun accept(target: BlockEntity, context: DisplayLinkContext, text: List<MutableComponent>) {
        val level = target.level as? ServerLevel ?: return
        val endpoint = endpoint(target, create = true) ?: return
        val sourceId = TelemetryIdentity.sourceId(level, context.blockEntity().blockPos)
        val decoded = CreateTelemetryDecoder.decode(context, text)
        val existing = endpoint.sources[sourceId]
        if (existing == null && endpoint.sources.size >= CCServerConfig.telemetryMaxSourcesValue()) return

        val createLabel = context.sourceConfig()
            .getString("Label")
            .trim()
            .takeIf(String::isNotEmpty)
        val changed = existing == null ||
            existing.sourceType != decoded.sourceType ||
            existing.supported != decoded.supported ||
            existing.payload != decoded.payload ||
            existing.displayText != decoded.displayText ||
            existing.createLabel != createLabel
        val revision = when {
            existing == null -> 1L
            changed -> existing.revision + 1L
            else -> existing.revision
        }
        endpoint.sources[sourceId] = TelemetrySourceState(
            id = sourceId,
            sourceType = decoded.sourceType,
            sourcePos = context.sourcePos.immutable(),
            linkPos = context.blockEntity().blockPos.immutable(),
            supported = decoded.supported,
            payload = decoded.payload,
            displayText = decoded.displayText,
            createLabel = createLabel,
            lastSeenTick = level.gameTime,
            revision = revision
        )
    }

    @Synchronized
    fun endpoint(target: BlockEntity, create: Boolean = false): TelemetryEndpointState? {
        val level = target.level as? ServerLevel ?: return null
        val endpointId = TelemetryIdentity.endpointId(level, target.blockPos)
        val endpoints = endpointsByLevel.getOrPut(level) { linkedMapOf() }
        endpoints[endpointId]?.let { return it }
        if (!create) return null
        val kind = if (target is ComputerControlDeskBlockEntity) {
            TelemetryEndpointKind.COMPUTER
        } else {
            TelemetryEndpointKind.DOCK
        }
        return TelemetryEndpointState(
            id = endpointId,
            kind = kind,
            targetPos = target.blockPos.immutable(),
            dimension = level.dimension().location().toString(),
            subLevelId = TelemetryIdentity.subLevelId(level, target.blockPos)
        ).also { endpoints[endpointId] = it }
    }

    @Synchronized
    fun endpoint(level: ServerLevel, endpointId: String): TelemetryEndpointState? =
        endpointsByLevel[level]?.get(endpointId)

    @Synchronized
    fun source(target: BlockEntity, idOrName: String): TelemetrySourceState? {
        val endpoint = endpoint(target) ?: return null
        return resolveSource(target, endpoint, idOrName)
    }

    @Synchronized
    fun rename(target: BlockEntity, idOrName: String, alias: String): TelemetrySourceState? {
        val endpoint = endpoint(target) ?: return null
        val source = resolveSource(target, endpoint, idOrName) ?: return null
        val normalized = alias.trim()
        require(normalized.isNotEmpty()) { "Telemetry alias must not be blank" }
        val duplicate = endpoint.sources.values.any { candidate ->
            candidate.id != source.id && (
                aliasFor(target, candidate.id)?.equals(normalized, ignoreCase = true) == true ||
                    candidate.createLabel?.equals(normalized, ignoreCase = true) == true
                )
        }
        require(!duplicate) { "Telemetry name '$normalized' is already used by this endpoint" }
        setAlias(target, source.id, normalized)
        return source
    }

    @Synchronized
    fun clearAlias(target: BlockEntity, idOrName: String): TelemetrySourceState? {
        val endpoint = endpoint(target) ?: return null
        val source = resolveSource(target, endpoint, idOrName) ?: return null
        setAlias(target, source.id, null)
        return source
    }

    @Synchronized
    fun describeSources(target: BlockEntity): Map<String, Any> {
        val level = target.level as? ServerLevel ?: return emptyMap()
        val endpoint = endpoint(target) ?: return emptyMap()
        val staleAfter = CCServerConfig.telemetryStaleAfterTicksValue().toLong()
        return endpoint.sources.values.associateTo(linkedMapOf()) { source ->
            source.id to source.toLua(
                dimension = endpoint.dimension,
                subLevelId = endpoint.subLevelId,
                alias = aliasFor(target, source.id),
                gameTime = level.gameTime,
                staleAfterTicks = staleAfter
            )
        }
    }

    @Synchronized
    fun describeSource(target: BlockEntity, idOrName: String): Map<String, Any>? {
        val level = target.level as? ServerLevel ?: return null
        val endpoint = endpoint(target) ?: return null
        val source = resolveSource(target, endpoint, idOrName) ?: return null
        return source.toLua(
            dimension = endpoint.dimension,
            subLevelId = endpoint.subLevelId,
            alias = aliasFor(target, source.id),
            gameTime = level.gameTime,
            staleAfterTicks = CCServerConfig.telemetryStaleAfterTicksValue().toLong()
        )
    }

    @Synchronized
    fun status(target: BlockEntity): Map<String, Any> {
        val level = target.level as? ServerLevel
        val endpoint = endpoint(target)
        if (level == null || endpoint == null) {
            return linkedMapOf(
                "sourceCount" to 0,
                "freshCount" to 0,
                "staleCount" to 0
            )
        }
        val staleAfter = CCServerConfig.telemetryStaleAfterTicksValue().toLong()
        val stale = endpoint.sources.values.count { level.gameTime - it.lastSeenTick > staleAfter }
        return linkedMapOf(
            "endpointId" to endpoint.id,
            "sourceCount" to endpoint.sources.size,
            "freshCount" to endpoint.sources.size - stale,
            "staleCount" to stale,
            "dimension" to endpoint.dimension
        ).apply {
            endpoint.subLevelId?.let { put("subLevelId", it) }
        }
    }

    @Synchronized
    fun sourceRevisions(target: BlockEntity): Map<String, Long> =
        endpoint(target)?.sources?.mapValuesTo(linkedMapOf()) { it.value.revision }.orEmpty()

    @Synchronized
    fun validate(level: ServerLevel) {
        val interval = CCServerConfig.telemetryValidationIntervalTicksValue().toLong()
        val previous = lastValidationTick[level] ?: Long.MIN_VALUE
        if (previous != Long.MIN_VALUE && level.gameTime - previous < interval) return
        lastValidationTick[level] = level.gameTime
        val endpoints = endpointsByLevel[level] ?: return

        endpoints.values.forEach { endpoint ->
            val iterator = endpoint.sources.iterator()
            while (iterator.hasNext()) {
                val (_, source) = iterator.next()
                if (!level.isLoaded(source.linkPos)) continue
                val link = level.getBlockEntity(source.linkPos) as? DisplayLinkBlockEntity
                if (link == null || link.getTargetPosition() != endpoint.targetPos || link.activeSource == null) {
                    iterator.remove()
                    continue
                }
                val currentType = CreateBuiltInRegistries.DISPLAY_SOURCE.getKey(link.activeSource)?.toString()
                if (currentType != source.sourceType) iterator.remove()
            }
        }
        endpoints.entries.removeIf { (_, endpoint) ->
            endpoint.sources.isEmpty() && level.isLoaded(endpoint.targetPos) && level.getBlockEntity(endpoint.targetPos) == null
        }
    }

    fun aliasFor(target: BlockEntity, sourceId: String): String? {
        val aliases = aliases(target, create = false) ?: return null
        return aliases.getString(sourceId).trim().takeIf(String::isNotEmpty)
    }

    private fun resolveSource(
        target: BlockEntity,
        endpoint: TelemetryEndpointState,
        idOrName: String
    ): TelemetrySourceState? {
        endpoint.sources[idOrName]?.let { return it }
        return endpoint.sources.values.firstOrNull { source ->
            aliasFor(target, source.id)?.equals(idOrName, ignoreCase = true) == true ||
                source.createLabel?.equals(idOrName, ignoreCase = true) == true
        }
    }

    private fun setAlias(target: BlockEntity, sourceId: String, alias: String?) {
        val aliases = aliases(target, create = true) ?: return
        if (alias == null) aliases.remove(sourceId) else aliases.putString(sourceId, alias)
        val root = target.persistentData.getCompound(PERSISTENT_ROOT)
        root.put(PERSISTENT_ALIASES, aliases)
        target.persistentData.put(PERSISTENT_ROOT, root)
        target.setChanged()
    }

    private fun aliases(target: BlockEntity, create: Boolean): CompoundTag? {
        val data = target.persistentData
        if (!create && !data.contains(PERSISTENT_ROOT)) return null
        val root = data.getCompound(PERSISTENT_ROOT)
        if (!create && !root.contains(PERSISTENT_ALIASES)) return null
        val aliases = root.getCompound(PERSISTENT_ALIASES)
        if (create) {
            root.put(PERSISTENT_ALIASES, aliases)
            data.put(PERSISTENT_ROOT, root)
        }
        return aliases
    }

    private const val PERSISTENT_ROOT = "cc_aeroworks_telemetry"
    private const val PERSISTENT_ALIASES = "aliases"
}

internal object TelemetryIdentity {
    fun sourceId(level: ServerLevel, linkPos: BlockPos): String = stable("source", level, linkPos)
    fun endpointId(level: ServerLevel, targetPos: BlockPos): String = stable("endpoint", level, targetPos)

    fun subLevelId(level: ServerLevel, pos: BlockPos): String? {
        if (!ModList.get().isLoaded("sable")) return null
        return runCatching { Sable.HELPER.getContaining(level, pos)?.uniqueId?.toString() }.getOrNull()
    }

    private fun stable(kind: String, level: ServerLevel, pos: BlockPos): String {
        val space = subLevelId(level, pos)?.let { "sable:$it" }
            ?: "world:${level.dimension().location()}"
        val value = "$kind|$space|${pos.x},${pos.y},${pos.z}"
        return UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8)).toString()
    }
}
