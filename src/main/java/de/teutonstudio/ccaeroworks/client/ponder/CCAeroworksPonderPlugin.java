package de.teutonstudio.ccaeroworks.client.ponder;

import de.teutonstudio.ccaeroworks.CCAeroworks;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class CCAeroworksPonderPlugin implements PonderPlugin {
    private static final ResourceLocation COMPUTER_CONTROL_DESK =
        ResourceLocation.fromNamespaceAndPath(CCAeroworks.MOD_ID, "computer_control_desk");
    private static final ResourceLocation ADVANCED_COMPUTER_CONTROL_DESK =
        ResourceLocation.fromNamespaceAndPath(CCAeroworks.MOD_ID, "advanced_computer_control_desk");
    private static final ResourceLocation TWO_DIGIT_DISPLAY =
        ResourceLocation.fromNamespaceAndPath(CCAeroworks.MOD_ID, "two_digit_display");
    private static final ResourceLocation THREE_DIGIT_DISPLAY =
        ResourceLocation.fromNamespaceAndPath(CCAeroworks.MOD_ID, "three_digit_display");

    @Override
    public String getModId() {
        return CCAeroworks.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(COMPUTER_CONTROL_DESK, ADVANCED_COMPUTER_CONTROL_DESK)
            .addStoryBoard("computer_control_desk", ComputerControlDeskScenes::overview);
        helper.forComponents(TWO_DIGIT_DISPLAY, THREE_DIGIT_DISPLAY)
            .addStoryBoard("computer_control_desk", DisplayModuleScenes::overview);
    }
}
