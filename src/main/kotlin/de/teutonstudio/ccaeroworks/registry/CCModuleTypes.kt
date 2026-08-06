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
    fun displayType(moduleType: ModuleType): DeskDisplayType? = when (moduleType) {
        TWO_DIGIT -> DeskDisplayType.TWO_DIGIT
        THREE_DIGIT -> DeskDisplayType.THREE_DIGIT
        else -> null
    }

    @JvmStatic
    fun radarDisplayType(moduleType: ModuleType): RadarDisplayType? = when (moduleType) {
        SMALL_RADAR -> RadarDisplayType.SMALL
        LARGE_RADAR -> RadarDisplayType.LARGE
        else -> null
    }
}
