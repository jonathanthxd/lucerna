package us.z1x.lucerna.samplers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.minecraft.client.render.texture.AbstractTexture;
import us.z1x.lucerna.gbufferoverrides.matching.InputAvailability;
import us.z1x.lucerna.gl.image.CustomImage;
import us.z1x.lucerna.gl.sampler.SamplerHolder;
import us.z1x.lucerna.gl.state.StateUpdateNotifiers;
import us.z1x.lucerna.pipeline.WorldRenderingPipeline;
import us.z1x.lucerna.rendertarget.RenderTarget;
import us.z1x.lucerna.rendertarget.RenderTargets;
import us.z1x.lucerna.shaderpack.PackRenderTargetDirectives;
import us.z1x.lucerna.shadows.ShadowRenderTargets;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class LucernaSamplers {
    public static final int ALBEDO_TEXTURE_UNIT = 0;
    public static final int OVERLAY_TEXTURE_UNIT = 2;
    public static final int LIGHTMAP_TEXTURE_UNIT = 1;

    public static final ImmutableSet<Integer> WORLD_RESERVED_TEXTURE_UNITS = ImmutableSet.of(0, 1, 2);

    public static final ImmutableSet<Integer> COMPOSITE_RESERVED_TEXTURE_UNITS = ImmutableSet.of(1, 2);

    private LucernaSamplers() {
    }

    public static void addRenderTargetSamplers(SamplerHolder samplers, Supplier<ImmutableSet<Integer>> flipped,
                                               RenderTargets renderTargets, boolean isFullscreenPass) {
        // colortex0 to colortex3 can only be sampled in fullscreen passes
        int startIndex = isFullscreenPass ? 0 : 4;

        for (int i = startIndex; i < renderTargets.getRenderTargetCount(); i++) {
            final int index = i;

            IntSupplier sampler = () -> {
                ImmutableSet<Integer> flippedBuffers = flipped.get();
                RenderTarget target = renderTargets.get(index);

                if (flippedBuffers.contains(index)) {
                    return target.getAltTexture();
                } else {
                    return target.getMainTexture();
                }
            };

            final String name = "colortex" + i;

            if (i < PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.size()) {
                String legacyName = PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.get(i);

                // colortex0 is the default sampler in fullscreen passes
                if (i == 0 && isFullscreenPass) {
                    samplers.addDefaultSampler(sampler, name, legacyName);
                } else {
                    samplers.addDynamicSampler(sampler, name, legacyName);
                }
            } else {
                samplers.addDynamicSampler(sampler, name);
            }
        }

        for (CustomImage image : renderTargets.getCustomImages()) {
            if (image.getInfo().samplerName() != null) {
                samplers.addDynamicSampler(image::getTexture, image.getInfo().samplerName());
            }
        }
    }

    public static void addNoiseSampler(SamplerHolder samplers, IntSupplier sampler) {
        samplers.addDynamicSampler(sampler, "noisetex");
    }

    public static boolean hasShadowSamplers(SamplerHolder samplers) {
        ImmutableList<String> shadowSamplers = ImmutableList.of("shadowtex0", "shadowtex0HW", "shadowtex1", "shadowtex1HW", "shadow", "watershadow",
                "shadowcolor", "shadowcolor0", "shadowcolor1", "shadowcolorimg0", "shadowcolorimg1");

        for (String samplerName : shadowSamplers) {
            if (samplers.hasSampler(samplerName)) {
                return true;
            }
        }

        return false;
    }

    public static boolean addShadowSamplers(SamplerHolder samplers, ShadowRenderTargets shadowRenderTargets) {
        boolean usesShadows;

        boolean waterShadowEnabled = samplers.hasSampler("watershadow");

        if (waterShadowEnabled) {
            usesShadows = true;
            samplers.addDynamicSampler(shadowRenderTargets.getDepthTexture()::getTextureId, "shadowtex0", "watershadow");
            samplers.addDynamicSampler(shadowRenderTargets.getDepthTextureNoTranslucents()::getTextureId,
                    "shadowtex1", "shadow");
        } else {
            usesShadows = samplers.addDynamicSampler(shadowRenderTargets.getDepthTexture()::getTextureId, "shadowtex0", "shadow");
            usesShadows |= samplers.addDynamicSampler(shadowRenderTargets.getDepthTextureNoTranslucents()::getTextureId, "shadowtex1");
        }

        samplers.addDynamicSampler(() -> shadowRenderTargets.getColorTextureId(0), "shadowcolor", "shadowcolor0");
        samplers.addDynamicSampler(() -> shadowRenderTargets.getColorTextureId(1), "shadowcolor1");

        if (shadowRenderTargets.isHardwareFiltered(0)) {
            samplers.addDynamicSampler(shadowRenderTargets.getDepthTexture()::getTextureId, "shadowtex0HW");
        }

        if (shadowRenderTargets.isHardwareFiltered(1)) {
            samplers.addDynamicSampler(shadowRenderTargets.getDepthTextureNoTranslucents()::getTextureId, "shadowtex1HW");
        }

        return usesShadows;
    }

    public static boolean hasPBRSamplers(SamplerHolder samplers) {
        return samplers.hasSampler("normals") || samplers.hasSampler("specular");
    }

    public static void addLevelSamplers(SamplerHolder samplers, WorldRenderingPipeline pipeline, AbstractTexture whitePixel, InputAvailability availability) {
        if (availability.texture) {
            samplers.addExternalSampler(ALBEDO_TEXTURE_UNIT, "tex", "texture", "gtexture");
        } else {
            samplers.addDynamicSampler(whitePixel::getGlId, "tex", "texture", "gtexture",
                    "gcolor", "colortex0");
        }

        if (availability.lightmap) {
            samplers.addExternalSampler(LIGHTMAP_TEXTURE_UNIT, "lightmap");
        } else {
            samplers.addDynamicSampler(whitePixel::getGlId, "lightmap");
        }

        if (availability.overlay) {
            samplers.addExternalSampler(OVERLAY_TEXTURE_UNIT, "iris_overlay");
        } else {
            samplers.addDynamicSampler(whitePixel::getGlId, "iris_overlay");
        }

        samplers.addDynamicSampler(pipeline::getCurrentNormalTexture, StateUpdateNotifiers.normalTextureChangeNotifier, "normals");
        samplers.addDynamicSampler(pipeline::getCurrentSpecularTexture, StateUpdateNotifiers.specularTextureChangeNotifier, "specular");
    }

    public static void addWorldDepthSamplers(SamplerHolder samplers, RenderTargets renderTargets) {
        samplers.addDynamicSampler(renderTargets::getDepthTexture, "depthtex0");
        samplers.addDynamicSampler(renderTargets.getDepthTextureNoTranslucents()::getTextureId, "depthtex1");
    }

    public static void addCompositeSamplers(SamplerHolder samplers, RenderTargets renderTargets) {
        samplers.addDynamicSampler(renderTargets::getDepthTexture,
                "gdepthtex", "depthtex0");
        samplers.addDynamicSampler(renderTargets.getDepthTextureNoTranslucents()::getTextureId,
                "depthtex1");
        samplers.addDynamicSampler(renderTargets.getDepthTextureNoHand()::getTextureId,
                "depthtex2");
    }
}
