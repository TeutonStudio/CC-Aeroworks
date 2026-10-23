package de.teutonstudio.ccaeroworks.display

enum class DeskDisplayTier(
    val serializedName: String,
    val touchCapable: Boolean,
    val colorCapable: Boolean
) {
    NORMAL("normal", touchCapable = false, colorCapable = false),
    ADVANCED("advanced", touchCapable = true, colorCapable = true)
}

data class DeskDisplayVariant(
    val type: DeskDisplayType,
    val tier: DeskDisplayTier,
    val modulePath: String
)
