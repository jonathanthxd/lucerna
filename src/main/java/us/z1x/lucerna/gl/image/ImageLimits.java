package us.z1x.lucerna.gl.image;

import us.z1x.lucerna.gl.LucernaRenderSystem;

public class ImageLimits {
    private final int maxImageUnits;
    private static ImageLimits instance;

    private ImageLimits() {
        this.maxImageUnits = LucernaRenderSystem.getMaxImageUnits();
    }

    public int getMaxImageUnits() {
        return maxImageUnits;
    }

    public static ImageLimits get() {
        if (instance == null) {
            instance = new ImageLimits();
        }

        return instance;
    }
}
