package us.z1x.lucerna.postprocess;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import us.z1x.lucerna.gl.LucernaRenderSystem;
import us.z1x.lucerna.gl.framebuffer.GlFramebuffer;
import us.z1x.lucerna.gl.program.ComputeProgram;
import us.z1x.lucerna.gl.program.Program;
import us.z1x.lucerna.gl.program.ProgramBuilder;
import us.z1x.lucerna.gl.program.ProgramSamplers;
import us.z1x.lucerna.gl.program.ProgramUniforms;
import us.z1x.lucerna.gl.sampler.SamplerLimits;
import us.z1x.lucerna.pipeline.PatchedShaderPrinter;
import us.z1x.lucerna.pipeline.transform.PatchShaderType;
import us.z1x.lucerna.pipeline.transform.TransformPatcher;
import us.z1x.lucerna.rendertarget.RenderTarget;
import us.z1x.lucerna.rendertarget.RenderTargets;
import us.z1x.lucerna.samplers.LucernaImages;
import us.z1x.lucerna.samplers.LucernaSamplers;
import us.z1x.lucerna.shaderpack.ComputeSource;
import us.z1x.lucerna.shaderpack.PackDirectives;
import us.z1x.lucerna.shaderpack.ProgramDirectives;
import us.z1x.lucerna.shaderpack.ProgramSource;
import us.z1x.lucerna.shadows.ShadowRenderTargets;
import us.z1x.lucerna.uniforms.CommonUniforms;
import us.z1x.lucerna.uniforms.FrameUpdateNotifier;
import us.z1x.lucerna.uniforms.custom.CustomUniforms;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class CompositeRenderer {
    private final RenderTargets renderTargets;

    private final ImmutableList<Pass> passes;
    private final IntSupplier noiseTexture;
    private final FrameUpdateNotifier updateNotifier;
    private final CenterDepthSampler centerDepthSampler;
    private final Object2ObjectMap<String, IntSupplier> customTextureIds;
    private final Map<String, String> textureRenames;
    private final ImmutableSet<Integer> flippedAtLeastOnceFinal;
    private final CustomUniforms customUniforms;

    public CompositeRenderer(PackDirectives packDirectives, ProgramSource[] sources, ComputeSource[][] computes, RenderTargets renderTargets,
                             IntSupplier noiseTexture, FrameUpdateNotifier updateNotifier,
                             CenterDepthSampler centerDepthSampler, BufferFlipper bufferFlipper,
                             Supplier<ShadowRenderTargets> shadowTargetsSupplier,
                             Object2ObjectMap<String, IntSupplier> customTextureIds, Map<String, String> textureRenames, ImmutableMap<Integer, Boolean> explicitPreFlips,
                             CustomUniforms customUniforms) {
        this.noiseTexture = noiseTexture;
        this.customUniforms = customUniforms;
        this.updateNotifier = updateNotifier;
        this.centerDepthSampler = centerDepthSampler;
        this.renderTargets = renderTargets;
        this.customTextureIds = customTextureIds;
        this.textureRenames = textureRenames;

        final ImmutableList.Builder<Pass> passes = ImmutableList.builder();
        final ImmutableSet.Builder<Integer> flippedAtLeastOnce = new ImmutableSet.Builder<>();

        explicitPreFlips.forEach((buffer, shouldFlip) -> {
            if (shouldFlip) {
                bufferFlipper.flip(buffer);
                // flipping deferred_pre or composite_pre does not set flippedAtLeastOnce
            }
        });

        for (int i = 0; i < sources.length; i++) {
            ProgramSource source = sources[i];

            ImmutableSet<Integer> flipped = bufferFlipper.snapshot();
            ImmutableSet<Integer> flippedAtLeastOnceSnapshot = flippedAtLeastOnce.build();

            if (source == null || !source.isValid()) {
                if (computes[i] != null && Arrays.stream(computes[i]).anyMatch(Objects::nonNull)) {
                    ComputeOnlyPass pass = new ComputeOnlyPass();
                    pass.computes = createComputes(computes[i], flipped, flippedAtLeastOnceSnapshot, shadowTargetsSupplier);
                    passes.add(pass);
                }
                continue;
            }

            Pass pass = new Pass();
            ProgramDirectives directives = source.getDirectives();

            pass.program = createProgram(source, flipped, flippedAtLeastOnceSnapshot, shadowTargetsSupplier);
            pass.computes = createComputes(computes[i], flipped, flippedAtLeastOnceSnapshot, shadowTargetsSupplier);
            int[] drawBuffers = directives.getDrawBuffers();

            GlFramebuffer framebuffer = renderTargets.createColorFramebuffer(flipped, drawBuffers);

            int passWidth = 0, passHeight = 0;
            ImmutableMap<Integer, Boolean> explicitFlips = directives.getExplicitFlips();

            for (int buffer : drawBuffers) {
                RenderTarget target = renderTargets.get(buffer);
                if ((passWidth > 0 && passWidth != target.getWidth()) || (passHeight > 0 && passHeight != target.getHeight())) {
                    throw new IllegalStateException("Pass widths must match");
                }
                passWidth = target.getWidth();
                passHeight = target.getHeight();

                // compare with boxed Boolean to avoid npes
                if (explicitFlips.get(buffer) == Boolean.FALSE) {
                    continue;
                }

                bufferFlipper.flip(buffer);
                flippedAtLeastOnce.add(buffer);
            }

            explicitFlips.forEach((buffer, shouldFlip) -> {
                if (shouldFlip) {
                    bufferFlipper.flip(buffer);
                    flippedAtLeastOnce.add(buffer);
                }
            });

            pass.drawBuffers = directives.getDrawBuffers();
            pass.viewWidth = passWidth;
            pass.viewHeight = passHeight;
            pass.stageReadsFromAlt = flipped;
            pass.framebuffer = framebuffer;
            pass.viewportScale = directives.getViewportScale();
            pass.mipmappedBuffers = directives.getMipmappedBuffers();

            passes.add(pass);
        }

        this.passes = passes.build();
        this.flippedAtLeastOnceFinal = flippedAtLeastOnce.build();

        LucernaRenderSystem.bindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, 0);
    }

    public ImmutableSet<Integer> getFlippedAtLeastOnceFinal() {
        return this.flippedAtLeastOnceFinal;
    }

    public void recalculateSizes() {
        for (Pass pass : passes) {
            if (pass instanceof ComputeOnlyPass) {
                continue;
            }
            int passWidth = 0, passHeight = 0;
            for (int buffer : pass.drawBuffers) {
                RenderTarget target = renderTargets.get(buffer);
                if ((passWidth > 0 && passWidth != target.getWidth()) || (passHeight > 0 && passHeight != target.getHeight())) {
                    throw new IllegalStateException("Pass widths must match");
                }
                passWidth = target.getWidth();
                passHeight = target.getHeight();
            }
            renderTargets.destroyFramebuffer(pass.framebuffer);
            pass.framebuffer = renderTargets.createColorFramebuffer(pass.stageReadsFromAlt, pass.drawBuffers);
            pass.viewWidth = passWidth;
            pass.viewHeight = passHeight;
        }
    }

    private static class Pass {
        int[] drawBuffers;
        int viewWidth;
        int viewHeight;
        Program program;
        ComputeProgram[] computes;
        GlFramebuffer framebuffer;
        ImmutableSet<Integer> stageReadsFromAlt;
        ImmutableSet<Integer> mipmappedBuffers;
        float viewportScale;

        protected void destroy() {
            this.program.destroy();
            for (ComputeProgram compute : this.computes) {
                if (compute != null) {
                    compute.destroy();
                }
            }
        }
    }

    private class ComputeOnlyPass extends Pass {
        @Override
        protected void destroy() {
            for (ComputeProgram compute : this.computes) {
                if (compute != null) {
                    compute.destroy();
                }
            }
        }
    }

    public void renderAll() {
        GlStateManager.disableBlend();
        GlStateManager.disableAlphaTest();

        FullScreenQuadRenderer.INSTANCE.begin();

        for (Pass renderPass : passes) {
            boolean ranCompute = false;
            for (ComputeProgram computeProgram : renderPass.computes) {
                if (computeProgram != null) {
                    ranCompute = true;
                    computeProgram.dispatch(renderTargets.getCurrentWidth(), renderTargets.getCurrentHeight());
                }
            }

            if (ranCompute) {
                LucernaRenderSystem.memoryBarrier(40);
            }

            Program.unbind();

            if (renderPass instanceof ComputeOnlyPass) {
                continue;
            }

            if (!renderPass.mipmappedBuffers.isEmpty()) {
                GlStateManager.activeTexture(GL15C.GL_TEXTURE0);

                for (int index : renderPass.mipmappedBuffers) {
                    setupMipmapping(CompositeRenderer.this.renderTargets.get(index), renderPass.stageReadsFromAlt.contains(index));
                }
            }

            float scaledWidth = renderPass.viewWidth * renderPass.viewportScale;
            float scaledHeight = renderPass.viewHeight * renderPass.viewportScale;
            GlStateManager.viewport(0, 0, (int) scaledWidth, (int) scaledHeight);

            renderPass.framebuffer.bind();
            renderPass.program.use();

            FullScreenQuadRenderer.INSTANCE.renderQuad();
        }

        FullScreenQuadRenderer.end();

        Minecraft.getInstance().getRenderTarget().bindWrite(true);
        ProgramUniforms.clearActiveUniforms();
        ProgramSamplers.clearActiveSamplers();
        LucernaRenderSystem.useProgram(0);

        // unbinding these textures is needed for shader pack reloading
        for (int i = 0; i < SamplerLimits.get().getMaxTextureUnits(); i++) {
            GlStateManager.activeTexture(GL15C.GL_TEXTURE0 + i);
            GlStateManager.bindTexture(0);
        }

        GlStateManager.activeTexture(GL15C.GL_TEXTURE0);
    }

    private static void setupMipmapping(RenderTarget target, boolean readFromAlt) {
        int texture = readFromAlt ? target.getAltTexture() : target.getMainTexture();

        // mipmapping stays on after writes like optifine, the sampling mode resets each frame
        LucernaRenderSystem.generateMipmaps(texture, GL20C.GL_TEXTURE_2D);

        int filter = GL20C.GL_LINEAR_MIPMAP_LINEAR;
        if (target.getInternalFormat().getPixelFormat().isInteger()) {
            filter = GL20C.GL_NEAREST_MIPMAP_NEAREST;
        }

        LucernaRenderSystem.texParameteri(texture, GL20C.GL_TEXTURE_2D, GL20C.GL_TEXTURE_MIN_FILTER, filter);
    }

    private Program createProgram(ProgramSource source, ImmutableSet<Integer> flipped, ImmutableSet<Integer> flippedAtLeastOnceSnapshot,
                                  Supplier<ShadowRenderTargets> shadowTargetsSupplier) {
        Map<PatchShaderType, String> transformed = TransformPatcher.patchComposite(
            source.getVertexSource().orElseThrow(NullPointerException::new),
            source.getGeometrySource().orElse(null),
            source.getFragmentSource().orElseThrow(NullPointerException::new), textureRenames);
        String vertex = transformed.get(PatchShaderType.VERTEX);
        String geometry = transformed.get(PatchShaderType.GEOMETRY);
        String fragment = transformed.get(PatchShaderType.FRAGMENT);
        PatchedShaderPrinter.debugPatchedShaders(source.getName(), vertex, geometry, fragment);

        Objects.requireNonNull(flipped);
        ProgramBuilder builder;

        try {
            builder = ProgramBuilder.begin(source.getName(), vertex, geometry, fragment,
                LucernaSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS);
        } catch (RuntimeException e) {
            throw new RuntimeException("Shader compilation failed!", e);
        }

        ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(builder, customTextureIds, flippedAtLeastOnceSnapshot);

        CommonUniforms.addCommonUniforms(builder, source.getParent().getPack().getIdMap(), source.getParent().getPackDirectives(), updateNotifier);
        customUniforms.assignTo(builder);
        LucernaSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, () -> flipped, renderTargets, true);
        LucernaImages.addRenderTargetImages(builder, () -> flipped, renderTargets);

        LucernaSamplers.addNoiseSampler(customTextureSamplerInterceptor, noiseTexture);
        LucernaSamplers.addCompositeSamplers(customTextureSamplerInterceptor, renderTargets);

        if (LucernaSamplers.hasShadowSamplers(customTextureSamplerInterceptor)) {
            LucernaSamplers.addShadowSamplers(customTextureSamplerInterceptor, shadowTargetsSupplier.get());
            LucernaImages.addShadowColorImages(builder, shadowTargetsSupplier.get());
        }

        centerDepthSampler.setUsage(builder.addDynamicSampler(centerDepthSampler::getCenterDepthTexture, "iris_centerDepthSmooth"));

        return builder.build();
    }

    private ComputeProgram[] createComputes(ComputeSource[] compute, ImmutableSet<Integer> flipped, ImmutableSet<Integer> flippedAtLeastOnceSnapshot, Supplier<ShadowRenderTargets> shadowTargetsSupplier) {
        ComputeProgram[] programs = new ComputeProgram[compute.length];
        for (int i = 0; i < programs.length; i++) {
            ComputeSource source = compute[i];
            if (source == null || !source.getSource().isPresent()) {
                continue;
            } else {
                Objects.requireNonNull(flipped);
                ProgramBuilder builder;

                try {
                    builder = ProgramBuilder.beginCompute(source.getName(), TransformPatcher.patchCompute(source.getSource().get(), textureRenames), LucernaSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS);
                } catch (RuntimeException e) {
                    throw new RuntimeException("Shader compilation failed!", e);
                }

                ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(builder, customTextureIds, flippedAtLeastOnceSnapshot);

                CommonUniforms.addCommonUniforms(builder, source.getParent().getPack().getIdMap(), source.getParent().getPackDirectives(), updateNotifier);
                customUniforms.assignTo(builder);
                LucernaSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, () -> flipped, renderTargets, true);
                LucernaImages.addRenderTargetImages(builder, () -> flipped, renderTargets);

                LucernaSamplers.addNoiseSampler(customTextureSamplerInterceptor, noiseTexture);
                LucernaSamplers.addCompositeSamplers(customTextureSamplerInterceptor, renderTargets);

                if (LucernaSamplers.hasShadowSamplers(customTextureSamplerInterceptor)) {
                    LucernaSamplers.addShadowSamplers(customTextureSamplerInterceptor, shadowTargetsSupplier.get());
                    LucernaImages.addShadowColorImages(builder, shadowTargetsSupplier.get());
                }

                centerDepthSampler.setUsage(builder.addDynamicSampler(centerDepthSampler::getCenterDepthTexture, "iris_centerDepthSmooth"));

                programs[i] = builder.buildCompute();

                programs[i].setWorkGroupInfo(source.getWorkGroupRelative(), source.getWorkGroups());
            }
        }

        return programs;
    }

    public void destroy() {
        for (Pass renderPass : passes) {
            renderPass.destroy();
        }
    }
}
