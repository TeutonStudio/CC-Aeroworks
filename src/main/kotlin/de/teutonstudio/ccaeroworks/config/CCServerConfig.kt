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
    val telemetryMaxSources: ModConfigSpec.IntValue

    @JvmField
    val telemetryMaxListEntries: ModConfigSpec.IntValue

    @JvmField
    val telemetryStaleAfterTicks: ModConfigSpec.IntValue

    @JvmField
    val telemetryValidationIntervalTicks: ModConfigSpec.IntValue

    @JvmField
    val telemetryDockScanIntervalTicks: ModConfigSpec.IntValue

    @JvmField
    val SPEC: ModConfigSpec

    init {
        builder
            .comment("Pixel resolutions used by programmable desk displays. This server config is synced to clients.")
            .push("display")

        builder.comment("Small display resolution.").push("small")
        smallDisplayPixelWidth = builder
            .comment("Exact horizontal pixel count. There is no artificial upper bound beyond a positive signed integer.")
            .defineInRange("width", DeskDisplayType.DEFAULT_SMALL_PIXEL_WIDTH, 1, Int.MAX_VALUE)
        smallDisplayPixelHeight = builder
            .comment("Exact vertical pixel count. There is no artificial upper bound beyond a positive signed integer.")
            .defineInRange("height", DeskDisplayType.DEFAULT_SMALL_PIXEL_HEIGHT, 1, Int.MAX_VALUE)
        builder.pop()

        builder.comment("Large display resolution.").push("large")
        largeDisplayPixelWidth = builder
            .comment("Exact horizontal pixel count. There is no artificial upper bound beyond a positive signed integer.")
            .defineInRange("width", DeskDisplayType.DEFAULT_LARGE_PIXEL_WIDTH, 1, Int.MAX_VALUE)
        largeDisplayPixelHeight = builder
            .comment("Exact vertical pixel count. There is no artificial upper bound beyond a positive signed integer.")
            .defineInRange("height", DeskDisplayType.DEFAULT_LARGE_PIXEL_HEIGHT, 1, Int.MAX_VALUE)
        builder.pop()

        builder.pop()

        builder
            .comment("Create Display Link telemetry exposed to Computer Control Desks.")
            .push("telemetry")
        telemetryMaxSources = builder
            .comment("Maximum number of Display Link telemetry sources retained by one endpoint.")
            .defineInRange("maxSourcesPerEndpoint", 128, 1, 4096)
        telemetryMaxListEntries = builder
            .comment("Maximum entries returned for item/fluid list telemetry. Counts still report the full list size.")
            .defineInRange("maxListEntries", 128, 1, 4096)
        telemetryStaleAfterTicks = builder
            .comment("Age in ticks after which a source is marked stale without being removed.")
            .defineInRange("staleAfterTicks", 220, 1, 72_000)
        telemetryValidationIntervalTicks = builder
            .comment("Interval in ticks for validating whether known Display Links still exist and target the endpoint.")
            .defineInRange("validationIntervalTicks", 20, 1, 1200)
        telemetryDockScanIntervalTicks = builder
            .comment("Interval in ticks for rescanning the local Sable sublevel for Simulated docking connectors.")
            .defineInRange("dockScanIntervalTicks", 40, 1, 1200)
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

    @JvmStatic
    fun telemetryMaxSourcesValue(): Int = telemetryMaxSources.loadedOr(128)

    @JvmStatic
    fun telemetryMaxListEntriesValue(): Int = telemetryMaxListEntries.loadedOr(128)

    @JvmStatic
    fun telemetryStaleAfterTicksValue(): Int = telemetryStaleAfterTicks.loadedOr(220)

    @JvmStatic
    fun telemetryValidationIntervalTicksValue(): Int = telemetryValidationIntervalTicks.loadedOr(20)

    @JvmStatic
    fun telemetryDockScanIntervalTicksValue(): Int = telemetryDockScanIntervalTicks.loadedOr(40)

    private fun ModConfigSpec.IntValue.loadedOr(defaultValue: Int): Int = try {
        get()
    } catch (_: IllegalStateException) {
        defaultValue
    }
}
