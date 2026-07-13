package de.teutonstudio.ccaeroworks.config

import net.neoforged.neoforge.common.ModConfigSpec

object CCClientConfig {
    private val builder = ModConfigSpec.Builder()

    @JvmField
    val combinedLeverSensitivity: ModConfigSpec.DoubleValue = builder
        .comment("Lever steps applied per mouse-Y unit while combined control is held.")
        .defineInRange("combinedLeverSensitivity", 0.15, 0.001, 10.0)

    @JvmField
    val combinedLeverInvertY: ModConfigSpec.BooleanValue = builder
        .define("combinedLeverInvertY", false)

    @JvmField
    val combinedLeverPacketRate: ModConfigSpec.IntValue = builder
        .comment("Maximum combined-lever packets per second.")
        .defineInRange("combinedLeverPacketRate", 20, 1, 20)

    @JvmField
    val freezeCameraOnlyWithValidTarget: ModConfigSpec.BooleanValue = builder
        .define("freezeCameraOnlyWithValidTarget", true)

    @JvmField
    val SPEC: ModConfigSpec = builder.build()
}
