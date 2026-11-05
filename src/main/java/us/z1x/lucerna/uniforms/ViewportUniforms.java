package us.z1x.lucerna.uniforms;

import net.minecraft.client.Minecraft;
import us.z1x.lucerna.gl.uniform.UniformHolder;
import us.z1x.lucerna.gui.option.LucernaVideoSettings;

import static us.z1x.lucerna.gl.uniform.UniformUpdateFrequency.PER_FRAME;

public final class ViewportUniforms {
    private ViewportUniforms() {
    }

    public static void addViewportUniforms(UniformHolder uniforms) {
        uniforms
            .uniform1f(PER_FRAME, "viewHeight", () -> LucernaVideoSettings.scaleRenderSize(Minecraft.getInstance().getRenderTarget().height))
            .uniform1f(PER_FRAME, "viewWidth", () -> LucernaVideoSettings.scaleRenderSize(Minecraft.getInstance().getRenderTarget().width))
            .uniform1f(PER_FRAME, "aspectRatio", ViewportUniforms::getAspectRatio);
    }

    private static float getAspectRatio() {
        return ((float) Minecraft.getInstance().width) / ((float) Minecraft.getInstance().height);
    }
}
