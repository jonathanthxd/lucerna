package us.z1x.lucerna.api.farterrain;

public final class FarTerrainContractRegression {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Check " + checks); }
    private static void invalid(Runnable action) {
        try { action.run(); throw new AssertionError("Accepted invalid geometry/frame"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    public static void main(String[] args) {
        float[] matrix = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        FarTerrainFrame frame = new FarTerrainFrame(matrix, matrix, 0.05f, 2048, 1024);
        matrix[0] = 10;
        check(frame.projection()[0] == 1 && frame.modelView()[0] == 1);
        frame.projection()[0] = 20;
        frame.modelView()[0] = 20;
        check(frame.projection()[0] == 1 && frame.modelView()[0] == 1);
        invalid(() -> new FarTerrainFrame(null, matrix, 1, 2, 1));
        invalid(() -> new FarTerrainFrame(new float[15], matrix, 1, 2, 1));
        for (float value : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            float[] broken = matrix.clone(); broken[5] = value;
            invalid(() -> new FarTerrainFrame(broken, matrix, 1, 2, 1));
            invalid(() -> new FarTerrainFrame(matrix, matrix, 1, value, 1));
            invalid(() -> new FarTerrainMesh(1, 3, value, 0, 0));
        }
        invalid(() -> new FarTerrainFrame(matrix, matrix, 0, 2, 1));
        invalid(() -> new FarTerrainFrame(matrix, matrix, 2, 1, 1));
        invalid(() -> new FarTerrainFrame(matrix, matrix, 1, 2, 0));
        invalid(() -> new FarTerrainMesh(0, 3, 0, 0, 0));
        invalid(() -> new FarTerrainMesh(1, -3, 0, 0, 0));
        invalid(() -> new FarTerrainMesh(1, 4, 0, 0, 0));
        check(new FarTerrainMesh(1, 0, -100, 32, 100).vertexCount() == 0);
        check(new FarTerrainMesh(1, 300, 0, 0, 0).vertexCount() == 300);
        check(FarTerrainMesh.STRIDE == 13 * Float.BYTES);
        System.out.println("Far terrain CPU contract checks passed: " + checks);
    }
}
