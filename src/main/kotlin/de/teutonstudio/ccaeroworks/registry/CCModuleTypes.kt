package de.teutonstudio.ccaeroworks.registry

import com.mred231.aeroworks.AeroworksSocketTypes
import com.mred231.aeroworks.content.controls.module.ModulePart
import com.mred231.aeroworks.content.controls.module.ModuleType
import com.mred231.aeroworks.content.controls.module.ModuleTypes
import com.mred231.aeroworks.content.controls.channel.ControlChannel
import com.mred231.aeroworks.content.controls.socket.SocketType
import com.mred231.aeroworks.content.controls.socket.SocketTypes
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.display.DeskDisplayTier
import de.teutonstudio.ccaeroworks.display.DeskDisplayType
import de.teutonstudio.ccaeroworks.display.DeskDisplayVariant
import java.lang.reflect.Array
import java.lang.reflect.Modifier
import java.util.Locale
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3

object CCModuleTypes {
    private const val AEROWORKS_YOKE = "aeroworks:yoke"
    private const val YOKE_X_TEMPLATE = "turn"
    private const val YOKE_Y_TEMPLATE = "pitch"

    private val INTERNAL_SOCKET: SocketType = SocketTypes.register(
        CCAeroworks.id("display_internal"),
        SocketType.builder().footprint(1)
    )

    @JvmField
    val TWO_DIGIT: ModuleType = createExtensionModule(
        DeskDisplayType.TWO_DIGIT,
        moduleIdPath = DeskDisplayType.TWO_DIGIT.modulePath,
        geometryPath = "small_display",
        interactive = false
    )

    @JvmField
    val THREE_DIGIT: ModuleType = createExtensionModule(
        DeskDisplayType.THREE_DIGIT,
        moduleIdPath = DeskDisplayType.THREE_DIGIT.modulePath,
        geometryPath = "large_display",
        interactive = false
    )

    @JvmField
    val ADVANCED_TWO_DIGIT: ModuleType = createExtensionModule(
        DeskDisplayType.TWO_DIGIT,
        moduleIdPath = "advanced_two_digit_display",
        geometryPath = "advanced_small_display",
        interactive = true
    )

    @JvmField
    val ADVANCED_THREE_DIGIT: ModuleType = createExtensionModule(
        DeskDisplayType.THREE_DIGIT,
        moduleIdPath = "advanced_three_digit_display",
        geometryPath = "advanced_large_display",
        interactive = true
    )

    internal fun createExtensionModule(
        displayType: DeskDisplayType,
        moduleIdPath: String,
        geometryPath: String,
        interactive: Boolean
    ): ModuleType {
        val model = CCAeroworks.id("block/module/$geometryPath")
        val shanks = when (displayType) {
            DeskDisplayType.TWO_DIGIT -> arrayOf(AeroworksSocketTypes.SMALL, AeroworksSocketTypes.LARGE)
            DeskDisplayType.THREE_DIGIT -> arrayOf(AeroworksSocketTypes.LARGE)
        }
        val builder = ModuleType.builder(*shanks)
            .summary("item.cc_aeroworks.$geometryPath")
            .part(
                ModulePart.builder(model)
                    .subSocket("internal", Vec3(0.5, -1.0, 0.5), INTERNAL_SOCKET)
            )

        if (interactive) {
            // Aeroworks already defines the exact two-axis control semantics needed by programmable
            // displays on its Yoke. Clone those public channel records so CC-Aeroworks inherits
            // the same value range and channel options instead of maintaining a parallel format.
            // Only the channel ids change: turn -> x and pitch -> y.
            addClonedChannel(builder, YOKE_X_TEMPLATE, "x")
            addClonedChannel(builder, YOKE_Y_TEMPLATE, "y")
        }

        // Aeroworks 1.5.0 has no module-registry aliases. Retain the historical ID for mounted
        // module persistence while the model and visible summary use the current size/tier name.
        return ModuleTypes.register(CCAeroworks.id(moduleIdPath), builder)
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

        // Defensive fallback for builder implementations whose mutator is not public.
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
    fun displayVariant(moduleType: ModuleType): DeskDisplayVariant? {
        if (moduleType === TWO_DIGIT || moduleType == TWO_DIGIT) return NORMAL_TWO_DIGIT
        if (moduleType === THREE_DIGIT || moduleType == THREE_DIGIT) return NORMAL_THREE_DIGIT
        if (moduleType === ADVANCED_TWO_DIGIT || moduleType == ADVANCED_TWO_DIGIT) return ADVANCED_TWO_DIGIT_VARIANT
        if (moduleType === ADVANCED_THREE_DIGIT || moduleType == ADVANCED_THREE_DIGIT) return ADVANCED_THREE_DIGIT_VARIANT
        return moduleTypeIdentities(moduleType)
            .mapNotNull(::displayVariantFromIdentity)
            .firstOrNull()
    }

    @JvmStatic
    fun displayType(moduleType: ModuleType): DeskDisplayType? = displayVariant(moduleType)?.type

    @JvmStatic
    fun isInteractiveDisplay(moduleType: ModuleType): Boolean =
        displayVariant(moduleType)?.tier?.touchCapable == true

    private fun displayVariantFromIdentity(rawIdentity: String): DeskDisplayVariant? = when {
        matchesModuleIdentity(rawIdentity, ADVANCED_TWO_DIGIT_VARIANT.modulePath) -> ADVANCED_TWO_DIGIT_VARIANT
        matchesModuleIdentity(rawIdentity, ADVANCED_THREE_DIGIT_VARIANT.modulePath) -> ADVANCED_THREE_DIGIT_VARIANT
        matchesModuleIdentity(rawIdentity, NORMAL_TWO_DIGIT.modulePath) -> NORMAL_TWO_DIGIT
        matchesModuleIdentity(rawIdentity, NORMAL_THREE_DIGIT.modulePath) -> NORMAL_THREE_DIGIT
        else -> null
    }


    internal fun matchesExtensionModule(moduleType: ModuleType, modulePath: String): Boolean =
        moduleTypeIdentities(moduleType).any { matchesModuleIdentity(it, modulePath) }

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

    private val NORMAL_TWO_DIGIT = DeskDisplayVariant(
        DeskDisplayType.TWO_DIGIT,
        DeskDisplayTier.NORMAL,
        DeskDisplayType.TWO_DIGIT.modulePath
    )
    private val NORMAL_THREE_DIGIT = DeskDisplayVariant(
        DeskDisplayType.THREE_DIGIT,
        DeskDisplayTier.NORMAL,
        DeskDisplayType.THREE_DIGIT.modulePath
    )
    private val ADVANCED_TWO_DIGIT_VARIANT = DeskDisplayVariant(
        DeskDisplayType.TWO_DIGIT,
        DeskDisplayTier.ADVANCED,
        "advanced_two_digit_display"
    )
    private val ADVANCED_THREE_DIGIT_VARIANT = DeskDisplayVariant(
        DeskDisplayType.THREE_DIGIT,
        DeskDisplayTier.ADVANCED,
        "advanced_three_digit_display"
    )
}
