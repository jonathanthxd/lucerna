package us.z1x.lucerna.gbufferoverrides.state;

import net.minecraft.client.render.platform.GlStateManager;
import us.z1x.lucerna.gbufferoverrides.matching.InputAvailability;
import us.z1x.lucerna.samplers.LucernaSamplers;

public class StateTracker {
    public static final StateTracker INSTANCE = new StateTracker();

    public boolean albedoSampler;
    public boolean lightmapSampler;
    public boolean overlaySampler;

    private static final InputAvailability[] INPUTS = new InputAvailability[InputAvailability.NUM_VALUES];

    static {
        for (int i = 0; i < INPUTS.length; i++) {
            INPUTS[i] = InputAvailability.unpack(i);
        }
    }

    public InputAvailability getInputs() {
        return INPUTS[(albedoSampler ? 1 : 0) | (lightmapSampler ? 2 : 0) | (overlaySampler ? 4 : 0)];
    }

    // vanilla may have enabled texturing before the pipeline started listening, so read the cached GL state
    public void syncWithGlState() {
        albedoSampler = GlStateManager.TEXTURES[LucernaSamplers.ALBEDO_TEXTURE_UNIT].state.enabled;
        lightmapSampler = GlStateManager.TEXTURES[LucernaSamplers.LIGHTMAP_TEXTURE_UNIT].state.enabled;
        overlaySampler = false;
    }
}
