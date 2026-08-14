package de.teutonstudio.ccaeroworks.registry

import com.mred231.aeroworks.AeroworksSocketTypes
import com.mred231.aeroworks.content.controls.ModulePart
import com.mred231.aeroworks.content.controls.ModuleType
import com.mred231.aeroworks.content.controls.ModuleTypes
import com.mred231.aeroworks.content.controls.SocketType
import com.mred231.aeroworks.content.controls.SocketTypes
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.display.DeskDisplayType
import de.teutonstudio.ccaeroworks.display.RadarDisplayType
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import java.lang.reflect.Array
import java.lang.reflect.Modifier
import java.util.Locale

object CCModuleTypes {
    private const val AEROWORKS_YOKE = "aeroworks:yoke"
    private const val YOKE_X_TEMPLATE = "turn"
    private const val YOKE_Y_TEMPLATE = "pitch"

    private val INTERNAL_SOCKET: SocketType = SocketTypes.register(
        CCAeroworks.id("display_internal"),
        SocketType.builder().footprint(1)
    )

    @JvmField
    val TWO_DIGIT: ModuleType = create(
        DeskDisplayType.TWO_DIGIT,
        DeskDisplayType.TWO_DIGIT.modulePath,
        interactive = false
    )

    @JvmField
    val THREE_DIGIT: ModuleType = create(
        DeskDisplayType.THREE_DIGIT,
        DeskDisplayType.THREE_DIGIT.modulePath,
        interactive = true
    )

    @JvmField
    val SMALL_RADAR: ModuleType = create(
        RadarDisplayType.SMALL.displayType,
        RadarDisplayType.SMALL.modulePath,
        interactive = false
    )

    @JvmField
    val LARGE_RADAR: ModuleType = create(
        RadarDisplayType.LARGE.displayType,
        RadarDisplayType.LARGE.modulePath,
        interactive = true
    )

    private fun create(displayType: DeskDisplayType, modulePath: String, interactive: Boolean): ModuleType {
        val model = CCAeroworks.id("block/module/$modulePath")
        val shanks = when (displayType) {
            DeskDisplayType.TWO_DIGIT -> arrayOf(AeroworksSocketTypes.SMALL, AeroworksSocketTypes.LARGE)
            DeskDisplayType.THREE_DIGIT -> arrayOf(AeroworksSocketTypes.LARGE)
        }
        val builder = ModuleType.builder(*shanks)
            .summary("item.cc_aeroworks.$modulePath")
            .part(
                ModulePart.builder(model)
                    .subSocket("internal", Vec3(0.5, -1.0, 0.5), INTERNAL_SOCKET)
            )

        if (interactive) {
            // Aeroworks already defines the exact two-axis control semantics needed by the large
            // displays on its Yoke. Clone those public channel records so CC-Aeroworks inherits
            // the same value range and channel options instead of maintaining a parallel format.
            // Only the channel ids change: turn -> x and pitch -> y.
            addClonedChannel(builder, YOKE_X_TEMPLATE, "x")
            addClonedChannel(builder, YOKE_Y_TEMPLATE, "y")
        }

        return ModuleTypes.register(CCAeroworks.id(modulePath), builder)
    }

    private fun addClonedChannel(builder: ModuleType.Builder, templateId: String, newId: String) {
        val yokeId = ResourceLocation.parse(AEROWORKS_YOKE)
        val yoke = ModuleTypes.get(yokeId)
            ?: error("Create: Aeroworks did not register $AEROWORKS_YOKE before CC-Aeroworks display modules")
        val template = yoke.channels().firstOrNull { it.id() == templateId }
            ?: error("Create: Aeroworks yoke is missing expected channel '$templateId'")
        val clone = cloneRecordWithId(template, newId)
        appendChannel(builder, clone)
    }

    /**
     * ControlChannel is supplied by Aeroworks. Cloning the record reflectively keeps this extension
     * tied to Aeroworks' own channel schema even if that record gains another component in a later
     * compatible 1.3.x build. Every component is copied verbatim except the public channel id.
     */
    private fun cloneRecordWithId(template: Any, newId: String): Any {
        val type = template.javaClass
        check(type.isRecord) { "Aeroworks ControlChannel is no longer a record: ${type.name}" }
        val components = type.recordComponents
        val idIndex = components.indexOfFirst { it.name == "id" }
        check(idIndex >= 0) { "Aeroworks ControlChannel record has no 'id' component" }

        val constructor = type.getDeclaredConstructor(*components.map { it.type }.toTypedArray())
        constructor.trySetAccessible()
        val arguments = components.mapIndexed { index, component ->
            if (index == idIndex) newId else component.accessor.invoke(template)
        }.toTypedArray()
        return constructor.newInstance(*arguments)
    }

    private fun appendChannel(builder: ModuleType.Builder, channel: Any) {
        val builderType = builder.javaClass
        val channelType = channel.javaClass

        // Prefer the public builder API. Accept either channel(ControlChannel) or a vararg/array
        // channels(ControlChannel...) shape so the code does not guess Aeroworks' method spelling.
        val method = builderType.methods.firstOrNull { candidate ->
            candidate.parameterCount == 1 &&
                candidate.name.lowercase(Locale.ROOT).contains("channel") &&
                (
                    candidate.parameterTypes[0].isAssignableFrom(channelType) ||
                        (candidate.parameterTypes[0].isArray &&
                            candidate.parameterTypes[0].componentType.isAssignableFrom(channelType))
                    )
        }
        if (method != null) {
            val parameter = method.parameterTypes[0]
            if (parameter.isArray) {
                val values = Array.newInstance(parameter.componentType, 1)
                Array.set(values, 0, channel)
                method.invoke(builder, values)
            } else {
                method.invoke(builder, channel)
            }
            return
        }

        // Defensive fallback for the 1.3.0 builder implementation if its mutator is not public.
        val field = generateSequence<Class<*>>(builderType) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .firstOrNull { field ->
                field.name.lowercase(Locale.ROOT).contains("channel") &&
                    java.util.Collection::class.java.isAssignableFrom(field.type)
            }
            ?: error("Aeroworks ModuleType.Builder exposes no channel mutator or channel collection")
        field.trySetAccessible()
        @Suppress("UNCHECKED_CAST")
        val collection = field.get(builder) as? MutableCollection<Any>
            ?: error("Aeroworks ModuleType.Builder channel collection is not mutable")
        collection.add(channel)
    }

    @JvmStatic
    fun register() = Unit

    @JvmStatic
    fun displayType(moduleType: ModuleType): DeskDisplayType? {
        if (moduleType === TWO_DIGIT || moduleType == TWO_DIGIT) return DeskDisplayType.TWO_DIGIT
        if (moduleType === THREE_DIGIT || moduleType == THREE_DIGIT) return DeskDisplayType.THREE_DIGIT
        return moduleTypeIdentities(moduleType)
            .mapNotNull(::displayTypeFromIdentity)
            .firstOrNull()
    }

    @JvmStatic
    fun radarDisplayType(moduleType: ModuleType): RadarDisplayType? {
        if (moduleType === SMALL_RADAR || moduleType == SMALL_RADAR) return RadarDisplayType.SMALL
        if (moduleType === LARGE_RADAR || moduleType == LARGE_RADAR) return RadarDisplayType.LARGE
        return moduleTypeIdentities(moduleType)
            .mapNotNull(::radarDisplayTypeFromIdentity)
            .firstOrNull()
    }

    private fun displayTypeFromIdentity(rawIdentity: String): DeskDisplayType? = when {
        matchesModuleIdentity(rawIdentity, DeskDisplayType.TWO_DIGIT.modulePath) -> DeskDisplayType.TWO_DIGIT
        matchesModuleIdentity(rawIdentity, DeskDisplayType.THREE_DIGIT.modulePath) -> DeskDisplayType.THREE_DIGIT
        else -> null
    }

    private fun radarDisplayTypeFromIdentity(rawIdentity: String): RadarDisplayType? = when {
        matchesModuleIdentity(rawIdentity, RadarDisplayType.SMALL.modulePath) -> RadarDisplayType.SMALL
        matchesModuleIdentity(rawIdentity, RadarDisplayType.LARGE.modulePath) -> RadarDisplayType.LARGE
        else -> null
    }

    private fun matchesModuleIdentity(rawIdentity: String, modulePath: String): Boolean {
        val identity = rawIdentity.lowercase(Locale.ROOT)
        val registryId = "${CCAeroworks.MOD_ID}:$modulePath"
        val summaryKey = "item.${CCAeroworks.MOD_ID}.$modulePath"
        return identity == registryId ||
            identity == summaryKey ||
            identity.contains(registryId) ||
            identity.contains(summaryKey)
    }

    private fun moduleTypeIdentities(moduleType: ModuleType): Sequence<String> = sequence {
        yield(moduleType.toString())

        for (methodName in listOf("id", "getId", "key", "getKey", "registryName", "getRegistryName")) {
            invokeRegistryIdentity(moduleType, methodName)?.toString()?.let { yield(it) }
        }

        for (methodName in listOf("id", "getId", "key", "getKey", "registryName", "getRegistryName", "summary", "getSummary")) {
            invokeInstanceIdentity(moduleType, methodName)?.toString()?.let { yield(it) }
        }

        for (identity in declaredFieldIdentities(moduleType)) {
            yield(identity)
        }
    }

    private fun declaredFieldIdentities(moduleType: ModuleType): Sequence<String> = sequence {
        var current: Class<*>? = moduleType.javaClass
        while (current != null) {
            val type = current
            for (field in type.declaredFields) {
                if (Modifier.isStatic(field.modifiers)) continue
                val fieldName = field.name.lowercase(Locale.ROOT)
                if (listOf("id", "key", "name", "summary", "registry", "model").none(fieldName::contains)) continue
                val value = runCatching {
                    field.trySetAccessible()
                    field.get(moduleType)
                }.getOrNull() ?: continue
                yield(value.toString())
            }
            current = type.superclass
        }
    }

    private fun invokeRegistryIdentity(moduleType: ModuleType, methodName: String): Any? = runCatching {
        val method = ModuleTypes::class.java.methods.firstOrNull { candidate ->
            candidate.name == methodName &&
                Modifier.isStatic(candidate.modifiers) &&
                candidate.parameterCount == 1 &&
                candidate.parameterTypes[0].isAssignableFrom(moduleType.javaClass)
        } ?: return@runCatching null
        method.invoke(null, moduleType)
    }.getOrNull()

    private fun invokeInstanceIdentity(moduleType: ModuleType, methodName: String): Any? = runCatching {
        val method = moduleType.javaClass.methods.firstOrNull { candidate ->
            candidate.name == methodName && candidate.parameterCount == 0
        } ?: return@runCatching null
        method.invoke(moduleType)
    }.getOrNull()
}
