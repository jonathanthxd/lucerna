package us.z1x.lucerna.uniforms.builtin;

import org.joml.Matrix4f;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.gl.uniform.UniformHolder;
import us.z1x.lucerna.gl.uniform.UniformUpdateFrequency;

public class BuiltinReplacementUniforms {
    // mimics the GL_TEXTURE matrix that GameRenderer sets on the lightmap unit
    private static final Matrix4f lightmapTextureMatrix = new Matrix4f().scale(0.00390625f).translate(8.0f, 8.0f, 8.0f);

    public static void addBuiltinReplacementUniforms(UniformHolder uniforms) {
        uniforms.uniformMatrix(UniformUpdateFrequency.ONCE, "iris_LightmapTextureMatrix", () -> {
            Lucerna.logger.warn("A shader appears to require the lightmap texture matrix even after transformations have occurred");
            Lucerna.logger.warn("Lucerna handles this correctly but it indicates that the shader is doing weird things with lightmap coordinates");

            return lightmapTextureMatrix;
        });
    }
}
