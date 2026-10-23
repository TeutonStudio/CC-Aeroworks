package de.teutonstudio.ccaeroworks.client.ponder;

import de.teutonstudio.ccaeroworks.CCAeroworks;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class CCAeroworksPonderPlugin implements PonderPlugin {
    private static final ResourceLocation SMALL_DISPLAY = CCAeroworks.id("small_display");
    private static final ResourceLocation LARGE_DISPLAY = CCAeroworks.id("large_display");
    private static final ResourceLocation ADVANCED_SMALL_DISPLAY = CCAeroworks.id("advanced_small_display");
    private static final ResourceLocation ADVANCED_LARGE_DISPLAY = CCAeroworks.id("advanced_large_display");

    @Override public String getModId() { return CCAeroworks.MOD_ID; }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(
                ResourceLocation.fromNamespaceAndPath("aeroworks", "control_desk"),
                ResourceLocation.fromNamespaceAndPath("aeroworks", "copycat_control_desk"),
                ResourceLocation.fromNamespaceAndPath("aeroworks", "control_stand"),
                ResourceLocation.fromNamespaceAndPath("aeroworks", "copycat_control_stand"),
                ResourceLocation.fromNamespaceAndPath("computercraft", "computer_normal"),
                ResourceLocation.fromNamespaceAndPath("computercraft", "computer_advanced"))
            .addStoryBoard("computer_control_desk", ComputerControlDeskScenes::network)
            .addStoryBoard("computer_control_desk", ComputerControlDeskScenes::peripheralSearch)
            .addStoryBoard("computer_control_desk", ComputerControlDeskScenes::diagnostics);
        helper.forComponents(SMALL_DISPLAY, LARGE_DISPLAY, ADVANCED_SMALL_DISPLAY, ADVANCED_LARGE_DISPLAY)
            .addStoryBoard("computer_control_desk", DisplayModuleScenes::crafting)
            .addStoryBoard("computer_control_desk", DisplayModuleScenes::mounting)
            .addStoryBoard("computer_control_desk", DisplayModuleScenes::programming);
    }
}
