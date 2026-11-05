package us.z1x.lucerna.pipeline;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL20C;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.blockrendering.BlockRenderingSettings;
import us.z1x.lucerna.shaderpack.DimensionId;
import us.z1x.lucerna.uniforms.SystemTimeUniforms;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class PipelineManager {
    private final Function<DimensionId, WorldRenderingPipeline> pipelineFactory;
    private final Map<DimensionId, WorldRenderingPipeline> pipelinesPerDimension = new HashMap<>();
    private WorldRenderingPipeline pipeline = new FixedFunctionWorldRenderingPipeline();
    private int versionCounterForCeleritasShaderReload = 0;

    public PipelineManager(Function<DimensionId, WorldRenderingPipeline> pipelineFactory) {
        this.pipelineFactory = pipelineFactory;
    }

    public WorldRenderingPipeline preparePipeline(DimensionId currentDimension) {
        if (!pipelinesPerDimension.containsKey(currentDimension)) {
            SystemTimeUniforms.COUNTER.reset();
            SystemTimeUniforms.TIMER.reset();

            Lucerna.logger.info("Creating pipeline for dimension {}", currentDimension);
            pipeline = pipelineFactory.apply(currentDimension);
            pipelinesPerDimension.put(currentDimension, pipeline);

            if (BlockRenderingSettings.INSTANCE.isReloadRequired()) {
                if (Minecraft.getInstance().worldRenderer != null) {
                    Minecraft.getInstance().worldRenderer.reload();
                }

                BlockRenderingSettings.INSTANCE.clearReloadRequired();
            }
        } else {
            pipeline = pipelinesPerDimension.get(currentDimension);
        }

        return pipeline;
    }

    @Nullable
    public WorldRenderingPipeline getPipelineNullable() {
        return pipeline;
    }

    public Optional<WorldRenderingPipeline> getPipeline() {
        return Optional.ofNullable(pipeline);
    }

    // bumped on each destroy so cached chunk and entity programs know to reload
    public int getVersionCounterForCeleritasShaderReload() {
        return versionCounterForCeleritasShaderReload;
    }

    // re-prepare the pipeline right after this or the program state becomes inconsistent
    // see https://github.com/IrisShaders/Iris/issues/1330
    public void destroyPipeline() {
        pipelinesPerDimension.forEach((dimensionId, pipeline) -> {
            Lucerna.logger.info("Destroying pipeline {}", dimensionId);
            resetTextureState();
            pipeline.destroy();
        });

        pipelinesPerDimension.clear();
        pipeline = null;
        versionCounterForCeleritasShaderReload++;
    }

    private void resetTextureState() {
        // unbind all units so destroyed render target textures do not stay bound, which breaks some pack reloads
        for (int i = 0; i < 16; i++) {
            GlStateManager.activeTexture(GL20C.GL_TEXTURE0 + i);
            GlStateManager.bindTexture(0);
        }

        // most code expects unit 0 to be active
        GlStateManager.activeTexture(GL20C.GL_TEXTURE0);
    }
}
