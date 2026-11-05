package us.z1x.lucerna.gl.sampler;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20C;

public class SamplerLimits {
    private final int maxTextureUnits;
    private final int maxDrawBuffers;
    private static SamplerLimits instance;

    private SamplerLimits() {
        this.maxTextureUnits = GL11.glGetInteger(GL20C.GL_MAX_TEXTURE_IMAGE_UNITS);
        this.maxDrawBuffers = GL11.glGetInteger(GL20C.GL_MAX_DRAW_BUFFERS);
    }

    public int getMaxTextureUnits() {
        return maxTextureUnits;
    }

    public static SamplerLimits get() {
        if (instance == null) {
            instance = new SamplerLimits();
        }

        return instance;
    }
}
