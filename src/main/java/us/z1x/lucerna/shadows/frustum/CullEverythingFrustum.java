package us.z1x.lucerna.shadows.frustum;

import net.minecraft.client.render.Culler;
import net.minecraft.util.math.Box;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.render.viewport.ViewportProvider;
import org.embeddedt.embeddium.impl.render.viewport.frustum.Frustum;
import org.joml.Vector3d;

public class CullEverythingFrustum implements Culler, ViewportProvider, Frustum {
    public CullEverythingFrustum() {
    }

    @Override
    public void prepare(double cameraX, double cameraY, double cameraZ) {
    }

    @Override
    public boolean isVisible(Box box) {
        return false;
    }

    private static final Vector3d EMPTY = new Vector3d();

    @Override
    public Viewport sodium$createViewport() {
        return new Viewport(this, EMPTY);
    }

    @Override
    public boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        return false;
    }

    @Override
    public int intersectAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        return OUTSIDE;
    }
}
