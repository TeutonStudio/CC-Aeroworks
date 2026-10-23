package de.teutonstudio.ccaeroworks.display

data class DeskDisplayState(
    val socket: Int,
    val variant: DeskDisplayVariant,
    val text: String,
    val pixels: DeskDisplayPixels? = null,
    val partsPerBlock: Int = variant.type.partsPerBlock,
    val color: Int = DisplayColorSettings.DEFAULT_COLOR
) {
    val type: DeskDisplayType
        get() = variant.type

    val resolution: DeskDisplayResolution
        get() = DeskDisplayResolution(type.pixelWidthAt(partsPerBlock), type.pixelHeightAt(partsPerBlock))
}
