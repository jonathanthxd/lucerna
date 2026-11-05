package us.z1x.lucerna.pipeline;

import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.gbufferoverrides.matching.InputAvailability;
import us.z1x.lucerna.gbufferoverrides.matching.RenderCondition;
import us.z1x.lucerna.pipeline.transform.PatchShaderType;
import us.z1x.lucerna.pipeline.transform.TransformPatcher;
import us.z1x.lucerna.shaderpack.ComputeSource;
import us.z1x.lucerna.shaderpack.ProgramFallbackResolver;
import us.z1x.lucerna.shaderpack.ProgramSet;
import us.z1x.lucerna.shaderpack.ProgramSource;
import us.z1x.lucerna.shaderpack.loading.ProgramId;
import us.z1x.lucerna.shaderpack.texture.TextureStage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

// the patched sources a pipeline will link, safe to run without a gl context
public final class PipelineSources {
    private PipelineSources() {
    }

    public static List<Map<PatchShaderType, String>> collect(ProgramSet programs) {
        List<Map<PatchShaderType, String>> sources = new ArrayList<>();
        ProgramFallbackResolver resolver = new ProgramFallbackResolver(programs);
        Map<String, String> gbufferRenames = programs.getPack().getCustomTexturePatching(TextureStage.GBUFFERS_AND_SHADOW);
        Set<List<Object>> seen = new HashSet<>();

        for (RenderCondition condition : RenderCondition.values()) {
            for (int packed = 0; packed < InputAvailability.NUM_VALUES; packed++) {
                InputAvailability availability = InputAvailability.unpack(packed);
                ProgramId id = DeferredWorldRenderingPipeline.programIdFor(condition, availability);

                if (seen.add(List.of(id, packed))) {
                    resolver.resolve(id).ifPresent(source -> add(sources, source, () -> TransformPatcher.patchAttributes(vertex(source), geometry(source), fragment(source), availability, gbufferRenames)));
                }
            }
        }

        addComposites(sources, programs.getShadowComposite(), programs, TextureStage.SHADOWCOMP);
        addComposites(sources, programs.getPrepare(), programs, TextureStage.PREPARE);
        addComposites(sources, programs.getDeferred(), programs, TextureStage.DEFERRED);
        addComposites(sources, programs.getComposite(), programs, TextureStage.COMPOSITE_AND_FINAL);
        programs.getCompositeFinal().ifPresent(source -> addComposite(sources, source, programs, TextureStage.COMPOSITE_AND_FINAL));
        patchAhead(programs);

        return sources;
    }

    // these link on first use or are compute programs, but parsing them still belongs on the worker
    private static void patchAhead(ProgramSet programs) {
        try {
            CeleritasTerrainPipeline.patchAhead(programs);
            patchComputes(programs.getShadowCompute(), programs.getPack().getCustomTexturePatching(TextureStage.SHADOWCOMP));
            patchComputes(programs.getShadowCompCompute(), programs.getPack().getCustomTexturePatching(TextureStage.SHADOWCOMP));
            patchComputes(programs.getSetupCompute(), Map.of());
            patchComputes(programs.getBeginCompute(), Map.of());
            patchComputes(programs.getPrepareCompute(), programs.getPack().getCustomTexturePatching(TextureStage.PREPARE));
            patchComputes(programs.getDeferredCompute(), programs.getPack().getCustomTexturePatching(TextureStage.DEFERRED));
            patchComputes(programs.getCompositeCompute(), programs.getPack().getCustomTexturePatching(TextureStage.COMPOSITE_AND_FINAL));
            patchComputes(programs.getFinalCompute(), programs.getPack().getCustomTexturePatching(TextureStage.COMPOSITE_AND_FINAL));
        } catch (RuntimeException e) {
            Lucerna.logger.warn("Failed to patch programs ahead of time", e);
        }
    }

    private static void patchComputes(ComputeSource[][] computes, Map<String, String> textureRenames) {
        for (ComputeSource[] sources : computes) {
            patchComputes(sources, textureRenames);
        }
    }

    private static void patchComputes(ComputeSource[] computes, Map<String, String> textureRenames) {
        for (ComputeSource source : computes) {
            if (source != null) {
                source.getSource().ifPresent(compute -> TransformPatcher.patchCompute(compute, textureRenames));
            }
        }
    }

    private static void addComposites(List<Map<PatchShaderType, String>> sources, ProgramSource[] composites, ProgramSet programs, TextureStage stage) {
        for (ProgramSource source : composites) {
            if (source != null && source.isValid()) {
                addComposite(sources, source, programs, stage);
            }
        }
    }

    private static void addComposite(List<Map<PatchShaderType, String>> sources, ProgramSource source, ProgramSet programs, TextureStage stage) {
        add(sources, source, () -> TransformPatcher.patchComposite(vertex(source), geometry(source), fragment(source), programs.getPack().getCustomTexturePatching(stage)));
    }

    private static void add(List<Map<PatchShaderType, String>> sources, ProgramSource source, Supplier<Map<PatchShaderType, String>> patch) {
        try {
            sources.add(patch.get());
        } catch (RuntimeException e) {
            Lucerna.logger.warn("Failed to patch {} ahead of time", source.getName(), e);
        }
    }

    private static String vertex(ProgramSource source) {
        return source.getVertexSource().orElseThrow(NullPointerException::new);
    }

    private static String geometry(ProgramSource source) {
        return source.getGeometrySource().orElse(null);
    }

    private static String fragment(ProgramSource source) {
        return source.getFragmentSource().orElseThrow(NullPointerException::new);
    }
}
