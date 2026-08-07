package de.teutonstudio.ccaeroworks.client.display

import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.display.RadarDisplayTrackSprite
import de.teutonstudio.ccaeroworks.display.RadarDisplayType
import dev.engine_room.flywheel.lib.model.baked.PartialModel

object DeskDisplayModels {
    @JvmField
    val HORIZONTAL: PartialModel = PartialModel.of(CCAeroworks.id("block/module/display_segment_horizontal"))

    @JvmField
    val VERTICAL: PartialModel = PartialModel.of(CCAeroworks.id("block/module/display_segment_vertical"))

    @JvmField
    val PIXEL: PartialModel = PartialModel.of(CCAeroworks.id("block/module/display_pixel"))

    @JvmField
    val RADAR_SMALL_BACKGROUND: PartialModel =
        PartialModel.of(CCAeroworks.id("block/module/radar_background_small"))

    @JvmField
    val RADAR_LARGE_BACKGROUND: PartialModel =
        PartialModel.of(CCAeroworks.id("block/module/radar_background_large"))

    @JvmField
    val RADAR_PIXEL: PartialModel = PartialModel.of(CCAeroworks.id("block/module/radar_pixel"))

    @JvmField
    val RADAR_SELECTED_PIXEL: PartialModel =
        PartialModel.of(CCAeroworks.id("block/module/radar_selected_pixel"))

    @JvmField
    val RADAR_SWEEP: PartialModel = PartialModel.of(CCAeroworks.id("block/module/radar_sweep"))

    private val radarDisconnected = PartialModel.of(CCAeroworks.id("block/module/radar_disconnected"))

    data class Segment(val model: PartialModel, val x: Double, val z: Double)

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
    fun radar(type: RadarDisplayType): RadarModels = when (type) {
        RadarDisplayType.SMALL -> RadarModels(RADAR_SMALL_BACKGROUND, RADAR_PIXEL, RADAR_SWEEP)
        RadarDisplayType.LARGE -> RadarModels(RADAR_LARGE_BACKGROUND, RADAR_PIXEL, RADAR_SWEEP)
    }

    @JvmStatic
    fun radarTrack(sprite: RadarDisplayTrackSprite): PartialModel = RADAR_PIXEL

    @JvmStatic
    fun radarSelected(): PartialModel = RADAR_SELECTED_PIXEL

    @JvmStatic
    fun radarDisconnected(): PartialModel = radarDisconnected
}
