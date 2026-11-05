package us.z1x.lucerna.postprocess;

import com.google.common.collect.ImmutableSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL21C;
import us.z1x.lucerna.gl.LucernaRenderSystem;
import us.z1x.lucerna.gl.framebuffer.GlFramebuffer;
import us.z1x.lucerna.gl.program.Program;
import us.z1x.lucerna.gl.program.ProgramBuilder;
import us.z1x.lucerna.gl.program.ProgramSamplers;
import us.z1x.lucerna.gl.program.ProgramUniforms;
import us.z1x.lucerna.gl.texture.DepthCopyStrategy;
import us.z1x.lucerna.gl.texture.InternalTextureFormat;
import us.z1x.lucerna.gl.texture.PixelType;
import us.z1x.lucerna.gl.uniform.UniformUpdateFrequency;
import us.z1x.lucerna.uniforms.SystemTimeUniforms;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.IntSupplier;

public class CenterDepthSampler {
    private static final double LN2 = Math.log(2);
    private boolean hasFirstSample;
    private boolean everRetrieved;
    private final Program program;
    private final GlFramebuffer framebuffer;
    private final int texture;
    private final int altTexture;
    private boolean destroyed;

    public CenterDepthSampler(IntSupplier depthSupplier, float halfLife) {
        this.texture = GlStateManager.genTextures();
        this.altTexture = GlStateManager.genTextures();
        this.framebuffer = new GlFramebuffer();

        // fall back to a less precise format without opengl 3
        InternalTextureFormat format = GL.getCapabilities().OpenGL32 ? InternalTextureFormat.R32F : InternalTextureFormat.RGB16;
        setupColorTexture(texture, format);
        setupColorTexture(altTexture, format);
        GlStateManager.bindTexture(0);

        this.framebuffer.addColorAttachment(0, texture);
        ProgramBuilder builder;

        try {
            String fsh = new String(IOUtils.toByteArray(Objects.requireNonNull(getClass().getResourceAsStream("/assets/lucerna/shaders/centerDepth.fsh"))), StandardCharsets.UTF_8);

            if (GL.getCapabilities().OpenGL32) {
                fsh = fsh.replace("VERSIONPLACEHOLDER", "150 compatibility");
            } else {
                fsh = fsh.replace("#define IS_GL3", "");
                fsh = fsh.replace("VERSIONPLACEHOLDER", "120");
            }
            String vsh = new String(IOUtils.toByteArray(Objects.requireNonNull(getClass().getResourceAsStream("/assets/lucerna/shaders/centerDepth.vsh"))), StandardCharsets.UTF_8);

            builder = ProgramBuilder.begin("centerDepthSmooth", vsh, null, fsh, ImmutableSet.of(0, 1, 2));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        builder.addDynamicSampler(depthSupplier, "depth");
        builder.addDynamicSampler(() -> altTexture, "altDepth");
        builder.uniform1f(UniformUpdateFrequency.PER_FRAME, "lastFrameTime", SystemTimeUniforms.TIMER::getLastFrameTime);
        builder.uniform1f(UniformUpdateFrequency.ONCE, "decay", () -> (1.0f / ((halfLife * 0.1) / LN2)));
        this.program = builder.build();
    }

    public void sampleCenterDepth() {
        if ((hasFirstSample && (!everRetrieved)) || destroyed) {
            // skip sampling while the pack never reads center depth
            return;
        }

        hasFirstSample = true;

        this.framebuffer.bind();
        this.program.use();

        GlStateManager.viewport(0, 0, 1, 1);

        FullScreenQuadRenderer.INSTANCE.render();

        ProgramUniforms.clearActiveUniforms();
        ProgramSamplers.clearActiveSamplers();

        // DepthCopyStrategy claims depth only, but the 2 non-stencil methods also copy color
        DepthCopyStrategy.fastest(false).copy(this.framebuffer, texture, null, altTexture, 1, 1);

        Minecraft.getInstance().getRenderTarget().bindWrite(true);
    }

    public void setupColorTexture(int texture, InternalTextureFormat format) {
        LucernaRenderSystem.texImage2D(texture, GL21C.GL_TEXTURE_2D, 0, format.getGlFormat(), 1, 1, 0, format.getPixelFormat().getGlFormat(), PixelType.FLOAT.getGlFormat(), null);

        LucernaRenderSystem.texParameteri(texture, GL21C.GL_TEXTURE_2D, GL21C.GL_TEXTURE_MIN_FILTER, GL21C.GL_LINEAR);
        LucernaRenderSystem.texParameteri(texture, GL21C.GL_TEXTURE_2D, GL21C.GL_TEXTURE_MAG_FILTER, GL21C.GL_LINEAR);
        LucernaRenderSystem.texParameteri(texture, GL21C.GL_TEXTURE_2D, GL21C.GL_TEXTURE_WRAP_S, GL21C.GL_CLAMP_TO_EDGE);
        LucernaRenderSystem.texParameteri(texture, GL21C.GL_TEXTURE_2D, GL21C.GL_TEXTURE_WRAP_T, GL21C.GL_CLAMP_TO_EDGE);
    }

    public int getCenterDepthTexture() {
        return altTexture;
    }

    public void setUsage(boolean usage) {
        everRetrieved |= usage;
    }

    public void destroy() {
        GlStateManager.deleteTextures(texture);
        GlStateManager.deleteTextures(altTexture);
        framebuffer.destroy();
        program.destroy();
        destroyed = true;
    }
}
