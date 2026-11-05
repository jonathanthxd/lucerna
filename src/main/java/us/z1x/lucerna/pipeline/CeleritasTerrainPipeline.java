package us.z1x.lucerna.pipeline;

import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.compat.celeritas.LucernaTerrainPass;
import us.z1x.lucerna.gl.blending.AlphaTest;
import us.z1x.lucerna.gl.blending.AlphaTestOverride;
import us.z1x.lucerna.gl.blending.BlendModeOverride;
import us.z1x.lucerna.gl.blending.BufferBlendOverride;
import us.z1x.lucerna.gl.framebuffer.GlFramebuffer;
import us.z1x.lucerna.gl.program.ProgramImages;
import us.z1x.lucerna.gl.program.ProgramSamplers;
import us.z1x.lucerna.gl.program.ProgramUniforms;
import us.z1x.lucerna.pipeline.transform.PatchShaderType;
import us.z1x.lucerna.pipeline.transform.TransformPatcher;
import us.z1x.lucerna.rendertarget.RenderTargets;
import us.z1x.lucerna.shaderpack.ProgramSet;
import us.z1x.lucerna.shaderpack.ProgramSource;
import us.z1x.lucerna.shaderpack.loading.ProgramId;
import us.z1x.lucerna.shaderpack.texture.TextureStage;
import us.z1x.lucerna.uniforms.CommonUniforms;
import us.z1x.lucerna.uniforms.builtin.BuiltinReplacementUniforms;
import us.z1x.lucerna.uniforms.custom.CustomUniforms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.Supplier;

// patches pack terrain programs to read celeritas chunk vertices for argentum's terrain renderer
public class CeleritasTerrainPipeline {
    private static final float CUTOUT_ALPHA_REFERENCE = 0.1F;

    public static final class PassInfo {
        private final Map<PatchShaderType, String> sources = new EnumMap<>(PatchShaderType.class);
        private GlFramebuffer framebuffer;
        private BlendModeOverride blendModeOverride;
        private List<BufferBlendOverride> bufferOverrides = Collections.emptyList();
        private float alphaReference;

        public Map<PatchShaderType, String> sources() {
            return sources;
        }

        public GlFramebuffer framebuffer() {
            return framebuffer;
        }

        public BlendModeOverride blendModeOverride() {
            return blendModeOverride;
        }

        public List<BufferBlendOverride> bufferOverrides() {
            return bufferOverrides;
        }

        public float alphaReference() {
            return alphaReference;
        }
    }

    private final WorldRenderingPipeline parent;
    private final CustomUniforms customUniforms;
    private final ProgramSet programSet;
    private final Map<LucernaTerrainPass, PassInfo> passInfoMap = new EnumMap<>(LucernaTerrainPass.class);
    private final Map<LucernaTerrainPass, Optional<ProgramSource>> programSources = new EnumMap<>(LucernaTerrainPass.class);
    private final PassInfo entityPassInfo = new PassInfo();
    private final PassInfo shadowEntityPassInfo = new PassInfo();

    private final IntFunction<ProgramSamplers> createTerrainSamplers;
    private final IntFunction<ProgramSamplers> createShadowSamplers;

    private final IntFunction<ProgramImages> createTerrainImages;
    private final IntFunction<ProgramImages> createShadowImages;

    public CeleritasTerrainPipeline(WorldRenderingPipeline parent,
                                    ProgramSet programSet, IntFunction<ProgramSamplers> createTerrainSamplers,
                                    IntFunction<ProgramSamplers> createShadowSamplers,
                                    IntFunction<ProgramImages> createTerrainImages,
                                    IntFunction<ProgramImages> createShadowImages,
                                    RenderTargets targets, ImmutableSet<Integer> flippedAfterPrepare,
                                    ImmutableSet<Integer> flippedAfterTranslucent, Supplier<GlFramebuffer> shadowFramebuffer,
                                    CustomUniforms customUniforms) {
        this.parent = Objects.requireNonNull(parent);
        this.customUniforms = customUniforms;
        this.programSet = programSet;

        Optional<ProgramSource> terrainSource = terrainSource(programSet);

        programSources.put(LucernaTerrainPass.GBUFFER_SOLID, terrainSource);
        programSources.put(LucernaTerrainPass.GBUFFER_CUTOUT, terrainSource);
        programSources.put(LucernaTerrainPass.GBUFFER_TRANSLUCENT, first(programSet.getGbuffersWater(), terrainSource));
        programSources.put(LucernaTerrainPass.SHADOW, programSet.getShadow());
        programSources.put(LucernaTerrainPass.SHADOW_CUTOUT, programSet.getShadow());

        for (LucernaTerrainPass pass : LucernaTerrainPass.values()) {
            PassInfo info = new PassInfo();
            passInfoMap.put(pass, info);

            if (pass.isShadow()) {
                if (createShadowSamplers != null) {
                    info.framebuffer = shadowFramebuffer.get();
                }
            } else {
                ImmutableSet<Integer> flipped = pass == LucernaTerrainPass.GBUFFER_TRANSLUCENT ? flippedAfterTranslucent : flippedAfterPrepare;
                int[] drawBuffers = programSources.get(pass).map(source -> source.getDirectives().getDrawBuffers()).orElse(new int[] {0});
                info.framebuffer = targets.createGbufferFramebuffer(flipped, drawBuffers);
            }
        }

        Optional<ProgramSource> entitySource = entitySource(programSet);
        int[] entityDrawBuffers = entitySource.map(source -> source.getDirectives().getDrawBuffers()).orElse(new int[] {0});
        entityPassInfo.framebuffer = targets.createGbufferFramebuffer(flippedAfterPrepare, entityDrawBuffers);
        Map<String, String> textureRenames = programSet.getPack().getCustomTexturePatching(TextureStage.GBUFFERS_AND_SHADOW);
        patchEntityShaders(entityPassInfo, entitySource, ProgramId.Entities, textureRenames);

        if (createShadowSamplers != null) {
            shadowEntityPassInfo.framebuffer = shadowFramebuffer.get();
            patchEntityShaders(shadowEntityPassInfo, programSet.getShadow(), ProgramId.Shadow, textureRenames);
        }

        this.createTerrainSamplers = createTerrainSamplers;
        this.createShadowSamplers = createShadowSamplers;
        this.createTerrainImages = createTerrainImages;
        this.createShadowImages = createShadowImages;

        patchShaders();
    }

    // fills the patch cache from a worker thread, so the pipeline finds these sources ready
    public static void patchAhead(ProgramSet programSet) {
        Map<String, String> textureRenames = programSet.getPack().getCustomTexturePatching(TextureStage.GBUFFERS_AND_SHADOW);
        Optional<ProgramSource> terrain = terrainSource(programSet);

        for (Optional<ProgramSource> source : List.of(terrain, first(programSet.getGbuffersWater(), terrain), programSet.getShadow())) {
            source.ifPresent(sources -> TransformPatcher.patchCeleritasTerrain(sources.getVertexSource().orElse(null), sources.getGeometrySource().orElse(null), sources.getFragmentSource().orElse(null), textureRenames));
        }

        for (Optional<ProgramSource> source : List.of(entitySource(programSet), programSet.getShadow())) {
            source.filter(sources -> !sources.getGeometrySource().isPresent()).ifPresent(sources -> TransformPatcher.patchInstancedEntity(sources.getVertexSource().orElse(null), sources.getFragmentSource().orElse(null), textureRenames));
        }
    }

    private static Optional<ProgramSource> terrainSource(ProgramSet programSet) {
        return first(programSet.getGbuffersTerrain(), programSet.getGbuffersTexturedLit(), programSet.getGbuffersTextured(), programSet.getGbuffersBasic());
    }

    private static Optional<ProgramSource> entitySource(ProgramSet programSet) {
        return first(programSet.getGbuffersEntities(), programSet.getGbuffersTexturedLit(), programSet.getGbuffersTextured(), programSet.getGbuffersBasic());
    }

    private void patchShaders() {
        for (LucernaTerrainPass pass : LucernaTerrainPass.values()) {
            PassInfo info = passInfoMap.get(pass);
            ProgramId programId = switch (pass) {
                case GBUFFER_TRANSLUCENT -> ProgramId.Water;
                case SHADOW, SHADOW_CUTOUT -> ProgramId.Shadow;
                default -> ProgramId.Terrain;
            };
            boolean cutout = pass == LucernaTerrainPass.GBUFFER_CUTOUT || pass == LucernaTerrainPass.SHADOW_CUTOUT;

            programSources.get(pass).ifPresent(sources -> {
                info.blendModeOverride = sources.getDirectives().getBlendModeOverride().orElse(programId.getBlendModeOverride());
                info.bufferOverrides = new ArrayList<>();
                sources.getDirectives().getBufferBlendOverrides().forEach(information -> {
                    int index = Ints.indexOf(sources.getDirectives().getDrawBuffers(), information.getIndex());
                    if (index > -1) {
                        info.bufferOverrides.add(new BufferBlendOverride(index, information.getBlendMode()));
                    }
                });

                AlphaTest alphaTest = sources.getDirectives().getAlphaTestOverride().map(AlphaTestOverride::getAlphaTest).orElse(null);
                info.alphaReference = cutout ? (alphaTest != null ? alphaTest.getReference() : CUTOUT_ALPHA_REFERENCE) : 0.0F;

                Map<PatchShaderType, String> result = TransformPatcher.patchCeleritasTerrain(
                    sources.getVertexSource().orElse(null),
                    sources.getGeometrySource().orElse(null),
                    sources.getFragmentSource().orElse(null),
                    programSet.getPack().getCustomTexturePatching(TextureStage.GBUFFERS_AND_SHADOW));

                info.sources.putAll(result);

                PatchedShaderPrinter.debugPatchedShaders(sources.getName() + "_celeritas_" + pass.name().toLowerCase(Locale.ROOT),
                    result.get(PatchShaderType.VERTEX), result.get(PatchShaderType.GEOMETRY), result.get(PatchShaderType.FRAGMENT));
            });
        }
    }

    private static void patchEntityShaders(PassInfo info, Optional<ProgramSource> source, ProgramId programId, Map<String, String> textureRenames) {
        source.filter(sources -> !sources.getGeometrySource().isPresent()).ifPresent(sources -> {
            info.blendModeOverride = sources.getDirectives().getBlendModeOverride().orElse(programId.getBlendModeOverride());
            info.bufferOverrides = new ArrayList<>();
            sources.getDirectives().getBufferBlendOverrides().forEach(information -> {
                int index = Ints.indexOf(sources.getDirectives().getDrawBuffers(), information.getIndex());
                if (index > -1) {
                    info.bufferOverrides.add(new BufferBlendOverride(index, information.getBlendMode()));
                }
            });

            try {
                Map<PatchShaderType, String> result = TransformPatcher.patchInstancedEntity(sources.getVertexSource().orElse(null), sources.getFragmentSource().orElse(null), textureRenames);
                info.sources.putAll(result);
                PatchedShaderPrinter.debugPatchedShaders(sources.getName() + "_instanced", result.get(PatchShaderType.VERTEX), null, result.get(PatchShaderType.FRAGMENT));
            } catch (RuntimeException e) {
                // argentum draws these entities with display lists instead
                Lucerna.logger.warn("Failed to patch {} for instanced entities", sources.getName(), e);
            }
        });
    }

    public PassInfo getEntityPassInfo(boolean shadow) {
        return shadow ? shadowEntityPassInfo : entityPassInfo;
    }

    public ProgramUniforms initUniforms(int programId) {
        ProgramUniforms.Builder uniforms = ProgramUniforms.builder("<celeritas terrain>", programId);

        CommonUniforms.addCommonUniforms(uniforms, programSet.getPack().getIdMap(), programSet.getPackDirectives(), parent.getFrameUpdateNotifier());
        customUniforms.assignTo(uniforms);
        BuiltinReplacementUniforms.addBuiltinReplacementUniforms(uniforms);

        return uniforms.buildUniforms();
    }

    public boolean hasShadowPass() {
        return createShadowSamplers != null;
    }

    public PassInfo getPassInfo(LucernaTerrainPass pass) {
        return passInfoMap.get(pass);
    }

    public ProgramSamplers initTerrainSamplers(int programId) {
        return createTerrainSamplers.apply(programId);
    }

    public ProgramSamplers initShadowSamplers(int programId) {
        return createShadowSamplers.apply(programId);
    }

    public ProgramImages initTerrainImages(int programId) {
        return createTerrainImages.apply(programId);
    }

    public ProgramImages initShadowImages(int programId) {
        return createShadowImages.apply(programId);
    }

    @SafeVarargs
    private static <T> Optional<T> first(Optional<T>... candidates) {
        for (Optional<T> candidate : candidates) {
            if (candidate.isPresent()) {
                return candidate;
            }
        }

        return Optional.empty();
    }
}
