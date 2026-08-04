package de.teutonstudio.ccaeroworks.client

import com.mojang.blaze3d.vertex.PoseStack
import de.teutonstudio.ccaeroworks.CCAeroworks
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes
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
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.neoforged.neoforge.client.ChunkRenderTypeSet
import net.neoforged.neoforge.client.RenderTypeHelper
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
        val normalDesk = try {
            AeroworksTypes.vanillaControlDeskBlock()
        } catch (error: IllegalStateException) {
            CCAeroworks.LOGGER.error(
                "[CC-Aeroworks] Could not locate the Aeroworks control desk model",
                error
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
                        InheritedConsoleModel(originalModel, normalState)
                    }

                    ConsoleMultiblockSkin.COMPUTER -> OverlayConsoleModel(
                        originalModel,
                        normalState,
                        computerSprite
                    )

                    ConsoleMultiblockSkin.ADVANCED -> OverlayConsoleModel(
                        originalModel,
                        normalState,
                        advancedSprite
                    )
                }
            }
        }

        inheritItemModels(event, originalModels, normalDesk, computerSprite, advancedSprite)
    }

    private fun inheritItemModels(
        event: ModelEvent.ModifyBakingResult,
        originalModels: Map<ModelResourceLocation, BakedModel>,
        normalDesk: Block,
        computerSprite: TextureAtlasSprite,
        advancedSprite: TextureAtlasSprite
    ) {
        val normalItemLocation = ModelResourceLocation.inventory(
            BuiltInRegistries.ITEM.getKey(normalDesk.asItem())
        )
        val normalItemModel = originalModels[normalItemLocation] ?: run {
            CCAeroworks.LOGGER.error(
                "[CC-Aeroworks] Could not locate the Aeroworks control desk item model"
            )
            return
        }

        event.models[
            ModelResourceLocation.inventory(CCAeroworks.id("computer_control_desk"))
        ] = OverlayItemModel(normalItemModel, computerSprite)
        event.models[
            ModelResourceLocation.inventory(CCAeroworks.id("advanced_computer_control_desk"))
        ] = OverlayItemModel(normalItemModel, advancedSprite)
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
    private val sourceState: BlockState
) : BakedModelWrapper<BakedModel>(delegate) {
    override fun getQuads(
        state: BlockState?,
        side: Direction?,
        random: RandomSource
    ): List<BakedQuad> = delegate.getQuads(sourceState, side, random)

    override fun getQuads(
        state: BlockState?,
        side: Direction?,
        random: RandomSource,
        modelData: ModelData,
        renderType: RenderType?
    ): List<BakedQuad> = delegate.getQuads(sourceState, side, random, modelData, renderType)

    override fun getRenderTypes(
        state: BlockState,
        random: RandomSource,
        modelData: ModelData
    ): ChunkRenderTypeSet = delegate.getRenderTypes(sourceState, random, modelData)
}

private class OverlayConsoleModel(
    private val delegate: BakedModel,
    private val sourceState: BlockState,
    overlaySprite: TextureAtlasSprite
) : BakedModelWrapper<BakedModel>(delegate) {
    private val overlay = OverlayQuadFactory(overlaySprite)

    override fun getQuads(
        state: BlockState?,
        side: Direction?,
        random: RandomSource
    ): List<BakedQuad> {
        val baseQuads = delegate.getQuads(sourceState, side, random)
        return baseQuads + overlay.create(baseQuads)
    }

    override fun getQuads(
        state: BlockState?,
        side: Direction?,
        random: RandomSource,
        modelData: ModelData,
        renderType: RenderType?
    ): List<BakedQuad> {
        if (renderType == null) {
            val baseQuads = delegate.getQuads(sourceState, side, random, modelData, null)
            return baseQuads + overlay.create(baseQuads)
        }

        if (renderType != RenderType.translucent()) {
            return delegate.getQuads(sourceState, side, random, modelData, renderType)
        }

        val baseRenderTypes = delegate.getRenderTypes(
            sourceState,
            RandomSource.create(OVERLAY_RANDOM_SEED),
            modelData
        )
        val translucentBase = if (baseRenderTypes.contains(RenderType.translucent())) {
            delegate.getQuads(sourceState, side, random, modelData, renderType)
        } else {
            emptyList()
        }
        val overlaySource = delegate.getQuads(
            sourceState,
            side,
            RandomSource.create(OVERLAY_RANDOM_SEED),
            modelData,
            null
        )
        return translucentBase + overlay.create(overlaySource)
    }

    override fun getRenderTypes(
        state: BlockState,
        random: RandomSource,
        modelData: ModelData
    ): ChunkRenderTypeSet = ChunkRenderTypeSet.union(
        delegate.getRenderTypes(sourceState, random, modelData),
        OVERLAY_BLOCK_RENDER_TYPES
    )

    override fun getParticleIcon(): TextureAtlasSprite = delegate.particleIcon

    override fun getParticleIcon(data: ModelData): TextureAtlasSprite =
        delegate.getParticleIcon(data)
}

private class OverlayItemModel(
    private val delegate: BakedModel,
    private val overlaySprite: TextureAtlasSprite
) : BakedModelWrapper<BakedModel>(delegate) {
    override fun getRenderPasses(itemStack: ItemStack, fabulous: Boolean): List<BakedModel> =
        delegate.getRenderPasses(itemStack, fabulous) + OverlayItemPass(delegate, overlaySprite)

    override fun applyTransform(
        transformType: ItemDisplayContext,
        poseStack: PoseStack,
        applyLeftHandTransform: Boolean
    ): BakedModel {
        val transformed = delegate.applyTransform(
            transformType,
            poseStack,
            applyLeftHandTransform
        )
        return if (transformed === delegate) this else OverlayItemModel(transformed, overlaySprite)
    }
}

private class OverlayItemPass(
    private val delegate: BakedModel,
    overlaySprite: TextureAtlasSprite
) : BakedModelWrapper<BakedModel>(delegate) {
    private val overlay = OverlayQuadFactory(overlaySprite)

    override fun getQuads(
        state: BlockState?,
        side: Direction?,
        random: RandomSource
    ): List<BakedQuad> = overlay.create(delegate.getQuads(state, side, random))

    override fun getRenderTypes(itemStack: ItemStack, fabulous: Boolean): List<RenderType> =
        listOf(RenderTypeHelper.getEntityRenderType(RenderType.translucent(), fabulous))

    override fun getParticleIcon(): TextureAtlasSprite = delegate.particleIcon

    override fun getParticleIcon(data: ModelData): TextureAtlasSprite =
        delegate.getParticleIcon(data)
}

private class OverlayQuadFactory(
    private val sprite: TextureAtlasSprite
) {
    private val cache = ConcurrentHashMap<BakedQuad, BakedQuad>()

    fun create(quads: List<BakedQuad>): List<BakedQuad> = quads.mapNotNull(::create)

    private fun create(originalQuad: BakedQuad): BakedQuad? {
        val vertices = originalQuad.vertices
        if (vertices.size % 4 != 0 || vertices.size / 4 <= 5) return null

        return cache.computeIfAbsent(originalQuad) { source ->
            val copiedVertices = source.vertices.clone()
            val stride = copiedVertices.size / 4
            val direction = source.direction

            repeat(4) { vertexIndex ->
                val base = vertexIndex * stride
                copiedVertices[base] = (
                    Float.fromBits(copiedVertices[base]) +
                        direction.stepX * OVERLAY_OFFSET
                    ).toRawBits()
                copiedVertices[base + 1] = (
                    Float.fromBits(copiedVertices[base + 1]) +
                        direction.stepY * OVERLAY_OFFSET
                    ).toRawBits()
                copiedVertices[base + 2] = (
                    Float.fromBits(copiedVertices[base + 2]) +
                        direction.stepZ * OVERLAY_OFFSET
                    ).toRawBits()

                val uIndex = base + 4
                val vIndex = base + 5
                val oldU = Float.fromBits(copiedVertices[uIndex])
                val oldV = Float.fromBits(copiedVertices[vIndex])
                copiedVertices[uIndex] = sprite
                    .getU(source.sprite.getUOffset(oldU))
                    .toRawBits()
                copiedVertices[vIndex] = sprite
                    .getV(source.sprite.getVOffset(oldV))
                    .toRawBits()
            }

            BakedQuad(
                copiedVertices,
                source.tintIndex,
                source.direction,
                sprite,
                source.isShade,
                source.hasAmbientOcclusion()
            )
        }
    }
}

private val OVERLAY_BLOCK_RENDER_TYPES = ChunkRenderTypeSet.of(RenderType.translucent())
private const val OVERLAY_RANDOM_SEED: Long = 42L
private const val OVERLAY_OFFSET: Float = 0.0005F
