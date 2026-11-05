package us.z1x.lucerna.apiimpl;

import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisApiConfig;
import net.minecraft.client.gui.screen.Screen;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.gui.screen.ShaderPackScreen;
import us.z1x.lucerna.shadows.ShadowRenderingState;

public class IrisApiV0Impl implements IrisApi {
    public static final IrisApiV0Impl INSTANCE = new IrisApiV0Impl();
    private static final IrisApiV0ConfigImpl CONFIG = new IrisApiV0ConfigImpl();

    @Override
    public int getMinorApiRevision() {
        return 1;
    }

    @Override
    public boolean isShaderPackInUse() {
        return Lucerna.isShaderPipelineActive();
    }

    @Override
    public boolean isRenderingShadowPass() {
        return ShadowRenderingState.areShadowsCurrentlyBeingRendered();
    }

    @Override
    public Object openMainIrisScreenObj(Object parent) {
        return new ShaderPackScreen((Screen) parent);
    }

    @Override
    public String getMainScreenLanguageKey() {
        return "options.lucerna.shaderPackSelection";
    }

    @Override
    public IrisApiConfig getConfig() {
        return CONFIG;
    }
}
