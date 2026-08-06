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
import net.minecraft.world.phys.Vec3
import java.lang.reflect.Modifier
import java.util.Locale

object CCModuleTypes {
    private val INTERNAL_SOCKET: SocketType = SocketTypes.register(
        CCAeroworks.id("display_internal"),
        SocketType.builder().footprint(1)
    )

    @JvmField
    val TWO_DIGIT: ModuleType = create(DeskDisplayType.TWO_DIGIT, DeskDisplayType.TWO_DIGIT.modulePath)

    @JvmField
    val THREE_DIGIT: ModuleType = create(DeskDisplayType.THREE_DIGIT, DeskDisplayType.THREE_DIGIT.modulePath)

    @JvmField
    val SMALL_RADAR: ModuleType = create(RadarDisplayType.SMALL.displayType, RadarDisplayType.SMALL.modulePath)

    @JvmField
    val LARGE_RADAR: ModuleType = create(RadarDisplayType.LARGE.displayType, RadarDisplayType.LARGE.modulePath)

    private fun create(displayType: DeskDisplayType, modulePath: String): ModuleType {
        val model = CCAeroworks.id("block/module/$modulePath")
        val shanks = when (displayType) {
            DeskDisplayType.TWO_DIGIT -> arrayOf(AeroworksSocketTypes.SMALL, AeroworksSocketTypes.LARGE)
            DeskDisplayType.THREE_DIGIT -> arrayOf(AeroworksSocketTypes.LARGE)
        }
        return ModuleTypes.register(
            CCAeroworks.id(modulePath),
            ModuleType.builder(*shanks)
                .summary("item.cc_aeroworks.$modulePath")
                .part(
                    ModulePart.builder(model)
                        .subSocket("internal", Vec3(0.5, -1.0, 0.5), INTERNAL_SOCKET)
                )
        )
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
