package de.teutonstudio.ccaeroworks.display

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity
import de.teutonstudio.ccaeroworks.config.CCServerConfig

/** Slot settings are saved on the console independently of its current text or pixel contents. */
interface DisplayResolutionStateAccess {
    fun ccaeroworks_getDisplayPpb(socket: Int): Int?
    fun ccaeroworks_setDisplayPpb(socket: Int, ppb: Int)
}

object DisplayResolutionSettings {
    fun effectivePpb(global: Boolean, configured: Int, maximum: Int, individual: Int?): Int =
        if (global) configured else (individual ?: configured).coerceIn(16, maximum)

    fun validateIndividualPpb(raw: Any?, maximum: Int): Int {
        val number = (raw as? Number)?.toDouble()
        require(number != null && number.isFinite() && number % 1.0 == 0.0 && number >= 16 && number <= maximum) {
            "Display PPB must be an integer in 16..$maximum"
        }
        return number.toInt()
    }

    fun partsPerBlock(desk: ConsoleBlockEntity, socket: Int): Int = effectivePpb(
        CCServerConfig.displayUseGlobalResolutionValue(),
        CCServerConfig.displayPartsPerBlockValue(),
        CCServerConfig.displayMaxIndividualPpbValue(),
        (desk as? DisplayResolutionStateAccess)?.ccaeroworks_getDisplayPpb(socket)
    )
}
