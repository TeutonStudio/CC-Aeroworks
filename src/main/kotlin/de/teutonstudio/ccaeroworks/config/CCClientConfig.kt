package de.teutonstudio.ccaeroworks.config

import net.neoforged.neoforge.common.ModConfigSpec

object CCClientConfig {
    private val builder = ModConfigSpec.Builder()

    @JvmField
    val combinedLeverSensitivity: ModConfigSpec.DoubleValue = builder
        .comment("Control steps applied per mouse-Y unit while a combined input is held.")
        .defineInRange("combinedLeverSensitivity", 0.15, 0.001, 10.0)

    @JvmField
    val combinedLeverInvertY: ModConfigSpec.BooleanValue = builder
        .define("combinedLeverInvertY", false)

    @JvmField
    val combinedLeverPacketRate: ModConfigSpec.IntValue = builder
        .comment("Maximum combined-input packets per second.")
        .defineInRange("combinedLeverPacketRate", 20, 1, 20)

    @JvmField
    val displayPointerSensitivity: ModConfigSpec.DoubleValue = builder
        .comment("Normalized display-surface movement per raw mouse unit while a display Combined channel is held.")
        .defineInRange("displayPointerSensitivity", 0.0025, 0.0001, 0.05)

    @JvmField
    val freezeCameraOnlyWithValidTarget: ModConfigSpec.BooleanValue = builder
        .define("freezeCameraOnlyWithValidTarget", true)

    @JvmField
    val SPEC: ModConfigSpec = builder.build()
}
