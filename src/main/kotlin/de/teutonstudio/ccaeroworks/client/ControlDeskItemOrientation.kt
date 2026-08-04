package de.teutonstudio.ccaeroworks.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import de.teutonstudio.ccaeroworks.CCAeroworks
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.client.resources.model.ModelResourceLocation
import net.minecraft.world.item.ItemDisplayContext
import net.neoforged.neoforge.client.event.ModelEvent
import net.neoforged.neoforge.client.model.BakedModelWrapper

object ControlDeskItemOrientation {
    private val COMPUTER_ITEM_MODEL = ModelResourceLocation.inventory(
        CCAeroworks.id("computer_control_desk")
    )
    private val ADVANCED_ITEM_MODEL = ModelResourceLocation.inventory(
        CCAeroworks.id("advanced_computer_control_desk")
    )

    fun modifyBakingResult(event: ModelEvent.ModifyBakingResult) {
        listOf(COMPUTER_ITEM_MODEL, ADVANCED_ITEM_MODEL).forEach { location ->
            val model = event.models[location] ?: return@forEach
            event.models[location] = GuiRotatedItemModel(model)
        }
    }
}

private class GuiRotatedItemModel(
    private val delegate: BakedModel
) : BakedModelWrapper<BakedModel>(delegate) {
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
        if (transformType == ItemDisplayContext.GUI) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F))
        }
        return if (transformed === delegate) this else GuiRotatedItemModel(transformed)
    }
}
