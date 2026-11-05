package us.z1x.lucerna.postprocess;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL11C;
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
import us.z1x.lucerna.rendertarget.Blaze3dRenderTargetExt;
import us.z1x.lucerna.rendertarget.RenderTarget;
import us.z1x.lucerna.rendertarget.RenderTargets;
import us.z1x.lucerna.samplers.LucernaImages;
import us.z1x.lucerna.samplers.LucernaSamplers;
import us.z1x.lucerna.shaderpack.ComputeSource;
import us.z1x.lucerna.shaderpack.ProgramDirectives;
import us.z1x.lucerna.shaderpack.ProgramSet;
import us.z1x.lucerna.shaderpack.ProgramSource;
import us.z1x.lucerna.shaderpack.texture.TextureStage;
import us.z1x.lucerna.shadows.ShadowRenderTargets;
import us.z1x.lucerna.uniforms.CommonUniforms;
import us.z1x.lucerna.uniforms.FrameUpdateNotifier;
import us.z1x.lucerna.uniforms.custom.CustomUniforms;

import java.util.Map;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class FinalPassRenderer {
    private final RenderTargets renderTargets;

    @Nullable
    private final Pass finalPass;
    private final ImmutableList<SwapPass> swapPasses;
    private final GlFramebuffer baseline;
    private final GlFramebuffer colorHolder;
    private int lastColorTextureId;
    private int lastColorTextureVersion;
    private final IntSupplier noiseTexture;
    private final FrameUpdateNotifier updateNotifier;
    private final CustomUniforms customUniforms;
    private final CenterDepthSampler centerDepthSampler;
    private final Object2ObjectMap<String, IntSupplier> customTextureIds;
    private final Map<String, String> textureRenames;

    public FinalPassRenderer(ProgramSet pack, RenderTargets renderTargets, IntSupplier noiseTexture,
                             FrameUpdateNotifier updateNotifier, ImmutableSet<Integer> flippedBuffers,
                             CenterDepthSampler centerDepthSampler,
                             Supplier<ShadowRenderTargets> shadowTargetsSupplier,
                             Object2ObjectMap<String, IntSupplier> customTextureIds,
                             ImmutableSet<Integer> flippedAtLeastOnce, CustomUniforms customUniforms) {
        this.updateNotifier = updateNotifier;
        this.customUniforms = customUniforms;
        this.centerDepthSampler = centerDepthSampler;
        this.customTextureIds = customTextureIds;
        this.textureRenames = pack.getPack().getCustomTexturePatching(TextureStage.COMPOSITE_AND_FINAL);

        this.noiseTexture = noiseTexture;
        this.renderTargets = renderTargets;
        this.finalPass = pack.getCompositeFinal().map(source -> {
            Pass pass = new Pass();
            ProgramDirectives directives = source.getDirectives();

            pass.program = createProgram(source, flippedBuffers, flippedAtLeastOnce, shadowTargetsSupplier);
            pass.computes = createComputes(pack.getFinalCompute(), flippedBuffers, flippedAtLeastOnce, shadowTargetsSupplier);
            pass.stageReadsFromAlt = flippedBuffers;
            pass.mipmappedBuffers = directives.getMipmappedBuffers();

            return pass;
        }).orElse(null);

        IntList buffersToBeCleared = pack.getPackDirectives().getRenderTargetDirectives().getBuffersToBeCleared();

        // the framebuffer matches the attachments written last since this pass reads from them
        this.baseline = renderTargets.createGbufferFramebuffer(flippedBuffers, new int[] {0});
        this.colorHolder = new GlFramebuffer();
        this.lastColorTextureId = Minecraft.getInstance().getRenderTarget().colorTextureId;
        this.lastColorTextureVersion = ((Blaze3dRenderTargetExt) Minecraft.getInstance().getRenderTarget()).lucerna$getColorBufferVersion();
        this.colorHolder.addColorAttachment(0, lastColorTextureId);

        ImmutableList.Builder<SwapPass> swapPasses = ImmutableList.builder();

        flippedBuffers.forEach((i) -> {
            int target = i;

            if (buffersToBeCleared.contains(target)) {
                return;
            }

            SwapPass swap = new SwapPass();
            RenderTarget target1 = renderTargets.get(target);
            swap.target = target;
            swap.width = target1.getWidth();
            swap.height = target1.getHeight();
            swap.from = renderTargets.createColorFramebuffer(ImmutableSet.of(), new int[] {target});
            swap.targetTexture = renderTargets.get(target).getMainTexture();

            swapPasses.add(swap);
        });

        this.swapPasses = swapPasses.build();

        LucernaRenderSystem.bindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, 0);
    }

    private static final class Pass {
        Program program;
        ComputeProgram[] computes;
        ImmutableSet<Integer> stageReadsFromAlt;
        ImmutableSet<Integer> mipmappedBuffers;

        private void destroy() {
            this.program.destroy();

            for (ComputeProgram compute : this.computes) {
                if (compute != null) {
                    compute.destroy();
                }
            }
        }
    }

    private static final class SwapPass {
        public int target;
        public int width;
        public int height;
        GlFramebuffer from;
        int targetTexture;
    }

    public void renderFinalPass() {
        GlStateManager.disableBlend();
        GlStateManager.disableAlphaTest();
        GlStateManager.depthMask(false);

        final var main = Minecraft.getInstance().getRenderTarget();
        final int baseWidth = renderTargets.getCurrentWidth();
        final int baseHeight = renderTargets.getCurrentHeight();

        if (((Blaze3dRenderTargetExt) main).lucerna$getColorBufferVersion() != lastColorTextureVersion || main.colorTextureId != lastColorTextureId) {
            lastColorTextureVersion = ((Blaze3dRenderTargetExt) main).lucerna$getColorBufferVersion();
            this.lastColorTextureId = main.colorTextureId;
            colorHolder.addColorAttachment(0, lastColorTextureId);
        }

        if (this.finalPass != null) {
            colorHolder.bind();

            FullScreenQuadRenderer.INSTANCE.begin();

            for (ComputeProgram computeProgram : finalPass.computes) {
                if (computeProgram != null) {
                    computeProgram.dispatch(baseWidth, baseHeight);
                }
            }

            LucernaRenderSystem.memoryBarrier(40);

            if (!finalPass.mipmappedBuffers.isEmpty()) {
                GlStateManager.activeTexture(GL15C.GL_TEXTURE0);

                for (int index : finalPass.mipmappedBuffers) {
                    setupMipmapping(renderTargets.get(index), finalPass.stageReadsFromAlt.contains(index));
                }
            }

            // the final pass upscales from the render quality size to the window
            GlStateManager.viewport(0, 0, main.width, main.height);
            finalPass.program.use();
            FullScreenQuadRenderer.INSTANCE.renderQuad();

            FullScreenQuadRenderer.end();
        } else {
            // without a final pass, blit colortex0 into the main framebuffer, which also scales to the window
            LucernaRenderSystem.blitFramebuffer(baseline.getId(), main.frameBufferId, 0, 0, baseWidth, baseHeight,
                0, 0, main.width, main.height, GL11C.GL_COLOR_BUFFER_BIT, GL11C.GL_LINEAR);
        }

        GlStateManager.activeTexture(GL15C.GL_TEXTURE0);

        for (int i = 0; i < renderTargets.getRenderTargetCount(); i++) {
            resetRenderTarget(renderTargets.get(i));
        }

        for (SwapPass swapPass : swapPasses) {
            // use bind(), not bindAsReadBuffer(), glCopyTexSubImage2D reads from GL_FRAMEBUFFER not GL_READ_FRAMEBUFFER
            // bindAsReadBuffer() broke taa on many packs and drivers
            swapPass.from.bind();

            GlStateManager.bindTexture(swapPass.targetTexture);
            GL11.glCopyTexSubImage2D(GL20C.GL_TEXTURE_2D, 0, 0, 0, 0, 0, swapPass.width, swapPass.height);
        }

        main.bindWrite(true);
        ProgramUniforms.clearActiveUniforms();
        ProgramSamplers.clearActiveSamplers();
        LucernaRenderSystem.useProgram(0);

        for (int i = 0; i < SamplerLimits.get().getMaxTextureUnits(); i++) {
            // unbind used textures so shader pack reloading works
            GlStateManager.activeTexture(GL15C.GL_TEXTURE0 + i);
            GlStateManager.bindTexture(0);
        }

        GlStateManager.activeTexture(GL15C.GL_TEXTURE0);
    }

    public void recalculateSwapPassSize() {
        for (SwapPass swapPass : swapPasses) {
            RenderTarget target = renderTargets.get(swapPass.target);
            renderTargets.destroyFramebuffer(swapPass.from);
            swapPass.from = renderTargets.createColorFramebuffer(ImmutableSet.of(), new int[] {swapPass.target});
            swapPass.width = target.getWidth();
            swapPass.height = target.getHeight();
            swapPass.targetTexture = target.getMainTexture();
        }
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

    private static void resetRenderTarget(RenderTarget target) {
        // reset the sampling mode so the target is not sampled elsewhere by accident
        int filter = GL20C.GL_LINEAR;
        if (target.getInternalFormat().getPixelFormat().isInteger()) {
            filter = GL20C.GL_NEAREST;
        }

        LucernaRenderSystem.texParameteri(target.getMainTexture(), GL20C.GL_TEXTURE_2D, GL20C.GL_TEXTURE_MIN_FILTER, filter);
        LucernaRenderSystem.texParameteri(target.getAltTexture(), GL20C.GL_TEXTURE_2D, GL20C.GL_TEXTURE_MIN_FILTER, filter);

        GlStateManager.bindTexture(0);
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
        if (finalPass != null) {
            finalPass.destroy();
        }

        colorHolder.destroy();
    }
}
