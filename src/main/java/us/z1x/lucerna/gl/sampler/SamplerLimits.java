package us.z1x.lucerna.gl.sampler;

import org.lwjgl.opengl.GL11;
import net.minecraft.client.render.platform.GlStateManager;
import org.lwjgl.opengl.GL20C;

public class SamplerLimits {
    private final int maxTextureUnits;
    private final int maxDrawBuffers;
    private static SamplerLimits instance;

    private SamplerLimits() {
        this.maxTextureUnits = effectiveTextureUnits(
            GL11.glGetInteger(GL20C.GL_MAX_TEXTURE_IMAGE_UNITS),
            GL11.glGetInteger(GL20C.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS),
            GlStateManager.TEXTURES.length);
        this.maxDrawBuffers = GL11.glGetInteger(GL20C.GL_MAX_DRAW_BUFFERS);
    }

    // Conservative shared namespace: do not expand to the larger combined limit.
    // GLSL linking additionally enforces each shader stage's sampler count.
    static int effectiveTextureUnits(int fragment, int combined, int cachedBindings) {
        int limit = Math.min(fragment, Math.min(combined, cachedBindings));
        if (limit <= 0) throw new IllegalStateException("No usable texture units: fragment=" + fragment
            + ", combined=" + combined + ", cachedBindings=" + cachedBindings);
        return limit;
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
