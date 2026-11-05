package us.z1x.lucerna.api.farterrain;

/** Borrowed VBO, GL_TRIANGLES. 13 floats/vertex: xyz, rgba, normal xyz, block/sky light, DH mini-ID.
 * Lights are normalized [0,1]; material IDs are the DH_BLOCK_* values. No buffer ownership transfer.
 * Translation is region origin minus interpolated camera position, in world axes. */
public record FarTerrainMesh(int vertexBuffer, int vertexCount, float x, float y, float z) {
    public static final int STRIDE = 52;
    public FarTerrainMesh {
        if (vertexBuffer <= 0 || vertexCount < 0 || vertexCount % 3 != 0
            || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
            throw new IllegalArgumentException("Invalid far terrain triangle mesh");
        }
    }
}
