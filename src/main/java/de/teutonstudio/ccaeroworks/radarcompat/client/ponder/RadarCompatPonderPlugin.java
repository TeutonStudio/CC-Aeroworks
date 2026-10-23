package de.teutonstudio.ccaeroworks.radarcompat.client.ponder;

import de.teutonstudio.ccaeroworks.CCAeroworks;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public final class RadarCompatPonderPlugin implements PonderPlugin {
    private static final ResourceLocation ADVANCED_SMALL_DISPLAY = CCAeroworks.id("advanced_small_display");
    private static final ResourceLocation ADVANCED_LARGE_DISPLAY = CCAeroworks.id("advanced_large_display");

    @Override public String getModId() { return CCAeroworks.MOD_ID; }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(ADVANCED_SMALL_DISPLAY, ADVANCED_LARGE_DISPLAY)
            .addStoryBoard("computer_control_desk", RadarDisplayScenes::controllerConnection)
            .addStoryBoard("computer_control_desk", RadarDisplayScenes::directRadarDisplay);
    }
}
