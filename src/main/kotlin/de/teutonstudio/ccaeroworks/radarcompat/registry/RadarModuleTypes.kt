package de.teutonstudio.ccaeroworks.radarcompat.registry

import com.mred231.aeroworks.content.controls.ModuleType
import de.teutonstudio.ccaeroworks.radarcompat.display.RadarDisplayType
import de.teutonstudio.ccaeroworks.registry.CCModuleTypes

object RadarModuleTypes {
    @JvmField
    val SMALL_RADAR: ModuleType = CCModuleTypes.createExtensionModule(
        RadarDisplayType.SMALL.displayType,
        RadarDisplayType.SMALL.modulePath,
        interactive = false
    )

    @JvmField
    val LARGE_RADAR: ModuleType = CCModuleTypes.createExtensionModule(
        RadarDisplayType.LARGE.displayType,
        RadarDisplayType.LARGE.modulePath,
        interactive = true
    )

    @JvmStatic
    fun register() = Unit

    @JvmStatic
    fun radarDisplayType(moduleType: ModuleType): RadarDisplayType? {
        if (moduleType === SMALL_RADAR || moduleType == SMALL_RADAR) return RadarDisplayType.SMALL
        if (moduleType === LARGE_RADAR || moduleType == LARGE_RADAR) return RadarDisplayType.LARGE
        return when {
            CCModuleTypes.matchesExtensionModule(moduleType, RadarDisplayType.SMALL.modulePath) -> RadarDisplayType.SMALL
            CCModuleTypes.matchesExtensionModule(moduleType, RadarDisplayType.LARGE.modulePath) -> RadarDisplayType.LARGE
            else -> null
        }
    }
}
