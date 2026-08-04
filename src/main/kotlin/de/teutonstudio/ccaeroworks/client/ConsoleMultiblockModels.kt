package de.teutonstudio.ccaeroworks.client

import com.mred231.aeroworks.content.controls.ConsoleDeskBlock
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockSkin
import de.teutonstudio.ccaeroworks.multiblock.ConsoleMultiblockSkinState
import de.teutonstudio.ccaeroworks.registry.CCBlocks
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.block.BlockModelShaper
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.client.resources.model.Material
import net.minecraft.client.resources.model.ModelResourceLocation
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.neoforged.neoforge.client.event.ModelEvent
import net.neoforged.neoforge.client.model.BakedModelWrapper
import net.neoforged.neoforge.client.model.data.ModelData
import java.util.concurrent.ConcurrentHashMap

object ConsoleMultiblockModels {
    private val COMPUTER_SKIN_MODEL = ModelResourceLocation.standalone(
        CCAeroworks.id("block/multiblock_skin_computer")
    )
    private val ADVANCED_SKIN_MODEL = ModelResourceLocation.standalone(
        CCAeroworks.id("block/multiblock_skin_advanced")
    )

    fun registerAdditional(event: ModelEvent.RegisterAdditional) {
        event.register(COMPUTER_SKIN_MODEL)
        event.register(ADVANCED_SKIN_MODEL)
    }

    fun modifyBakingResult(event: ModelEvent.ModifyBakingResult) {
        val normalDesk = BuiltInRegistries.BLOCK
            .filterIsInstance<ConsoleDeskBlock>()
            .firstOrNull {
                BuiltInRegistries.BLOCK.getKey(it).namespace == "aeroworks"
            }
            ?: run {
                CCAeroworks.LOGGER.error(
                    "[CC-Aeroworks] Could not locate the Aeroworks control desk model"
                )
                return
            }

        val originalModels = HashMap(event.models)
        val computerSprite = event.textureGetter.apply(
            Material(
                TextureAtlas.LOCATION_BLOCKS,
                CCAeroworks.id("block/computer_control_desk_multiblock")
            )
        )
        val advancedSprite = event.textureGetter.apply(
            Material(
                TextureAtlas.LOCATION_BLOCKS,
                CCAeroworks.id("block/advanced_computer_control_desk_multiblock")
            )
        )

        val targetBlocks = listOf<Block>(
            normalDesk,
            CCBlocks.COMPUTER_CONTROL_DESK.get(),
            CCBlocks.ADVANCED_COMPUTER_CONTROL_DESK.get()
        )

        targetBlocks.forEach blockLoop@{ block ->
            val isNormalDesk = block === normalDesk
            block.stateDefinition.possibleStates.forEach stateLoop@{ targetState ->
                if (!targetState.hasProperty(ConsoleMultiblockSkinState.SKIN)) return@stateLoop

                val normalState = copyConsoleShape(targetState, normalDesk.defaultBlockState())
                val normalLocation = BlockModelShaper.stateToModelLocation(normalState)
                val originalModel = originalModels[normalLocation] ?: return@stateLoop
                val targetLocation = BlockModelShaper.stateToModelLocation(targetState)

                event.models[targetLocation] = when (
                    targetState.getValue(ConsoleMultiblockSkinState.SKIN)
                ) {
                    ConsoleMultiblockSkin.DEFAULT -> if (isNormalDesk) {
                        originalModel
                    } else {
                        InheritedConsoleModel(originalModel, normalState, null)
                    }

                    ConsoleMultiblockSkin.COMPUTER -> InheritedConsoleModel(
                        originalModel,
                        normalState,
                        computerSprite
                    )

                    ConsoleMultiblockSkin.ADVANCED -> InheritedConsoleModel(
                        originalModel,
                        normalState,
                        advancedSprite
                    )
                }
            }
        }
    }

    private fun copyConsoleShape(source: BlockState, initialTarget: BlockState): BlockState {
        var target = initialTarget
        target = copyDirectionProperty(source, target, "facing")
        target = copyBooleanProperty(source, target, "ceiling")
        target = copyBooleanProperty(source, target, "open_east")
        target = copyBooleanProperty(source, target, "open_west")
        return target
    }

    private fun copyDirectionProperty(
        source: BlockState,
        target: BlockState,
        name: String
    ): BlockState {
        val sourceProperty = source.properties
            .filterIsInstance<DirectionProperty>()
            .firstOrNull { it.name == name }
            ?: return target
        val targetProperty = target.properties
            .filterIsInstance<DirectionProperty>()
            .firstOrNull { it.name == name }
            ?: return target
        return target.setValue(targetProperty, source.getValue(sourceProperty))
    }

    private fun copyBooleanProperty(
        source: BlockState,
        target: BlockState,
        name: String
    ): BlockState {
        val sourceProperty = source.properties
            .filterIsInstance<BooleanProperty>()
            .firstOrNull { it.name == name }
            ?: return target
        val targetProperty = target.properties
            .filterIsInstance<BooleanProperty>()
            .firstOrNull { it.name == name }
            ?: return target
        return target.setValue(targetProperty, source.getValue(sourceProperty))
    }
}

private class InheritedConsoleModel(
    private val delegate: BakedModel,
    private val sourceState: BlockState,
    private val replacementSprite: TextureAtlasSprite?
) : BakedModelWrapper<BakedModel>(delegate) {
    private val quadCache = ConcurrentHashMap<BakedQuad, BakedQuad>()

    override fun getQuads(
        state: BlockState?,
        side: Direction?,
        random: RandomSource
    ): List<BakedQuad> = retexture(
        delegate.getQuads(sourceState, side, random)
    )

    override fun getQuads(
        state: BlockState?,
        side: Direction?,
        random: RandomSource,
        modelData: ModelData,
        renderType: RenderType?
    ): List<BakedQuad> = retexture(
        delegate.getQuads(sourceState, side, random, modelData, renderType)
    )

    override fun getParticleIcon(): TextureAtlasSprite =
        replacementSprite ?: delegate.particleIcon

    override fun getParticleIcon(data: ModelData): TextureAtlasSprite =
        replacementSprite ?: delegate.getParticleIcon(data)

    private fun retexture(quads: List<BakedQuad>): List<BakedQuad> {
        if (replacementSprite == null) return quads
        return quads.map(::replaceTexture)
    }

    private fun replaceTexture(quad: BakedQuad): BakedQuad =
        quadCache.computeIfAbsent(quad) { originalQuad ->
            val sprite = replacementSprite ?: return@computeIfAbsent originalQuad
            val vertices = originalQuad.vertices.clone()
            val stride = vertices.size / 4
            if (stride <= 5) return@computeIfAbsent originalQuad

            repeat(4) { vertexIndex ->
                val base = vertexIndex * stride
                val uIndex = base + 4
                val vIndex = base + 5
                val oldU = Float.fromBits(vertices[uIndex])
                val oldV = Float.fromBits(vertices[vIndex])
                vertices[uIndex] = sprite
                    .getU(originalQuad.sprite.getUOffset(oldU))
                    .toRawBits()
                vertices[vIndex] = sprite
                    .getV(originalQuad.sprite.getVOffset(oldV))
                    .toRawBits()
            }

            BakedQuad(
                vertices,
                originalQuad.tintIndex,
                originalQuad.direction,
                sprite,
                originalQuad.isShade,
                originalQuad.hasAmbientOcclusion()
            )
        }
}
