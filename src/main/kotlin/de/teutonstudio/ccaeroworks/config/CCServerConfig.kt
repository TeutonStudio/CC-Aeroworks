package de.teutonstudio.ccaeroworks.config

import de.teutonstudio.ccaeroworks.display.DeskDisplayType
import net.neoforged.neoforge.common.ModConfigSpec

object CCServerConfig {
    private val builder = ModConfigSpec.Builder()

    @JvmField
    val displayPartsPerBlock: ModConfigSpec.IntValue

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
            .comment(
                "Programmable display pixel density in parts per block (PPB). " +
                    "Minecraft's normal 16x16 block texture density is 16 PPB; all display axes " +
                    "derive from the same value so rendered pixels stay square."
            )
            .push("display")

        displayPartsPerBlock = builder
            .comment(
                "Pixel density in parts per block. The default 256 PPB gives 112x112 pixels on " +
                    "small displays and 160x112 pixels on large displays."
            )
            .defineInRange(
                "ppb",
                DeskDisplayType.DEFAULT_PARTS_PER_BLOCK,
                DeskDisplayType.VANILLA_PARTS_PER_BLOCK,
                Int.MAX_VALUE
            )

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
    fun displayPartsPerBlockValue(): Int =
        displayPartsPerBlock.loadedOr(DeskDisplayType.DEFAULT_PARTS_PER_BLOCK)

    @JvmStatic
    fun pixelWidth(type: DeskDisplayType): Int = type.pixelWidthAt(displayPartsPerBlockValue())

    @JvmStatic
    fun pixelHeight(type: DeskDisplayType): Int = type.pixelHeightAt(displayPartsPerBlockValue())

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
