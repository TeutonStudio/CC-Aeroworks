package de.teutonstudio.ccaeroworks.registry

import com.mred231.aeroworks.AeroworksSocketTypes
import com.mred231.aeroworks.content.controls.ModulePart
import com.mred231.aeroworks.content.controls.ModuleType
import com.mred231.aeroworks.content.controls.ModuleTypes
import com.mred231.aeroworks.content.controls.SocketType
import com.mred231.aeroworks.content.controls.SocketTypes
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.display.DeskDisplayType
import net.minecraft.world.phys.Vec3

object CCModuleTypes {
    private val INTERNAL_SOCKET: SocketType = SocketTypes.register(
        CCAeroworks.id("display_internal"),
        SocketType.builder().footprint(1)
    )

    @JvmField
    val TWO_DIGIT: ModuleType = create(DeskDisplayType.TWO_DIGIT)

    @JvmField
    val THREE_DIGIT: ModuleType = create(DeskDisplayType.THREE_DIGIT)

    private fun create(displayType: DeskDisplayType): ModuleType {
        val model = CCAeroworks.id("block/module/${displayType.modulePath}")
        return ModuleTypes.register(
            CCAeroworks.id(displayType.modulePath),
            ModuleType.builder(AeroworksSocketTypes.SMALL)
                .summary("item.cc_aeroworks.${displayType.modulePath}")
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
}
