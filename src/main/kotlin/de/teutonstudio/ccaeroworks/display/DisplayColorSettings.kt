package de.teutonstudio.ccaeroworks.display

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity

interface DisplayColorStateAccess {
    fun ccaeroworks_getDisplayColor(socket: Int): Int?
    fun ccaeroworks_setDisplayColor(socket: Int, color: Int)
}

object DisplayColorSettings {
    const val DEFAULT_COLOR: Int = 0x72E5FF
    const val NORMAL_COLOR: Int = 0xFC7A03

    fun validate(raw: Any?): Int {
        val number = (raw as? Number)?.toDouble()
        require(number != null && number.isFinite() && number % 1.0 == 0.0 && number in 0.0..0xFFFFFF.toDouble()) {
            "Display color must be an integer in 0..16777215 (0x000000..0xFFFFFF)"
        }
        return number.toInt()
    }

    fun color(desk: ConsoleBlockEntity, socket: Int, tier: DeskDisplayTier): Int =
        if (!tier.colorCapable) NORMAL_COLOR
        else (desk as? DisplayColorStateAccess)?.ccaeroworks_getDisplayColor(socket) ?: DEFAULT_COLOR

    fun argb(color: Int): Int = 0xFF000000.toInt() or (color and 0xFFFFFF)
}
