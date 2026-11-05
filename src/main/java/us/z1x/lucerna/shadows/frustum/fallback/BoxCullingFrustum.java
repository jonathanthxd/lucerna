package us.z1x.lucerna.shadows.frustum.fallback;

import net.minecraft.client.render.Culler;
import net.minecraft.util.math.Box;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.render.viewport.ViewportProvider;
import org.embeddedt.embeddium.impl.render.viewport.frustum.Frustum;
import org.joml.Vector3d;
import us.z1x.lucerna.shadows.frustum.BoxCuller;

public class BoxCullingFrustum implements Culler, ViewportProvider, Frustum {
    private final BoxCuller boxCuller;
    private double x, y, z;

    public BoxCullingFrustum(BoxCuller boxCuller) {

        this.boxCuller = boxCuller;
    }

    public void prepare(double cameraX, double cameraY, double cameraZ) {
        this.x = cameraX;
        this.y = cameraY;
        this.z = cameraZ;
        boxCuller.setPosition(cameraX, cameraY, cameraZ);
    }

    public boolean isVisible(Box box) {
        return !boxCuller.isCulled(box);
    }

    private final Vector3d position = new Vector3d();

    @Override
    public Viewport sodium$createViewport() {
        return new Viewport(this, position.set(x, y, z));
    }

    @Override
    public boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        return !boxCuller.isCulledCeleritas(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public int intersectAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        if (boxCuller.isCulledCeleritas(minX, minY, minZ, maxX, maxY, maxZ)) {
            return OUTSIDE;
        }

        return boxCuller.isFullyInsideCeleritas(minX, minY, minZ, maxX, maxY, maxZ) ? FULLY_INSIDE : PARTIALLY_INSIDE;
    }
}
