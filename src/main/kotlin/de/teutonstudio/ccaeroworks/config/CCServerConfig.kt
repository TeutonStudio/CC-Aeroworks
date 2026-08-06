package de.teutonstudio.ccaeroworks.config

import de.teutonstudio.ccaeroworks.display.DeskDisplayType
import net.neoforged.neoforge.common.ModConfigSpec

object CCServerConfig {
    private val builder = ModConfigSpec.Builder()

    @JvmField
    val smallDisplayPixelWidth: ModConfigSpec.IntValue

    @JvmField
    val smallDisplayPixelHeight: ModConfigSpec.IntValue

    @JvmField
    val largeDisplayPixelWidth: ModConfigSpec.IntValue

    @JvmField
    val largeDisplayPixelHeight: ModConfigSpec.IntValue

    @JvmField
    val SPEC: ModConfigSpec

    init {
        builder
            .comment("Pixel resolutions used by programmable desk displays. This server config is synced to clients.")
            .push("display")

        builder.comment("Two-digit display resolution.").push("small")
        smallDisplayPixelWidth = builder
            .comment("Exact horizontal pixel count. There is no artificial upper bound beyond a positive signed integer.")
            .defineInRange("width", DeskDisplayType.DEFAULT_SMALL_PIXEL_WIDTH, 1, Int.MAX_VALUE)
        smallDisplayPixelHeight = builder
            .comment("Exact vertical pixel count. There is no artificial upper bound beyond a positive signed integer.")
            .defineInRange("height", DeskDisplayType.DEFAULT_SMALL_PIXEL_HEIGHT, 1, Int.MAX_VALUE)
        builder.pop()

        builder.comment("Three-digit display resolution.").push("large")
        largeDisplayPixelWidth = builder
            .comment("Exact horizontal pixel count. There is no artificial upper bound beyond a positive signed integer.")
            .defineInRange("width", DeskDisplayType.DEFAULT_LARGE_PIXEL_WIDTH, 1, Int.MAX_VALUE)
        largeDisplayPixelHeight = builder
            .comment("Exact vertical pixel count. There is no artificial upper bound beyond a positive signed integer.")
            .defineInRange("height", DeskDisplayType.DEFAULT_LARGE_PIXEL_HEIGHT, 1, Int.MAX_VALUE)
        builder.pop()

        builder.pop()
        SPEC = builder.build()
    }

    @JvmStatic
    fun pixelWidth(type: DeskDisplayType): Int = when (type) {
        DeskDisplayType.TWO_DIGIT -> smallDisplayPixelWidth.loadedOr(DeskDisplayType.DEFAULT_SMALL_PIXEL_WIDTH)
        DeskDisplayType.THREE_DIGIT -> largeDisplayPixelWidth.loadedOr(DeskDisplayType.DEFAULT_LARGE_PIXEL_WIDTH)
    }

    @JvmStatic
    fun pixelHeight(type: DeskDisplayType): Int = when (type) {
        DeskDisplayType.TWO_DIGIT -> smallDisplayPixelHeight.loadedOr(DeskDisplayType.DEFAULT_SMALL_PIXEL_HEIGHT)
        DeskDisplayType.THREE_DIGIT -> largeDisplayPixelHeight.loadedOr(DeskDisplayType.DEFAULT_LARGE_PIXEL_HEIGHT)
    }

    private fun ModConfigSpec.IntValue.loadedOr(defaultValue: Int): Int = try {
        get()
    } catch (_: IllegalStateException) {
        defaultValue
    }
}
