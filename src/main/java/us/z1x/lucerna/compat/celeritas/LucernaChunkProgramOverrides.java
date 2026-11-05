package us.z1x.lucerna.compat.celeritas;

import org.embeddedt.embeddium.impl.gl.GlObject;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.gl.shader.GlShader;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.jetbrains.annotations.Nullable;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.pipeline.CeleritasTerrainPipeline;
import us.z1x.lucerna.pipeline.WorldRenderingPipeline;
import us.z1x.lucerna.pipeline.transform.PatchShaderType;
import us.z1x.lucerna.shadows.ShadowRenderingState;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LucernaChunkProgramOverrides {
    private static final Map<PatchShaderType, ShaderType> SHADER_TYPES = Map.of(
        PatchShaderType.VERTEX, ShaderType.VERTEX,
        PatchShaderType.GEOMETRY, ShaderType.GEOM,
        PatchShaderType.FRAGMENT, ShaderType.FRAGMENT
    );

    private final EnumMap<LucernaTerrainPass, GlProgram<LucernaChunkShaderInterface>> programs = new EnumMap<>(LucernaTerrainPass.class);
    private boolean shadersCreated;
    private int versionCounterForCeleritasShaderReload = -1;

    @Nullable
    private GlProgram<LucernaChunkShaderInterface> createProgram(LucernaTerrainPass pass, CeleritasTerrainPipeline pipeline) {
        CeleritasTerrainPipeline.PassInfo info = pipeline.getPassInfo(pass);

        if (info.sources().get(PatchShaderType.VERTEX) == null || info.sources().get(PatchShaderType.FRAGMENT) == null) {
            return null;
        }

        List<GlShader> shaders = new ArrayList<>();

        try {
            info.sources().forEach((type, source) -> {
                if (source != null) {
                    ShaderType shaderType = SHADER_TYPES.get(type);
                    shaders.add(new GlShader(shaderType, "lucerna:celeritas-terrain-" + pass.toString().toLowerCase(Locale.ROOT) + "." + shaderType.fileExtension, source));
                }
            });

            GlProgram.Builder builder = GlProgram.builder("lucerna:chunk_shader_for_" + pass.getName());
            shaders.forEach(builder::attachShader);

            int i = 0;

            for (var attr : XHFPModelVertexType.VERTEX_FORMAT.getAttributes()) {
                builder.bindAttribute(attr.getName(), i++);
            }

            return builder.link(shader -> new LucernaChunkShaderInterface(((GlObject) shader).handle(), shader, pipeline, pass,
                info.blendModeOverride(), info.bufferOverrides(), info.alphaReference()));
        } finally {
            shaders.forEach(GlShader::delete);
        }
    }

    private void createShaders(CeleritasTerrainPipeline pipeline) {
        for (LucernaTerrainPass pass : LucernaTerrainPass.values()) {
            if (pass.isShadow() && !pipeline.hasShadowPass()) {
                continue;
            }

            programs.put(pass, createProgram(pass, pipeline));
        }

        shadersCreated = true;
    }

    @Nullable
    public GlProgram<LucernaChunkShaderInterface> getProgramOverride(TerrainRenderPass pass) {
        if (versionCounterForCeleritasShaderReload != Lucerna.getPipelineManager().getVersionCounterForCeleritasShaderReload()) {
            versionCounterForCeleritasShaderReload = Lucerna.getPipelineManager().getVersionCounterForCeleritasShaderReload();
            deleteShaders();
        }

        WorldRenderingPipeline worldRenderingPipeline = Lucerna.getPipelineManager().getPipelineNullable();
        CeleritasTerrainPipeline celeritasTerrainPipeline = worldRenderingPipeline != null ? worldRenderingPipeline.getCeleritasTerrainPipeline() : null;

        if (celeritasTerrainPipeline == null) {
            return null;
        }

        if (!shadersCreated) {
            createShaders(celeritasTerrainPipeline);
        }

        if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            return programs.get(pass.supportsFragmentDiscard() ? LucernaTerrainPass.SHADOW_CUTOUT : LucernaTerrainPass.SHADOW);
        } else if (pass.supportsFragmentDiscard()) {
            return programs.get(LucernaTerrainPass.GBUFFER_CUTOUT);
        } else if (pass.isReverseOrder()) {
            return programs.get(LucernaTerrainPass.GBUFFER_TRANSLUCENT);
        } else {
            return programs.get(LucernaTerrainPass.GBUFFER_SOLID);
        }
    }

    public void deleteShaders() {
        for (GlProgram<?> program : programs.values()) {
            if (program != null) {
                program.delete();
            }
        }

        programs.clear();
        shadersCreated = false;
    }
}
