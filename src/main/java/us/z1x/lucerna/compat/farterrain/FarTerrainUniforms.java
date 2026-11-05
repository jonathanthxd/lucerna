package us.z1x.lucerna.compat.farterrain;

import org.joml.Matrix4f;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.api.farterrain.FarTerrainApi;
import us.z1x.lucerna.gl.sampler.SamplerHolder;
import us.z1x.lucerna.gl.uniform.UniformHolder;
import us.z1x.lucerna.pipeline.DeferredWorldRenderingPipeline;
import static us.z1x.lucerna.gl.uniform.UniformUpdateFrequency.CUSTOM;

public final class FarTerrainUniforms {
    private static final Matrix4f IDENTITY = new Matrix4f();
    private FarTerrainUniforms() {}
    private static FarTerrainState state() {
        var pipeline = Lucerna.getPipelineManager().getPipelineNullable();
        return pipeline instanceof DeferredWorldRenderingPipeline deferred ? deferred.getFarTerrainState() : null;
    }
    public static void addUniforms(UniformHolder holder) {
        if (!FarTerrainApi.enabled()) return;
        // CUSTOM updates on every bind, including when TFL submits matrices after the sky in this frame.
        holder.uniformMatrix(CUSTOM, "dhProjection", () -> state() == null ? IDENTITY : state().projection)
            .uniformMatrix(CUSTOM, "dhProjectionInverse", () -> state() == null ? IDENTITY : state().inverse)
            .uniformMatrix(CUSTOM, "dhPreviousProjection", () -> state() == null ? IDENTITY : state().previous)
            .uniformMatrix(CUSTOM, "dhPreviousProjectionInverse", () -> state() == null ? IDENTITY : state().previousInverse)
            .uniform1f(CUSTOM, "dhNearPlane", () -> state() == null ? 0.05f : state().near)
            .uniform1f(CUSTOM, "dhFarPlane", () -> state() == null ? 1f : state().far)
            .uniform1i(CUSTOM, "dhRenderDistance", () -> state() == null ? 0 : state().distance);
    }
    public static void addSamplers(SamplerHolder holder) {
        if (!FarTerrainApi.enabled()) return;
        holder.addDynamicSampler(() -> state() == null ? 0 : state().depth(), "dhDepthTex0");
        holder.addDynamicSampler(() -> state() == null ? 0 : state().opaqueDepth(), "dhDepthTex1");
    }
}
