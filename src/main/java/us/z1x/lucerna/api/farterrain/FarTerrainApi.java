package us.z1x.lucerna.api.farterrain;

import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.pipeline.DeferredWorldRenderingPipeline;
import java.util.List;

/** Experimental Lucerna-owned API v1. Call only on Minecraft's render thread. */
public final class FarTerrainApi {
    public static final int VERSION = 1;
    public enum Phase { TERRAIN, WATER }
    public enum Result { RENDERED, UNAVAILABLE, WRONG_PHASE, FAILED }
    private FarTerrainApi() {}
    public static boolean enabled() { return Boolean.getBoolean("lucerna.experimentalFarTerrain"); }
    private static DeferredWorldRenderingPipeline pipeline() {
        var value = Lucerna.getPipelineManager().getPipelineNullable();
        return value instanceof DeferredWorldRenderingPipeline deferred ? deferred : null;
    }
    public static boolean isPipelineActive() { return pipeline() != null; }
    /** Means a complete source pair exists; does NOT certify GLSL or vertex compatibility. */
    public static boolean hasProgram(Phase phase) {
        var pipeline = pipeline();
        return enabled() && pipeline != null && pipeline.hasFarTerrainProgram(phase);
    }
    public static Result render(Phase phase, FarTerrainFrame frame, List<FarTerrainMesh> meshes) {
        var pipeline = pipeline();
        return !enabled() || pipeline == null ? Result.UNAVAILABLE : pipeline.renderFarTerrain(phase, frame, List.copyOf(meshes));
    }
}
