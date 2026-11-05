package us.z1x.lucerna.compat.argentum;

import org.embeddedt.embeddium.impl.gl.GlObject;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.gl.shader.GlShader;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL30C;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.gl.blending.BlendModeOverride;
import us.z1x.lucerna.gl.blending.BufferBlendOverride;
import us.z1x.lucerna.gl.program.ProgramImages;
import us.z1x.lucerna.gl.program.ProgramSamplers;
import us.z1x.lucerna.gl.program.ProgramUniforms;
import us.z1x.lucerna.pipeline.CeleritasTerrainPipeline;
import us.z1x.lucerna.pipeline.DeferredWorldRenderingPipeline;
import us.z1x.lucerna.pipeline.WorldRenderingPipeline;
import us.z1x.lucerna.pipeline.transform.PatchShaderType;

import java.util.Map;
import java.util.function.Supplier;

// argentum's instanced entity renderer, linked against shader pack programs instead of its own
public final class InstancedEntityPrograms {
    private static final Entry[] ENTRIES = new Entry[2];

    private static int version = -1;
    @Nullable
    private static Map<PatchShaderType, String> building;
    @Nullable
    private static Entry active;
    private static int glintPass = -1;
    private static int itemGlintPass = -1;
    private static int chargePass;
    private static boolean effectMasked;

    private InstancedEntityPrograms() {
    }

    private record Entry(GlProgram<?> program, CeleritasTerrainPipeline.PassInfo info, ProgramUniforms uniforms, ProgramSamplers samplers, ProgramImages images) {
    }

    @Nullable
    private static CeleritasTerrainPipeline getTerrainPipeline() {
        WorldRenderingPipeline pipeline = Lucerna.getPipelineManager().getPipelineNullable();
        return pipeline instanceof DeferredWorldRenderingPipeline ? pipeline.getCeleritasTerrainPipeline() : null;
    }

    public static boolean isActive() {
        return getTerrainPipeline() != null;
    }

    // returns null when argentum should keep its own program
    @Nullable
    public static GlProgram<?> get(boolean shadow, Supplier<GlProgram<?>> link) {
        CeleritasTerrainPipeline pipeline = getTerrainPipeline();

        if (version != Lucerna.getPipelineManager().getVersionCounterForCeleritasShaderReload()) {
            version = Lucerna.getPipelineManager().getVersionCounterForCeleritasShaderReload();
            delete();
        }

        if (pipeline == null) {
            return null;
        }

        int index = shadow ? 1 : 0;

        if (ENTRIES[index] == null) {
            CeleritasTerrainPipeline.PassInfo info = pipeline.getEntityPassInfo(shadow);

            if (info.sources().isEmpty()) {
                // no usable entity program in this pack, so entities fall back to display lists
                ArgentumCompat.disableEntityInstancing();
                return null;
            }

            building = info.sources();
            GlProgram<?> program;

            try {
                program = link.get();
            } finally {
                building = null;
            }

            int handle = ((GlObject) program).handle();
            ENTRIES[index] = new Entry(program, info, pipeline.initUniforms(handle),
                shadow ? pipeline.initShadowSamplers(handle) : pipeline.initTerrainSamplers(handle),
                shadow ? pipeline.initShadowImages(handle) : pipeline.initTerrainImages(handle));
        }

        return ENTRIES[index].program();
    }

    @Nullable
    public static GlShader createShader(ShaderType type) {
        if (building == null) {
            return null;
        }

        PatchShaderType patchType = type == ShaderType.VERTEX ? PatchShaderType.VERTEX : PatchShaderType.FRAGMENT;
        return new GlShader(type, "lucerna:instanced_entity." + type.fileExtension, building.get(patchType));
    }

    public static void prepare(boolean shadow) {
        Entry entry = ENTRIES[shadow ? 1 : 0];

        if (entry == null || getTerrainPipeline() == null) {
            return;
        }

        active = entry;
        entry.info().framebuffer().bind();

        if (entry.info().blendModeOverride() != null) {
            entry.info().blendModeOverride().apply();
        }

        entry.info().bufferOverrides().forEach(BufferBlendOverride::apply);
        entry.uniforms().update();
        entry.samplers().update();
        entry.images().update();
    }

    public static void restore() {
        if (active == null) {
            return;
        }

        active = null;
        updateEffectMask();
        ProgramUniforms.clearActiveUniforms();
        ProgramSamplers.clearActiveSamplers();
        BlendModeOverride.restore();

        // argentum unbinds the program, so the next vanilla draw has to set up its gbuffer pass again
        if (Lucerna.getPipelineManager().getPipelineNullable() instanceof DeferredWorldRenderingPipeline pipeline) {
            pipeline.beginPass(null);
        }
    }

    public static void setGlintPass(int pass) {
        glintPass = pass;
        updateEffectMask();
    }

    public static void setItemGlintPass(int pass) {
        itemGlintPass = pass;
        updateEffectMask();
    }

    public static void setChargePass(int pass) {
        chargePass = pass;
        updateEffectMask();
    }

    // glint and charged layers only blend into colortex0, see InstancedEntityTransformer
    private static void updateEffectMask() {
        boolean effect = active != null && (glintPass >= 0 || itemGlintPass >= 0 || chargePass != 0);

        if (effect != effectMasked) {
            effectMasked = effect;

            for (int buffer = 1; buffer < 8; buffer++) {
                GL30C.glColorMaski(buffer, !effect, !effect, !effect, !effect);
            }
        }
    }

    private static void delete() {
        for (int i = 0; i < ENTRIES.length; i++) {
            if (ENTRIES[i] != null) {
                ENTRIES[i].program().delete();
                ENTRIES[i] = null;
            }
        }
    }
}
