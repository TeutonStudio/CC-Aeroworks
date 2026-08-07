package de.teutonstudio.ccaeroworks.client.display

import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.display.RadarDisplayTrackSprite
import de.teutonstudio.ccaeroworks.display.RadarDisplayType
import dev.engine_room.flywheel.lib.model.baked.PartialModel

object DeskDisplayModels {
    /**
     * These three partials are already used by the programmable desk displays
     * and are backed exclusively by CC-Aeroworks' own block-atlas textures.
     * Radar rendering deliberately reuses them instead of baking Create: Radars'
     * renderer-only monitor sprites into the Minecraft block atlas.
     */
    @JvmField
    val HORIZONTAL: PartialModel = PartialModel.of(CCAeroworks.id("block/module/display_segment_horizontal"))

    @JvmField
    val VERTICAL: PartialModel = PartialModel.of(CCAeroworks.id("block/module/display_segment_vertical"))

    @JvmField
    val PIXEL: PartialModel = PartialModel.of(CCAeroworks.id("block/module/display_pixel"))

    private val radarDisconnected = PartialModel.of(CCAeroworks.id("block/module/radar_disconnected"))

    data class Segment(val model: PartialModel, val x: Double, val z: Double)

    /**
     * Kept as a small compatibility surface for callers compiled against the
     * earlier radar-model helper. None of these values references external
     * Create: Radars monitor textures anymore.
     */
    data class RadarModels(
        val filler: PartialModel,
        val circle: PartialModel,
        val sweep: PartialModel
    )

    private val segments = mapOf(
        'a' to Segment(HORIZONTAL, 0.0, 0.14),
        'b' to Segment(VERTICAL, 0.09, 0.07),
        'c' to Segment(VERTICAL, 0.09, -0.07),
        'd' to Segment(HORIZONTAL, 0.0, -0.14),
        'e' to Segment(VERTICAL, -0.09, -0.07),
        'f' to Segment(VERTICAL, -0.09, 0.07),
        'g' to Segment(HORIZONTAL, 0.0, 0.0)
    )

    private val glyphs = mapOf(
        '0' to "abcdef", '1' to "bc", '2' to "abdeg", '3' to "abcdg", '4' to "bcfg",
        '5' to "acdfg", '6' to "acdefg", '7' to "abc", '8' to "abcdefg", '9' to "abcdfg",
        '-' to "g", ' ' to ""
    )

    @JvmStatic
    fun init() = Unit

    @JvmStatic
    fun segments(character: Char): List<Segment> = glyphs[character].orEmpty().mapNotNull(segments::get)

    @JvmStatic
    fun radar(type: RadarDisplayType): RadarModels =
        RadarModels(HORIZONTAL, HORIZONTAL, VERTICAL)

    @JvmStatic
    fun radarTrack(sprite: RadarDisplayTrackSprite): PartialModel = PIXEL

    @JvmStatic
    fun radarSelected(): PartialModel = PIXEL

    @JvmStatic
    fun radarDisconnected(): PartialModel = radarDisconnected
}
