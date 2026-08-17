package de.teutonstudio.ccaeroworks.radarcompat.client.ponder;

import de.teutonstudio.ccaeroworks.CCAeroworks;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public final class RadarCompatPonderPlugin implements PonderPlugin {
    private static final ResourceLocation SMALL_RADAR_DISPLAY = CCAeroworks.id("small_radar_display");
    private static final ResourceLocation LARGE_RADAR_DISPLAY = CCAeroworks.id("large_radar_display");

    @Override public String getModId() { return CCAeroworks.MOD_ID; }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(SMALL_RADAR_DISPLAY, LARGE_RADAR_DISPLAY)
            .addStoryBoard("computer_control_desk", RadarDisplayScenes::controllerConnection)
            .addStoryBoard("computer_control_desk", RadarDisplayScenes::directRadarDisplay);
    }
}
