package us.z1x.lucerna.pipeline;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.AbstractTexture;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.opengl.GL30C;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.blockrendering.BlockMaterialMapping;
import us.z1x.lucerna.blockrendering.BlockRenderingSettings;
import us.z1x.lucerna.gbufferoverrides.matching.InputAvailability;
import us.z1x.lucerna.gbufferoverrides.matching.ProgramTable;
import us.z1x.lucerna.gbufferoverrides.matching.RenderCondition;
import us.z1x.lucerna.gbufferoverrides.matching.SpecialCondition;
import us.z1x.lucerna.gbufferoverrides.state.RenderTargetStateListener;
import us.z1x.lucerna.gbufferoverrides.state.StateTracker;
import us.z1x.lucerna.gl.LucernaRenderSystem;
import us.z1x.lucerna.gl.blending.AlphaTestOverride;
import us.z1x.lucerna.gl.blending.BlendModeOverride;
import us.z1x.lucerna.gl.blending.BufferBlendOverride;
import us.z1x.lucerna.gl.framebuffer.GlFramebuffer;
import us.z1x.lucerna.gl.program.ComputeProgram;
import us.z1x.lucerna.gl.program.Program;
import us.z1x.lucerna.gl.program.ProgramBuilder;
import us.z1x.lucerna.gl.program.ProgramImages;
import us.z1x.lucerna.gl.program.ProgramSamplers;
import us.z1x.lucerna.gl.texture.DepthBufferFormat;
import us.z1x.lucerna.gui.option.LucernaVideoSettings;
import us.z1x.lucerna.layer.GbufferPrograms;
import us.z1x.lucerna.pipeline.transform.PatchShaderType;
import us.z1x.lucerna.pipeline.transform.TransformPatcher;
import us.z1x.lucerna.postprocess.BufferFlipper;
import us.z1x.lucerna.postprocess.CenterDepthSampler;
import us.z1x.lucerna.postprocess.CompositeRenderer;
import us.z1x.lucerna.postprocess.FinalPassRenderer;
import us.z1x.lucerna.rendertarget.DepthTexture;
import us.z1x.lucerna.rendertarget.NativeImageBackedSingleColorTexture;
import us.z1x.lucerna.rendertarget.RenderTargets;
import us.z1x.lucerna.samplers.LucernaImages;
import us.z1x.lucerna.samplers.LucernaSamplers;
import us.z1x.lucerna.shaderpack.CloudSetting;
import us.z1x.lucerna.shaderpack.ComputeSource;
import us.z1x.lucerna.shaderpack.IdMap;
import us.z1x.lucerna.shaderpack.OptionalBoolean;
import us.z1x.lucerna.shaderpack.PackDirectives;
import us.z1x.lucerna.shaderpack.PackShadowDirectives;
import us.z1x.lucerna.shaderpack.ProgramDirectives;
import us.z1x.lucerna.shaderpack.ProgramFallbackResolver;
import us.z1x.lucerna.shaderpack.ProgramSet;
import us.z1x.lucerna.shaderpack.ProgramSource;
import us.z1x.lucerna.shaderpack.loading.ProgramId;
import us.z1x.lucerna.shaderpack.texture.TextureStage;
import us.z1x.lucerna.shadows.ShadowRenderTargets;
import us.z1x.lucerna.texture.pbr.PBRTextureManager;
import us.z1x.lucerna.texture.pbr.PBRTextures;
import us.z1x.lucerna.uniforms.CapturedRenderingState;
import us.z1x.lucerna.uniforms.CommonUniforms;
import us.z1x.lucerna.uniforms.FrameUpdateNotifier;
import us.z1x.lucerna.uniforms.custom.CustomUniforms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public class DeferredWorldRenderingPipeline implements WorldRenderingPipeline, RenderTargetStateListener  {
    private static final Vector4f EMPTY_CLEAR_COLOR = new Vector4f(1.0F);

    private final RenderTargets renderTargets;
    private final DepthTexture mainDepth;

    @Nullable
    private ShadowRenderTargets shadowRenderTargets;
    @Nullable
    private ComputeProgram[] shadowComputes;
    private final Supplier<ShadowRenderTargets> shadowTargetsSupplier;

    private final ProgramTable<Pass> table;

    private ImmutableList<ClearPass> clearPassesFull;
    private ImmutableList<ClearPass> clearPasses;
    private ImmutableList<ClearPass> shadowClearPasses;
    private ImmutableList<ClearPass> shadowClearPassesFull;

    private final CompositeRenderer setupRenderer;
    private final CompositeRenderer beginRenderer;
    private boolean setupDone;
    private final CompositeRenderer prepareRenderer;

    @Nullable
    private final ShadowRenderer shadowRenderer;

    private final int shadowMapResolution;
    private final CompositeRenderer deferredRenderer;
    private final CompositeRenderer compositeRenderer;
    private final FinalPassRenderer finalPassRenderer;
    private final CustomTextureManager customTextureManager;
    private final AbstractTexture whitePixel;
    private final AbstractTexture defaultNormal;
    private final AbstractTexture defaultSpecular;
    private final FrameUpdateNotifier updateNotifier;
    private final CenterDepthSampler centerDepthSampler;

    // [(textured=false,lightmap=false), (textured=true,lightmap=false), (textured=true,lightmap=true)]
    private static final ProgramId[] PROGRAM_IDS = {
        ProgramId.Basic, ProgramId.Textured, ProgramId.TexturedLit,
        ProgramId.SkyBasic, ProgramId.SkyTextured, ProgramId.SkyTextured,
        null, null, ProgramId.Terrain,
        null, null, ProgramId.Water,
        null, ProgramId.Clouds, ProgramId.Clouds,
        null, ProgramId.DamagedBlock, ProgramId.DamagedBlock,
        ProgramId.Block, ProgramId.Block, ProgramId.Block,
        ProgramId.BeaconBeam, ProgramId.BeaconBeam, ProgramId.BeaconBeam,
        ProgramId.Entities, ProgramId.Entities, ProgramId.Entities,
        ProgramId.EntitiesTrans, ProgramId.EntitiesTrans, ProgramId.EntitiesTrans,
        null, ProgramId.ArmorGlint, ProgramId.ArmorGlint,
        null, ProgramId.SpiderEyes, ProgramId.SpiderEyes,
        ProgramId.Hand, ProgramId.Hand, ProgramId.Hand,
        ProgramId.HandWater, ProgramId.HandWater, ProgramId.HandWater,
        null, null, ProgramId.Weather,
        // world border uses textured_lit even though it has no lightmap
        null, ProgramId.TexturedLit, ProgramId.TexturedLit,
        ProgramId.Shadow, ProgramId.Shadow, ProgramId.Shadow
    };

    private final ImmutableSet<Integer> flippedBeforeShadow;
    private final ImmutableSet<Integer> flippedAfterPrepare;
    private final ImmutableSet<Integer> flippedAfterTranslucent;

    private final CeleritasTerrainPipeline celeritasTerrainPipeline;

    private final HorizonRenderer horizonRenderer = new HorizonRenderer();

    private final float sunPathRotation;
    private final CloudSetting cloudSetting;
    private final Map<String, String> gbufferTextureRenames;
    private final boolean shouldRenderUnderwaterOverlay;
    private final boolean shouldRenderVignette;
    private final boolean shouldRenderSun;
    private final boolean shouldRenderMoon;
    private final boolean shouldWriteRainAndSnowToDepthBuffer;
    private final boolean shouldRenderParticlesBeforeDeferred;
    private final boolean shouldRenderPrepareBeforeShadow;
    private final boolean oldLighting;
    private final boolean allowConcurrentCompute;
    private final OptionalInt forcedShadowRenderDistanceChunks;

    private Pass current = null;

    private WorldRenderingPhase overridePhase = null;
    private WorldRenderingPhase phase = WorldRenderingPhase.NONE;
    private boolean isBeforeTranslucent;
    private boolean isRenderingShadow = false;
    private InputAvailability inputs = new InputAvailability(false, false, false);
    private SpecialCondition special = null;

    private boolean shouldBindPBR;
    private int currentNormalTexture;
    private int currentSpecularTexture;
    private PackDirectives packDirectives;
    private final CustomUniforms customUniforms;

    public DeferredWorldRenderingPipeline(ProgramSet programs) {
        Objects.requireNonNull(programs);

        this.gbufferTextureRenames = programs.getPack().getCustomTexturePatching(TextureStage.GBUFFERS_AND_SHADOW);

        this.cloudSetting = programs.getPackDirectives().getCloudSetting();
        this.shouldRenderUnderwaterOverlay = programs.getPackDirectives().underwaterOverlay();
        this.shouldRenderVignette = programs.getPackDirectives().vignette();
        this.shouldRenderSun = programs.getPackDirectives().shouldRenderSun();
        this.shouldRenderMoon = programs.getPackDirectives().shouldRenderMoon();
        this.shouldWriteRainAndSnowToDepthBuffer = programs.getPackDirectives().rainDepth();
        this.shouldRenderParticlesBeforeDeferred = programs.getPackDirectives().areParticlesBeforeDeferred();
        this.allowConcurrentCompute = programs.getPackDirectives().getConcurrentCompute();
        this.shouldRenderPrepareBeforeShadow = programs.getPackDirectives().isPrepareBeforeShadow();
        this.oldLighting = programs.getPackDirectives().isOldLighting();
        this.updateNotifier = new FrameUpdateNotifier();

        this.packDirectives = programs.getPackDirectives();
        this.customUniforms = programs.getPack().getCustomUniforms().build(programs.getPack().getIdMap(), programs.getPackDirectives(), updateNotifier);

        RenderTarget mainTarget = Minecraft.getInstance().getRenderTarget();

        // the vanilla main framebuffer uses a depth renderbuffer, so the gbuffers get their own depth texture
        this.mainDepth = new DepthTexture(mainTarget.width, mainTarget.height, DepthBufferFormat.DEPTH24);

        this.renderTargets = new RenderTargets(mainTarget.width, mainTarget.height, mainDepth.getTextureId(), 0,
            DepthBufferFormat.DEPTH24, programs.getPackDirectives().getRenderTargetDirectives().getRenderTargetSettings(), programs.getPackDirectives());

        this.sunPathRotation = programs.getPackDirectives().getSunPathRotation();

        PackShadowDirectives shadowDirectives = programs.getPackDirectives().getShadowDirectives();

        if (shadowDirectives.isDistanceRenderMulExplicit()) {
            if (shadowDirectives.getDistanceRenderMul() >= 0.0) {
                // add 15 before dividing by 16 to round up
                forcedShadowRenderDistanceChunks =
                        OptionalInt.of(((int) (shadowDirectives.getDistance() * shadowDirectives.getDistanceRenderMul()) + 15) / 16);
            } else {
                forcedShadowRenderDistanceChunks = OptionalInt.of(-1);
            }
        } else {
            forcedShadowRenderDistanceChunks = OptionalInt.empty();
        }

        BlockRenderingSettings.INSTANCE.setBlockStateIds(
                BlockMaterialMapping.createBlockStateIdMap(programs.getPack().getIdMap().getBlockProperties()));
        BlockRenderingSettings.INSTANCE.setBlockTypeIds(BlockMaterialMapping.createBlockTypeMap(programs.getPack().getIdMap().getBlockRenderTypeMap()));

        BlockRenderingSettings.INSTANCE.setEntityIds(programs.getPack().getIdMap().getEntityIdMap());
        BlockRenderingSettings.INSTANCE.setItemIds(programs.getPack().getIdMap().getItemIdMap());
        BlockRenderingSettings.INSTANCE.setAmbientOcclusionLevel(programs.getPackDirectives().getAmbientOcclusionLevel());
        BlockRenderingSettings.INSTANCE.setDisableDirectionalShading(shouldDisableDirectionalShading());
        BlockRenderingSettings.INSTANCE.setUseSeparateAo(programs.getPackDirectives().shouldUseSeparateAo());
        BlockRenderingSettings.INSTANCE.setUseExtendedVertexFormat(true);

        // avoid clobbering texture unit 0
        GlStateManager.activeTexture(GL20C.GL_TEXTURE2);

        customTextureManager = new CustomTextureManager(programs.getPackDirectives(), programs.getPack().getCustomTextureDataMap(), programs.getPack().getCustomNoiseTexture());

        whitePixel = new NativeImageBackedSingleColorTexture(255, 255, 255, 255);
        defaultNormal = new NativeImageBackedSingleColorTexture(127, 127, 255, 255);
        defaultSpecular = new NativeImageBackedSingleColorTexture(0, 0, 0, 0);
        currentNormalTexture = defaultNormal.getGlId();
        currentSpecularTexture = defaultSpecular.getGlId();

        GlStateManager.activeTexture(GL20C.GL_TEXTURE0);

        this.flippedBeforeShadow = ImmutableSet.of();

        BufferFlipper flipper = new BufferFlipper();

        this.centerDepthSampler = new CenterDepthSampler(() -> getRenderTargets().getDepthTexture(), programs.getPackDirectives().getCenterDepthHalfLife());

        this.shadowMapResolution = programs.getPackDirectives().getShadowDirectives().getResolution();

        this.shadowTargetsSupplier = () -> {
            if (shadowRenderTargets == null) {
                this.shadowRenderTargets = new ShadowRenderTargets(shadowMapResolution, shadowDirectives);
            }

            return shadowRenderTargets;
        };

        PatchedShaderPrinter.resetPrintState();

        this.setupRenderer = new CompositeRenderer(programs.getPackDirectives(), new ProgramSource[99], programs.getSetupCompute(), renderTargets,
                customTextureManager.getNoiseTexture(), updateNotifier, centerDepthSampler, flipper, shadowTargetsSupplier,
                Object2ObjectMaps.emptyMap(), Map.of(), ImmutableMap.of(), customUniforms);
        this.beginRenderer = new CompositeRenderer(programs.getPackDirectives(), new ProgramSource[99], programs.getBeginCompute(), renderTargets,
                customTextureManager.getNoiseTexture(), updateNotifier, centerDepthSampler, flipper, shadowTargetsSupplier,
                Object2ObjectMaps.emptyMap(), Map.of(), ImmutableMap.of(), customUniforms);

        this.prepareRenderer = new CompositeRenderer(programs.getPackDirectives(), programs.getPrepare(), programs.getPrepareCompute(), renderTargets,
                customTextureManager.getNoiseTexture(), updateNotifier, centerDepthSampler, flipper, shadowTargetsSupplier,
                customTextureManager.getCustomTextureIdMap(TextureStage.PREPARE), programs.getPack().getCustomTexturePatching(TextureStage.PREPARE),
                programs.getPackDirectives().getExplicitFlips("prepare_pre"), customUniforms);

        flippedAfterPrepare = flipper.snapshot();

        this.deferredRenderer = new CompositeRenderer(programs.getPackDirectives(), programs.getDeferred(), programs.getDeferredCompute(), renderTargets,
                customTextureManager.getNoiseTexture(), updateNotifier, centerDepthSampler, flipper, shadowTargetsSupplier,
                customTextureManager.getCustomTextureIdMap(TextureStage.DEFERRED), programs.getPack().getCustomTexturePatching(TextureStage.DEFERRED),
                programs.getPackDirectives().getExplicitFlips("deferred_pre"), customUniforms);

        flippedAfterTranslucent = flipper.snapshot();

        this.compositeRenderer = new CompositeRenderer(programs.getPackDirectives(), programs.getComposite(), programs.getCompositeCompute(), renderTargets,
                customTextureManager.getNoiseTexture(), updateNotifier, centerDepthSampler, flipper, shadowTargetsSupplier,
                customTextureManager.getCustomTextureIdMap(TextureStage.COMPOSITE_AND_FINAL), programs.getPack().getCustomTexturePatching(TextureStage.COMPOSITE_AND_FINAL),
                programs.getPackDirectives().getExplicitFlips("composite_pre"), customUniforms);
        this.finalPassRenderer = new FinalPassRenderer(programs, renderTargets, customTextureManager.getNoiseTexture(), updateNotifier, flipper.snapshot(),
                centerDepthSampler, shadowTargetsSupplier,
                customTextureManager.getCustomTextureIdMap(TextureStage.COMPOSITE_AND_FINAL),
                this.compositeRenderer.getFlippedAtLeastOnceFinal(), customUniforms);

        ProgramFallbackResolver resolver = new ProgramFallbackResolver(programs);

        Map<PassKey, Pass> cachedPasses = new HashMap<>();

        this.shadowComputes = createShadowComputes(programs.getShadowCompute(), programs);

        this.table = new ProgramTable<>((condition, availability) -> {
            ProgramId id = programIdFor(condition, availability);

            return cachedPasses.computeIfAbsent(new PassKey(id, availability), p -> {
                ProgramSource source = resolver.resolveNullable(p.id());

                if (condition == RenderCondition.SHADOW) {
                    if (!shadowDirectives.isShadowEnabled().orElse(shadowRenderTargets != null)) {
                        return null;
                    } else if (source == null) {
                        // still need the custom framebuffer, viewport, and blend mode behavior
                        GlFramebuffer shadowFb =
                            shadowTargetsSupplier.get().createShadowFramebuffer(shadowRenderTargets.snapshot(), new int[] {0});
                        return new Pass(null, shadowFb, shadowFb, null,
                            BlendModeOverride.OFF, Collections.emptyList(), true);
                    }
                }

                if (source == null) {
                    return createDefaultPass();
                }

                try {
                    return createPass(source, availability, condition == RenderCondition.SHADOW, id);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create pass for " + source.getName() + " for rendering condition "
                        + condition + " specialized to input availability " + availability, e);
                }
            });
        });

        if (shadowRenderTargets == null && shadowDirectives.isShadowEnabled() == OptionalBoolean.TRUE) {
            shadowRenderTargets = new ShadowRenderTargets(shadowMapResolution, shadowDirectives);
        }

        if (shadowRenderTargets != null) {
            this.shadowClearPasses = ClearPassCreator.createShadowClearPasses(shadowRenderTargets, false, shadowDirectives);
            this.shadowClearPassesFull = ClearPassCreator.createShadowClearPasses(shadowRenderTargets, true, shadowDirectives);

            if (programs.getPackDirectives().getShadowDirectives().isShadowEnabled().orElse(true)) {
                this.shadowRenderer = new ShadowRenderer(programs.getShadow().orElse(null),
                    programs.getPackDirectives(), shadowRenderTargets);
                Program shadowProgram = table.match(RenderCondition.SHADOW, new InputAvailability(true, true, true)).getProgram();
                shadowRenderer.setUsesImages(shadowProgram != null && shadowProgram.getActiveImages() > 0);
            } else {
                shadowRenderer = null;
            }
        } else {
            this.shadowClearPasses = ImmutableList.of();
            this.shadowClearPassesFull = ImmutableList.of();
            this.shadowRenderer = null;
        }

        this.clearPassesFull = ClearPassCreator.createClearPasses(renderTargets, true,
                programs.getPackDirectives().getRenderTargetDirectives());
        this.clearPasses = ClearPassCreator.createClearPasses(renderTargets, false,
                programs.getPackDirectives().getRenderTargetDirectives());

        Supplier<ImmutableSet<Integer>> flipped =
            () -> isBeforeTranslucent ? flippedAfterPrepare : flippedAfterTranslucent;

        IntFunction<ProgramSamplers> createTerrainSamplers = (programId) -> {
            ProgramSamplers.Builder builder = ProgramSamplers.builder(programId, LucernaSamplers.WORLD_RESERVED_TEXTURE_UNITS);
            ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(builder, customTextureManager.getCustomTextureIdMap(TextureStage.GBUFFERS_AND_SHADOW));

            LucernaSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, flipped, renderTargets, false);
            LucernaSamplers.addLevelSamplers(customTextureSamplerInterceptor, this, whitePixel, new InputAvailability(true, true, false));
            LucernaSamplers.addWorldDepthSamplers(customTextureSamplerInterceptor, renderTargets);
            LucernaSamplers.addNoiseSampler(customTextureSamplerInterceptor, customTextureManager.getNoiseTexture());

            if (LucernaSamplers.hasShadowSamplers(customTextureSamplerInterceptor)) {
                LucernaSamplers.addShadowSamplers(customTextureSamplerInterceptor, Objects.requireNonNull(shadowRenderTargets));
            }

            return builder.build();
        };

        IntFunction<ProgramImages> createTerrainImages = (programId) -> {
            ProgramImages.Builder builder = ProgramImages.builder(programId);

            LucernaImages.addRenderTargetImages(builder, flipped, renderTargets);

            if (LucernaImages.hasShadowImages(builder)) {
                LucernaImages.addShadowColorImages(builder, Objects.requireNonNull(shadowRenderTargets));
            }

            return builder.build();
        };

        IntFunction<ProgramSamplers> createShadowTerrainSamplers = (programId) -> {
            ProgramSamplers.Builder builder = ProgramSamplers.builder(programId, LucernaSamplers.WORLD_RESERVED_TEXTURE_UNITS);
            ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(builder, customTextureManager.getCustomTextureIdMap(TextureStage.GBUFFERS_AND_SHADOW));

            LucernaSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, () -> flippedAfterPrepare, renderTargets, false);
            LucernaSamplers.addLevelSamplers(customTextureSamplerInterceptor, this, whitePixel, new InputAvailability(true, true, false));
            LucernaSamplers.addNoiseSampler(customTextureSamplerInterceptor, customTextureManager.getNoiseTexture());

            if (LucernaSamplers.hasShadowSamplers(customTextureSamplerInterceptor)) {
                LucernaSamplers.addShadowSamplers(customTextureSamplerInterceptor, Objects.requireNonNull(shadowRenderTargets));
            }

            return builder.build();
        };

        IntFunction<ProgramImages> createShadowTerrainImages = (programId) -> {
            ProgramImages.Builder builder = ProgramImages.builder(programId);

            LucernaImages.addRenderTargetImages(builder, () -> flippedAfterPrepare, renderTargets);

            if (LucernaImages.hasShadowImages(builder)) {
                LucernaImages.addShadowColorImages(builder, Objects.requireNonNull(shadowRenderTargets));
            }

            return builder.build();
        };

        this.celeritasTerrainPipeline = new CeleritasTerrainPipeline(this, programs, createTerrainSamplers,
            shadowRenderer == null ? null : createShadowTerrainSamplers, createTerrainImages,
            shadowRenderer == null ? null : createShadowTerrainImages, renderTargets, flippedAfterPrepare, flippedAfterTranslucent,
            () -> shadowRenderTargets.createShadowFramebuffer(shadowRenderTargets.snapshot(), new int[] {0, 1}), customUniforms);
    }

    private record PassKey(ProgramId id, InputAvailability availability) {
    }

    private RenderTargets getRenderTargets() {
        return renderTargets;
    }

    private void checkWorld() {
        if (Minecraft.getInstance().world == null) {
            isRenderingWorld = false;
            current = null;
        }
    }

    @Override
    public boolean shouldDisableVanillaEntityShadows() {
        // optifine seems to disable vanilla shadows when the pack uses shadow mapping
        return shadowRenderer != null;
    }

    @Override
    public boolean shouldDisableDirectionalShading() {
        return !oldLighting;
    }

    @Override
    public CloudSetting getCloudSetting() {
        return cloudSetting;
    }

    @Override
    public boolean shouldRenderUnderwaterOverlay() {
        return shouldRenderUnderwaterOverlay;
    }

    @Override
    public boolean shouldRenderVignette() {
        return shouldRenderVignette;
    }

    @Override
    public boolean shouldRenderSun() {
        return shouldRenderSun;
    }

    @Override
    public boolean shouldRenderMoon() {
        return shouldRenderMoon;
    }

    @Override
    public boolean shouldWriteRainAndSnowToDepthBuffer() {
        return shouldWriteRainAndSnowToDepthBuffer;
    }

    @Override
    public boolean shouldRenderParticlesBeforeDeferred() {
        return shouldRenderParticlesBeforeDeferred;
    }

    @Override
    public boolean allowConcurrentCompute() {
        return allowConcurrentCompute;
    }

    @Override
    public float getSunPathRotation() {
        return sunPathRotation;
    }

    private RenderCondition getCondition(WorldRenderingPhase phase) {
        if (isRenderingShadow) {
            return RenderCondition.SHADOW;
        }

        if (special != null) {
            if (special == SpecialCondition.BEACON_BEAM) {
                return RenderCondition.BEACON_BEAM;
            } else if (special == SpecialCondition.ENTITY_EYES) {
                return RenderCondition.ENTITY_EYES;
            } else if (special == SpecialCondition.GLINT) {
                return RenderCondition.GLINT;
            }
        }

        switch (phase) {
            case NONE:
            case OUTLINE:
            case DEBUG:
            case PARTICLES:
                return RenderCondition.DEFAULT;
            case SKY:
            case SUNSET:
            case CUSTOM_SKY:
            case SUN:
            case MOON:
            case STARS:
            case VOID:
                return RenderCondition.SKY;
            case TERRAIN_SOLID:
            case TERRAIN_CUTOUT:
            case TERRAIN_CUTOUT_MIPPED:
                return RenderCondition.TERRAIN_OPAQUE;
            case ENTITIES:
                if (GlStateManager.BLEND.sfactorRGB == GL11.GL_SRC_ALPHA && GlStateManager.BLEND.dfactorRGB == GL11.GL_ONE_MINUS_SRC_ALPHA && GlStateManager.BLEND.sfactorAlpha == GL11.GL_ONE && GlStateManager.BLEND.dfactorAlpha == GL11.GL_ONE_MINUS_SRC_ALPHA) {
                    return RenderCondition.ENTITIES_TRANSLUCENT;
                } else {
                    return RenderCondition.ENTITIES;
                }
            case BLOCK_ENTITIES:
                return RenderCondition.BLOCK_ENTITIES;
            case DESTROY:
                return RenderCondition.DESTROY;
            case HAND_SOLID:
                return RenderCondition.HAND_OPAQUE;
            case TERRAIN_TRANSLUCENT:
            case TRIPWIRE:
                return RenderCondition.TERRAIN_TRANSLUCENT;
            case CLOUDS:
                return RenderCondition.CLOUDS;
            case RAIN_SNOW:
                return RenderCondition.RAIN_SNOW;
            case HAND_TRANSLUCENT:
                return RenderCondition.HAND_TRANSLUCENT;
            case WORLD_BORDER:
                return RenderCondition.WORLD_BORDER;
            default:
                throw new IllegalStateException("Unknown render phase " + phase);
        }
    }

    private void matchPass() {
        if (!isRenderingWorld || isRenderingFullScreenPass || isPostChain || !isMainBound) {
            return;
        }

        if (celeritasTerrainRendering) {
            beginPass(table.match(getCondition(getPhase()), new InputAvailability(true, true, false)));
            return;
        }

        beginPass(table.match(getCondition(getPhase()), inputs));
    }

    public void beginPass(Pass pass) {
        if (current == pass) {
            return;
        }

        if (current != null) {
            current.stopUsing();
        }

        current = pass;

        if (pass != null) {
            pass.use();
        } else {
            Program.unbind();
        }
    }

    private Pass createDefaultPass() {
        GlFramebuffer framebufferBeforeTranslucents;
        GlFramebuffer framebufferAfterTranslucents;

        framebufferBeforeTranslucents =
            renderTargets.createGbufferFramebuffer(flippedAfterPrepare, new int[] {0});
        framebufferAfterTranslucents =
            renderTargets.createGbufferFramebuffer(flippedAfterTranslucent, new int[] {0});

        return new Pass(null, framebufferBeforeTranslucents, framebufferAfterTranslucents, null,
            null, Collections.emptyList(), false);
    }

    public static ProgramId programIdFor(RenderCondition condition, InputAvailability availability) {
        int index = availability.texture ? (availability.lightmap ? 2 : 1) : 0;
        ProgramId id = PROGRAM_IDS[condition.ordinal() * 3 + index];

        return id != null ? id : PROGRAM_IDS[index];
    }

    private Pass createPass(ProgramSource source, InputAvailability availability, boolean shadow, ProgramId id) {
        Map<PatchShaderType, String> transformed = TransformPatcher.patchAttributes(
            source.getVertexSource().orElseThrow(NullPointerException::new),
            source.getGeometrySource().orElse(null),
            source.getFragmentSource().orElseThrow(NullPointerException::new),
            availability, gbufferTextureRenames);
        String vertex = transformed.get(PatchShaderType.VERTEX);
        String geometry = transformed.get(PatchShaderType.GEOMETRY);
        String fragment = transformed.get(PatchShaderType.FRAGMENT);

        PatchedShaderPrinter.debugPatchedShaders(source.getName(), vertex, geometry, fragment);

        ProgramBuilder builder = ProgramBuilder.begin(source.getName(), vertex, geometry, fragment,
            LucernaSamplers.WORLD_RESERVED_TEXTURE_UNITS);

        return createPassInner(builder, source.getParent().getPack().getIdMap(), source.getDirectives(), source.getParent().getPackDirectives(), availability, shadow, id);
    }

    private Pass createPassInner(ProgramBuilder builder, IdMap map, ProgramDirectives programDirectives,
                                 PackDirectives packDirectives, InputAvailability availability, boolean shadow, ProgramId id) {

        CommonUniforms.addCommonUniforms(builder, map, packDirectives, updateNotifier);
        customUniforms.assignTo(builder);

        Supplier<ImmutableSet<Integer>> flipped;

        if (shadow) {
            flipped = () -> (shouldRenderPrepareBeforeShadow ? flippedAfterPrepare : flippedBeforeShadow);
        } else {
            flipped = () -> isBeforeTranslucent ? flippedAfterPrepare : flippedAfterTranslucent;
        }

        TextureStage textureStage = TextureStage.GBUFFERS_AND_SHADOW;

        ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor =
            ProgramSamplers.customTextureSamplerInterceptor(builder,
                customTextureManager.getCustomTextureIdMap(textureStage));

        LucernaSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, flipped, renderTargets, false);
        LucernaImages.addRenderTargetImages(builder, flipped, renderTargets);

        if (!shouldBindPBR) {
            shouldBindPBR = LucernaSamplers.hasPBRSamplers(customTextureSamplerInterceptor);
        }

        LucernaSamplers.addLevelSamplers(customTextureSamplerInterceptor, this, whitePixel, availability);

        if (!shadow) {
            LucernaSamplers.addWorldDepthSamplers(customTextureSamplerInterceptor, renderTargets);
        }

        LucernaSamplers.addNoiseSampler(customTextureSamplerInterceptor, customTextureManager.getNoiseTexture());

        if (LucernaSamplers.hasShadowSamplers(customTextureSamplerInterceptor)) {
            if (!shadow) {
                shadowTargetsSupplier.get();
            }

            if (shadowRenderTargets != null) {
                LucernaSamplers.addShadowSamplers(customTextureSamplerInterceptor, shadowRenderTargets);
                LucernaImages.addShadowColorImages(builder, shadowRenderTargets);
            }
        }

        GlFramebuffer framebufferBeforeTranslucents;
        GlFramebuffer framebufferAfterTranslucents;

        if (shadow) {
            framebufferBeforeTranslucents =
                shadowTargetsSupplier.get().createShadowFramebuffer(shadowRenderTargets.snapshot(), new int[] { 0, 1 });
            framebufferAfterTranslucents = framebufferBeforeTranslucents;
        } else {
            framebufferBeforeTranslucents =
                renderTargets.createGbufferFramebuffer(flippedAfterPrepare, programDirectives.getDrawBuffers());
            framebufferAfterTranslucents =
                renderTargets.createGbufferFramebuffer(flippedAfterTranslucent, programDirectives.getDrawBuffers());
        }

        builder.bindAttributeLocation(11, "mc_Entity");
        builder.bindAttributeLocation(12, "mc_midTexCoord");
        builder.bindAttributeLocation(13, "at_tangent");
        builder.bindAttributeLocation(14, "at_midBlock");

        AlphaTestOverride alphaTestOverride = programDirectives.getAlphaTestOverride().orElse(null);

        List<BufferBlendOverride> bufferOverrides = new ArrayList<>();

        programDirectives.getBufferBlendOverrides().forEach(information -> {
            int index = Ints.indexOf(programDirectives.getDrawBuffers(), information.getIndex());
            if (index > -1) {
                bufferOverrides.add(new BufferBlendOverride(index, information.getBlendMode()));
            }
        });

        return new Pass(builder.build(), framebufferBeforeTranslucents, framebufferAfterTranslucents, alphaTestOverride,
                programDirectives.getBlendModeOverride().orElse(id.getBlendModeOverride()), bufferOverrides, shadow);
    }

    private boolean isPostChain;
    private boolean isMainBound = true;

    @Override
    public void beginPostChain() {
        isPostChain = true;

        beginPass(null);
    }

    @Override
    public void endPostChain() {
        isPostChain = false;
    }

    private static boolean usesAttribute(@Nullable Program program, String name) {
        return program != null && GL20C.glGetAttribLocation(program.getProgramId(), name) != -1;
    }

    private final class Pass {
        @Nullable
        private final Program program;
        private final GlFramebuffer framebufferBeforeTranslucents;
        private final GlFramebuffer framebufferAfterTranslucents;
        @Nullable
        private final AlphaTestOverride alphaTestOverride;
        @Nullable
        private final BlendModeOverride blendModeOverride;
        @Nullable
        private final List<BufferBlendOverride> bufferBlendOverrides;
        private final boolean shadowViewport;
        private final boolean usesEntity;
        private final boolean usesMidTexCoord;
        private final boolean usesTangent;
        private final boolean usesMidBlock;

        private Pass(@Nullable Program program, GlFramebuffer framebufferBeforeTranslucents, GlFramebuffer framebufferAfterTranslucents,
                     @Nullable AlphaTestOverride alphaTestOverride, @Nullable BlendModeOverride blendModeOverride, @Nullable List<BufferBlendOverride> bufferBlendOverrides, boolean shadowViewport) {
            this.program = program;
            this.framebufferBeforeTranslucents = framebufferBeforeTranslucents;
            this.framebufferAfterTranslucents = framebufferAfterTranslucents;
            this.alphaTestOverride = alphaTestOverride;
            this.blendModeOverride = blendModeOverride;
            this.bufferBlendOverrides = bufferBlendOverrides;
            this.shadowViewport = shadowViewport;
            this.usesEntity = usesAttribute(program, "mc_Entity");
            this.usesMidTexCoord = usesAttribute(program, "mc_midTexCoord");
            this.usesTangent = usesAttribute(program, "at_tangent");
            this.usesMidBlock = usesAttribute(program, "at_midBlock");
        }

        private boolean usesExtendedVertexAttributes() {
            return usesMidTexCoord || usesTangent;
        }

        public void use() {
            if (isBeforeTranslucent) {
                framebufferBeforeTranslucents.bind();
            } else {
                framebufferAfterTranslucents.bind();
            }

            if (shadowViewport) {
                GlStateManager.viewport(0, 0, shadowMapResolution, shadowMapResolution);
            } else {
                GlStateManager.viewport(0, 0, renderTargets.getCurrentWidth(), renderTargets.getCurrentHeight());
            }

            if (program != null && !celeritasTerrainRendering) {
                program.use();

                // vanilla geometry has no extended attributes, so give the pack the values OptiFine uses for it
                if (usesEntity) {
                    LucernaRenderSystem.vertexAttrib4f(11, -1.0F, -1.0F, 0.0F, 1.0F);
                }

                if (usesMidTexCoord) {
                    LucernaRenderSystem.vertexAttrib4f(12, 0.0F, 0.0F, 0.0F, 1.0F);
                }

                if (usesTangent) {
                    LucernaRenderSystem.vertexAttrib4f(13, 1.0F, 0.0F, 0.0F, 1.0F);
                }

                if (usesMidBlock) {
                    LucernaRenderSystem.vertexAttrib4f(14, 0.0F, 0.0F, 0.0F, 0.0F);
                }
            }

            if (alphaTestOverride != null) {
                alphaTestOverride.apply();
            } else {
                // a previous program on the stack may have applied an override
                AlphaTestOverride.restore();
            }

            if (blendModeOverride != null) {
                blendModeOverride.apply();
            } else {
                // a previous program on the stack may have applied an override
                BlendModeOverride.restore();
            }

            if (bufferBlendOverrides != null && !bufferBlendOverrides.isEmpty()) {
                bufferBlendOverrides.forEach(BufferBlendOverride::apply);
            }
        }

        public void stopUsing() {
            if (alphaTestOverride != null) {
                AlphaTestOverride.restore();
            }

            if (blendModeOverride != null || (bufferBlendOverrides != null && !bufferBlendOverrides.isEmpty())) {
                BlendModeOverride.restore();
            }
        }

        @Nullable
        public Program getProgram() {
            return program;
        }

        public void destroy() {
            if (this.program != null) {
                this.program.destroy();
            }
        }
    }

    @Override
    public void destroy() {
        BlendModeOverride.restore();
        AlphaTestOverride.restore();

        destroyPasses(table);

        setupRenderer.destroy();
        beginRenderer.destroy();
        prepareRenderer.destroy();
        compositeRenderer.destroy();
        deferredRenderer.destroy();
        finalPassRenderer.destroy();
        centerDepthSampler.destroy();

        for (ComputeProgram computeProgram : shadowComputes) {
            if (computeProgram != null) {
                computeProgram.destroy();
            }
        }

        horizonRenderer.destroy();

        // unbind custom framebuffers before destroying render targets
        LucernaRenderSystem.bindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, 0);
        LucernaRenderSystem.bindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, 0);
        LucernaRenderSystem.bindFramebuffer(GL30C.GL_FRAMEBUFFER, 0);

        Minecraft.getInstance().getRenderTarget().bindWrite(false);

        renderTargets.destroy();
        mainDepth.destroy();

        if (shadowRenderTargets != null) {
            shadowRenderTargets.destroy();
        }

        customTextureManager.destroy();
        whitePixel.clearGlId();
        defaultNormal.clearGlId();
        defaultSpecular.clearGlId();
    }

    private static void destroyPasses(ProgramTable<Pass> table) {
        Set<Pass> destroyed = new HashSet<>();

        table.forEach(pass -> {
            if (pass == null) {
                return;
            }

            if (destroyed.contains(pass)) {
                return;
            }

            pass.destroy();
            destroyed.add(pass);
        });
    }

    private final Vector4f clearFogColor = new Vector4f();
    private int mainDepthWidth = -1;
    private int mainDepthHeight = -1;

    private void prepareRenderTargets() {
        GlStateManager.activeTexture(GL15C.GL_TEXTURE0);

        if (shadowRenderTargets != null) {
            if (packDirectives.getShadowDirectives().isShadowEnabled() == OptionalBoolean.FALSE) {
                if (shadowRenderTargets.isFullClearRequired()) {
                    shadowRenderTargets.onFullClear();
                    for (ClearPass clearPass : shadowClearPassesFull) {
                        clearPass.execute(EMPTY_CLEAR_COLOR);
                    }
                }
            } else {
                // clear depth first, regardless of color clearing
                shadowRenderTargets.getDepthSourceFb().bind();
                GlStateManager.clear(GL21C.GL_DEPTH_BUFFER_BIT);

                ImmutableList<ClearPass> passes;

                for (ComputeProgram computeProgram : shadowComputes) {
                    if (computeProgram != null) {
                        computeProgram.dispatch(shadowMapResolution, shadowMapResolution);
                    }
                }

                if (shadowRenderTargets.isFullClearRequired()) {
                    passes = shadowClearPassesFull;
                    shadowRenderTargets.onFullClear();
                } else {
                    passes = shadowClearPasses;
                }

                for (ClearPass clearPass : passes) {
                    clearPass.execute(EMPTY_CLEAR_COLOR);
                }
            }
        }

        RenderTarget main = Minecraft.getInstance().getRenderTarget();
        int width = LucernaVideoSettings.scaleRenderSize(main.width);
        int height = LucernaVideoSettings.scaleRenderSize(main.height);

        if (width != mainDepthWidth || height != mainDepthHeight) {
            mainDepthWidth = width;
            mainDepthHeight = height;
            mainDepth.resize(width, height, DepthBufferFormat.DEPTH24);
        }

        boolean changed = renderTargets.resizeIfNeeded(0, mainDepth.getTextureId(), width, height, DepthBufferFormat.DEPTH24, packDirectives);
        renderTargets.prepareCustomResources();

        if (!setupDone) {
            setupDone = true;
            setupRenderer.renderAll();
        }

        beginRenderer.renderAll();

        if (changed) {
            prepareRenderer.recalculateSizes();
            deferredRenderer.recalculateSizes();
            compositeRenderer.recalculateSizes();
            finalPassRenderer.recalculateSwapPassSize();

            this.clearPassesFull.forEach(clearPass -> renderTargets.destroyFramebuffer(clearPass.getFramebuffer()));
            this.clearPasses.forEach(clearPass -> renderTargets.destroyFramebuffer(clearPass.getFramebuffer()));

            this.clearPassesFull = ClearPassCreator.createClearPasses(renderTargets, true,
                packDirectives.getRenderTargetDirectives());
            this.clearPasses = ClearPassCreator.createClearPasses(renderTargets, false,
                packDirectives.getRenderTargetDirectives());
        }

        final ImmutableList<ClearPass> passes;

        if (renderTargets.isFullClearRequired()) {
            renderTargets.onFullClear();
            passes = clearPassesFull;
        } else {
            passes = clearPasses;
        }

        Vector3d fogColor3 = CapturedRenderingState.INSTANCE.getFogColor();

        // alpha must be 1.0, sildur's vibrant shaders get pink reflections otherwise
        clearFogColor.set((float) fogColor3.x, (float) fogColor3.y, (float) fogColor3.z, 1.0F);

        for (ClearPass clearPass : passes) {
            clearPass.execute(clearFogColor);
        }

        // vanilla only clears the depth of its own framebuffer, the gbuffers have a depth texture of their own
        renderTargets.getDepthSourceFb().bind();
        GlStateManager.depthMask(true);
        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);

        Minecraft.getInstance().getRenderTarget().bindWrite(true);
    }

    private ComputeProgram[] createShadowComputes(ComputeSource[] compute, ProgramSet programSet) {
        ComputeProgram[] programs = new ComputeProgram[compute.length];
        for (int i = 0; i < programs.length; i++) {
            ComputeSource source = compute[i];
            if (source == null || !source.getSource().isPresent()) {
                continue;
            } else {
                ProgramBuilder builder;

                try {
                    builder = ProgramBuilder.beginCompute(source.getName(), TransformPatcher.patchCompute(source.getSource().get(), programSet.getPack().getCustomTexturePatching(TextureStage.SHADOWCOMP)), LucernaSamplers.WORLD_RESERVED_TEXTURE_UNITS);
                } catch (RuntimeException e) {
                    throw new RuntimeException("Shader compilation failed!", e);
                }

                CommonUniforms.addCommonUniforms(builder, programSet.getPack().getIdMap(), programSet.getPackDirectives(), updateNotifier);
                customUniforms.assignTo(builder);

                Supplier<ImmutableSet<Integer>> flipped;

                flipped = () -> flippedBeforeShadow;

                TextureStage textureStage = TextureStage.GBUFFERS_AND_SHADOW;

                ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor =
                    ProgramSamplers.customTextureSamplerInterceptor(builder,
                        customTextureManager.getCustomTextureIdMap(textureStage));

                LucernaSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, flipped, renderTargets, false);
                LucernaImages.addRenderTargetImages(builder, flipped, renderTargets);

                LucernaSamplers.addLevelSamplers(customTextureSamplerInterceptor, this, whitePixel, new InputAvailability(true, true, false));

                LucernaSamplers.addNoiseSampler(customTextureSamplerInterceptor, customTextureManager.getNoiseTexture());

                if (LucernaSamplers.hasShadowSamplers(customTextureSamplerInterceptor)) {
                    if (shadowRenderTargets != null) {
                        LucernaSamplers.addShadowSamplers(customTextureSamplerInterceptor, shadowRenderTargets);
                        LucernaImages.addShadowColorImages(builder, shadowRenderTargets);
                    }
                }

                programs[i] = builder.buildCompute();

                programs[i].setWorkGroupInfo(source.getWorkGroupRelative(), source.getWorkGroups());
            }
        }

        return programs;
    }

    @Override
    public void beginHand() {
        // copy depth now so depthtex2 holds the non-translucent depth without the hand
        renderTargets.copyPreHandDepth();
    }

    @Override
    public void beginTranslucents() {
        isBeforeTranslucent = false;

        // copy depth now so depthtex1 holds the non-translucent depth
        renderTargets.copyPreTranslucentDepth();

        // removes blend mode overrides and similar
        beginPass(null);

        isRenderingFullScreenPass = true;

        deferredRenderer.renderAll();

        GlStateManager.enableBlend();
        GlStateManager.enableAlphaTest();

        // leave the lightmap and overlay units alone, enabling them here turned clouds and rain red

        isRenderingFullScreenPass = false;
    }

    @Override
    public void renderShadows(WorldRenderer worldRenderer, Entity playerCamera, Culler playerCuller) {
        if (shouldRenderPrepareBeforeShadow) {
            isRenderingFullScreenPass = true;

            prepareRenderer.renderAll();

            isRenderingFullScreenPass = false;
        }

        if (shadowRenderer != null) {
            isRenderingShadow = true;

            shadowRenderer.renderShadows(worldRenderer, playerCamera, playerCuller);

            // removes blend mode overrides and similar
            beginPass(null);
            isRenderingShadow = false;
        }

        if (!shouldRenderPrepareBeforeShadow) {
            isRenderingFullScreenPass = true;

            prepareRenderer.renderAll();

            isRenderingFullScreenPass = false;
        }
    }

    @Override
    public void addDebugText(List<String> messages) {
        messages.add("");

        if (shadowRenderer != null) {
            shadowRenderer.addDebugText(messages);
        } else {
            messages.add("[Lucerna] Shadow Maps: not used by shader pack");
        }
    }

    @Override
    public OptionalInt getForcedShadowRenderDistanceChunksForDisplay() {
        return forcedShadowRenderDistanceChunks;
    }

    private boolean isRenderingWorld = false;
    private boolean isRenderingFullScreenPass = false;

    @Override
    public void beginLevelRendering() {
        isRenderingFullScreenPass = false;
        isRenderingWorld = true;
        isBeforeTranslucent = true;
        isMainBound = true;
        isPostChain = false;
        phase = WorldRenderingPhase.NONE;
        overridePhase = null;

        checkWorld();

        if (!isRenderingWorld) {
            Lucerna.logger.warn("beginWorldRender was called but we are not currently rendering a world?");
            return;
        }

        if (current != null) {
            // the previous frame threw before finalizeLevelRendering, drop the stale pass
            Lucerna.logger.warn("Called beginLevelRendering but level rendering appears to still be in progress?");
            beginPass(null);
        }

        updateNotifier.onNewFrame();
        customUniforms.update();
        StateTracker.INSTANCE.syncWithGlState();
        inputs = StateTracker.INSTANCE.getInputs();
        LucernaRenderSystem.invalidateFramebufferBindings();

        prepareRenderTargets();

        setPhase(WorldRenderingPhase.SKY);

        // draw the horizon before the sky so mods that change sky rendering cannot break it
        if (Minecraft.getInstance().world.dimension.isNatural()) {
            GlStateManager.disableTexture();
            GlStateManager.depthMask(false);

            Vector3d fogColor = CapturedRenderingState.INSTANCE.getFogColor();
            GlStateManager.color3f((float) fogColor.x, (float) fogColor.y, (float) fogColor.z);

            syncProgram();
            horizonRenderer.renderHorizon(CapturedRenderingState.INSTANCE.getGbufferModelView());

            GlStateManager.depthMask(true);
            GlStateManager.enableTexture();
        }
    }

    @Override
    public void finalizeLevelRendering() {
        checkWorld();

        if (!isRenderingWorld) {
            Lucerna.logger.warn("finalizeWorldRendering was called but we are not currently rendering a world?");
            return;
        }

        beginPass(null);

        isRenderingWorld = false;
        phase = WorldRenderingPhase.NONE;
        overridePhase = null;

        isRenderingFullScreenPass = true;

        centerDepthSampler.sampleCenterDepth();

        compositeRenderer.renderAll();
        finalPassRenderer.renderFinalPass();

        // vanilla leaves the scene depth in the main target, mods and upscalers that run after the world read it there
        RenderTarget main = Minecraft.getInstance().getRenderTarget();
        LucernaRenderSystem.blitFramebuffer(renderTargets.getDepthSourceFb().getId(), main.frameBufferId, 0, 0, renderTargets.getCurrentWidth(), renderTargets.getCurrentHeight(),
            0, 0, main.width, main.height, GL11C.GL_DEPTH_BUFFER_BIT, GL11C.GL_NEAREST);

        isRenderingFullScreenPass = false;
    }

    @Override
    public CeleritasTerrainPipeline getCeleritasTerrainPipeline() {
        return celeritasTerrainPipeline;
    }

    @Override
    public FrameUpdateNotifier getFrameUpdateNotifier() {
        return updateNotifier;
    }

    @Override
    public WorldRenderingPhase getPhase() {
        if (overridePhase != null) {
            return overridePhase;
        }

        return phase;
    }

    boolean celeritasTerrainRendering = false;

    @Override
    public void syncProgram() {
        matchPass();
    }

    @Override
    public boolean usesExtendedVertexAttributes() {
        return current != null && current.usesExtendedVertexAttributes();
    }

    @Override
    public void beginTerrainRendering() {
        celeritasTerrainRendering = true;
        syncProgram();

    }

    @Override
    public void endTerrainRendering() {
        celeritasTerrainRendering = false;
        current = null;
        syncProgram();
    }

    @Override
    public void setOverridePhase(WorldRenderingPhase phase) {
        this.overridePhase = phase;

        GbufferPrograms.runPhaseChangeNotifier();
    }

    @Override
    public void setPhase(WorldRenderingPhase phase) {
        this.phase = phase;

        GbufferPrograms.runPhaseChangeNotifier();
    }

    @Override
    public void setInputs(InputAvailability availability) {
        this.inputs = availability;
    }

    @Override
    public void setSpecialCondition(SpecialCondition special) {
        this.special = special;
    }

    @Override
    public RenderTargetStateListener getRenderTargetStateListener() {
        return this;
    }

    @Override
    public int getCurrentNormalTexture() {
        return currentNormalTexture;
    }

    @Override
    public int getCurrentSpecularTexture() {
        return currentSpecularTexture;
    }

    @Override
    public void onBindTexture(int id) {
        PBRTextures textures = PBRTextureManager.INSTANCE.getTextures(id);
        int normal = textures.normal() != 0 ? textures.normal() : defaultNormal.getGlId();
        int specular = textures.specular() != 0 ? textures.specular() : defaultSpecular.getGlId();

        if (normal != currentNormalTexture || specular != currentSpecularTexture) {
            currentNormalTexture = normal;
            currentSpecularTexture = specular;
            PBRTextureManager.notifyTexturesChanged();
        }
    }
}
