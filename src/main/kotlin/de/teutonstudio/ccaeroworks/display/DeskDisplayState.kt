package de.teutonstudio.ccaeroworks.display

data class DeskDisplayState(
    val socket: Int,
    val type: DeskDisplayType,
    val text: String,
    val pixels: DeskDisplayPixels? = null,
    val partsPerBlock: Int = type.partsPerBlock
) {
    val resolution: DeskDisplayResolution
        get() = DeskDisplayResolution(type.pixelWidthAt(partsPerBlock), type.pixelHeightAt(partsPerBlock))
}
