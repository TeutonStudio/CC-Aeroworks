package de.teutonstudio.ccaeroworks.client.display

import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.display.RadarDisplayTrackSprite
import de.teutonstudio.ccaeroworks.display.RadarDisplayType
import dev.engine_room.flywheel.lib.model.baked.PartialModel

object DeskDisplayModels {
    private val horizontal = PartialModel.of(CCAeroworks.id("block/module/display_segment_horizontal"))
    private val vertical = PartialModel.of(CCAeroworks.id("block/module/display_segment_vertical"))
    @JvmField
    val PIXEL: PartialModel = PartialModel.of(CCAeroworks.id("block/module/display_pixel"))

    private val radarSmallFiller = PartialModel.of(CCAeroworks.id("block/module/radar_small_filler"))
    private val radarSmallCircle = PartialModel.of(CCAeroworks.id("block/module/radar_small_circle"))
    private val radarSmallSweep = PartialModel.of(CCAeroworks.id("block/module/radar_small_sweep"))
    private val radarLargeFiller = PartialModel.of(CCAeroworks.id("block/module/radar_large_filler"))
    private val radarLargeCircle = PartialModel.of(CCAeroworks.id("block/module/radar_large_circle"))
    private val radarLargeSweep = PartialModel.of(CCAeroworks.id("block/module/radar_large_sweep"))
    private val radarTrackContraption = PartialModel.of(CCAeroworks.id("block/module/radar_track_contraption"))
    private val radarTrackPlayer = PartialModel.of(CCAeroworks.id("block/module/radar_track_player"))
    private val radarTrackProjectile = PartialModel.of(CCAeroworks.id("block/module/radar_track_projectile"))
    private val radarTrackEntity = PartialModel.of(CCAeroworks.id("block/module/radar_track_entity"))
    private val radarTrackSelected = PartialModel.of(CCAeroworks.id("block/module/radar_track_selected"))
    private val radarDisconnected = PartialModel.of(CCAeroworks.id("block/module/radar_disconnected"))

    data class Segment(val model: PartialModel, val x: Double, val z: Double)

    data class RadarModels(
        val filler: PartialModel,
        val circle: PartialModel,
        val sweep: PartialModel
    )

    private val segments = mapOf(
        'a' to Segment(horizontal, 0.0, 0.14),
        'b' to Segment(vertical, 0.09, 0.07),
        'c' to Segment(vertical, 0.09, -0.07),
        'd' to Segment(horizontal, 0.0, -0.14),
        'e' to Segment(vertical, -0.09, -0.07),
        'f' to Segment(vertical, -0.09, 0.07),
        'g' to Segment(horizontal, 0.0, 0.0)
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
        RadarDisplayType.SMALL -> RadarModels(radarSmallFiller, radarSmallCircle, radarSmallSweep)
        RadarDisplayType.LARGE -> RadarModels(radarLargeFiller, radarLargeCircle, radarLargeSweep)
    }

    @JvmStatic
    fun radarTrack(sprite: RadarDisplayTrackSprite): PartialModel = when (sprite) {
        RadarDisplayTrackSprite.CONTRAPTION -> radarTrackContraption
        RadarDisplayTrackSprite.PLAYER -> radarTrackPlayer
        RadarDisplayTrackSprite.PROJECTILE -> radarTrackProjectile
        RadarDisplayTrackSprite.ENTITY -> radarTrackEntity
    }

    @JvmStatic
    fun radarSelected(): PartialModel = radarTrackSelected

    @JvmStatic
    fun radarDisconnected(): PartialModel = radarDisconnected
}
