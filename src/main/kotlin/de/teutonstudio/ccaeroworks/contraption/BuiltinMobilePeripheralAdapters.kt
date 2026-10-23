package de.teutonstudio.ccaeroworks.contraption

import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.api.mobile.MobilePeripheralAdapter
import de.teutonstudio.ccaeroworks.api.mobile.MobilePeripheralAdapters
import de.teutonstudio.ccaeroworks.api.mobile.MobilePeripheralContext
import de.teutonstudio.ccaeroworks.api.mobile.MobilePeripheralInstance
import de.teutonstudio.ccaeroworks.train.TrainControlConsoleIntegration
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.state.BlockState

object BuiltinMobilePeripheralAdapters {
    private var registered = false

    @Synchronized
    fun register() {
        if (registered) return
        MobilePeripheralAdapters.register(object : MobilePeripheralAdapter {
            override fun id() = CCAeroworks.id("contraption_control")
            override fun matches(state: BlockState, blockEntityData: CompoundTag): Boolean =
                TrainControlConsoleIntegration.isContraptionControls(state)
            override fun create(context: MobilePeripheralContext): MobilePeripheralInstance =
                MobileContraptionControlPeripheral(context)
        })
        MobilePeripheralAdapters.register(VirtualAeroworksDeskAdapter)
        registerCc("wireless_modem", "wireless_modem_normal", "wireless_modem_advanced")
        registerCc("wired_network", "cable", "wired_modem_full")
        registerCc("speaker", "speaker")
        registerCc("monitor", "monitor_normal", "monitor_advanced")
        registerCc("disk_drive", "disk_drive")
        registerCc("printer", "printer")
        registerCc("redstone_relay", "redstone_relay")
        registered = true
    }

    private fun registerCc(name: String, vararg paths: String) {
        MobilePeripheralAdapters.register(
            VirtualCcPeripheralAdapter(
                CCAeroworks.id("computercraft_$name"),
                paths.mapTo(linkedSetOf()) {
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("computercraft", it)
                }
            )
        )
    }
}
