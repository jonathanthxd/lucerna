package us.z1x.lucerna.texture.pbr;

import java.util.function.IntUnaryOperator;

final class PBRMipmaps {
    private static final IntUnaryOperator LINEAR = value -> 0;
    // labpbr keeps metal ids, porosity and emission apart, so only blend texels of the same kind
    private static final IntUnaryOperator F0_KIND = value -> value < 230 ? 0 : value - 229;
    private static final IntUnaryOperator POROSITY_KIND = value -> value < 65 ? 0 : 1;
    private static final IntUnaryOperator EMISSION_KIND = value -> value < 255 ? 0 : 1;

    private PBRMipmaps() {
    }

    static int[][] generate(int[] base, int width, int height, int mipLevels, boolean labSpecular) {
        int[][] levels = new int[mipLevels + 1][];
        levels[0] = base;

        for (int level = 1; level <= mipLevels; level++) {
            int previousWidth = width >> (level - 1);
            int levelWidth = width >> level;
            int levelHeight = height >> level;
            int[] previous = levels[level - 1];
            int[] pixels = new int[levelWidth * levelHeight];

            for (int y = 0; y < levelHeight; y++) {
                for (int x = 0; x < levelWidth; x++) {
                    int offset = y * 2 * previousWidth + x * 2;
                    int c0 = previous[offset];
                    int c1 = previous[offset + 1];
                    int c2 = previous[offset + previousWidth];
                    int c3 = previous[offset + previousWidth + 1];

                    pixels[y * levelWidth + x] = labSpecular
                        ? blend(c0, c1, c2, c3, 16, LINEAR) << 16
                            | blend(c0, c1, c2, c3, 8, F0_KIND) << 8
                            | blend(c0, c1, c2, c3, 0, POROSITY_KIND)
                            | blend(c0, c1, c2, c3, 24, EMISSION_KIND) << 24
                        : blend(c0, c1, c2, c3, 16, LINEAR) << 16
                            | blend(c0, c1, c2, c3, 8, LINEAR) << 8
                            | blend(c0, c1, c2, c3, 0, LINEAR)
                            | blend(c0, c1, c2, c3, 24, LINEAR) << 24;
                }
            }

            levels[level] = pixels;
        }

        return levels;
    }

    private static int blend(int c0, int c1, int c2, int c3, int shift, IntUnaryOperator kind) {
        int v0 = c0 >>> shift & 0xFF;
        int v1 = c1 >>> shift & 0xFF;
        int v2 = c2 >>> shift & 0xFF;
        int v3 = c3 >>> shift & 0xFF;
        int k0 = kind.applyAsInt(v0);
        int k1 = kind.applyAsInt(v1);
        int k2 = kind.applyAsInt(v2);
        int k3 = kind.applyAsInt(v3);
        int target = k0 == k1 || k0 == k2 || k0 == k3 ? k0 : (k1 == k2 || k1 == k3 ? k1 : (k2 == k3 ? k2 : k0));
        int sum = 0;
        int count = 0;

        if (k0 == target) {
            sum += v0;
            count++;
        }

        if (k1 == target) {
            sum += v1;
            count++;
        }

        if (k2 == target) {
            sum += v2;
            count++;
        }

        if (k3 == target) {
            sum += v3;
            count++;
        }

        return sum / count;
    }
}
