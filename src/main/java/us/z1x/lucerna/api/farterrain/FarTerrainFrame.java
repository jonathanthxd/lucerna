package us.z1x.lucerna.api.farterrain;

/** Immutable CPU matrices. Positions are relative to the interpolated camera. */
public record FarTerrainFrame(float[] projection, float[] modelView, float nearPlane, float farPlane, int renderDistance) {
    public FarTerrainFrame {
        projection = matrix(projection);
        modelView = matrix(modelView);
        if (!(nearPlane > 0 && farPlane > nearPlane) || !Float.isFinite(farPlane) || renderDistance <= 0) {
            throw new IllegalArgumentException("Invalid far terrain clipping planes/render distance");
        }
    }
    private static float[] matrix(float[] value) {
        if (value == null || value.length != 16) throw new IllegalArgumentException("Expected a column-major 4x4 matrix");
        for (float entry : value) if (!Float.isFinite(entry)) throw new IllegalArgumentException("Non-finite matrix");
        return value.clone();
    }
    @Override public float[] projection() { return projection.clone(); }
    @Override public float[] modelView() { return modelView.clone(); }
}
