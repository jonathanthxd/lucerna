package us.z1x.lucerna.shadows.frustum.advanced;

import net.minecraft.client.render.Culler;
import net.minecraft.util.math.Box;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.render.viewport.ViewportProvider;
import org.embeddedt.embeddium.impl.render.viewport.frustum.Frustum;
import org.embeddedt.embeddium.impl.render.viewport.frustum.ShadowSearchFrustum;
import org.joml.Math;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import us.z1x.lucerna.shadows.frustum.BoxCuller;

// tight shadow frustum from the player frustum, only valid when the shadow map is used for direct shadows and not sun bounce gi
// based on l. spiro's tutorial at http://lspiroengine.com/?p=153 and http://lspiroengine.com/?p=187
public class AdvancedShadowCullingFrustum implements Culler, ViewportProvider, Frustum, ShadowSearchFrustum {
    private static final int MAX_CLIPPING_PLANES = 13;

    protected final BoxCuller boxCuller;
    // planes are stored as (a, b, c, -d), dot(plane, (x, y, z, 1)) > 0 means the point is inside
    private final Vector4f[] planes = new Vector4f[MAX_CLIPPING_PLANES];
    private final Vector3f shadowLightVectorFromOrigin;
    public double x;
    public double y;
    public double z;
    private int planeCount = 0;

    public AdvancedShadowCullingFrustum(Matrix4f playerView, Matrix4f playerProjection, Vector3f shadowLightVectorFromOrigin,
                                        BoxCuller boxCuller) {
        this.shadowLightVectorFromOrigin = shadowLightVectorFromOrigin;
        BaseClippingPlanes baseClippingPlanes = new BaseClippingPlanes(playerView, playerProjection);

        boolean[] isBack = addBackPlanes(baseClippingPlanes);
        addEdgePlanes(baseClippingPlanes, isBack);

        this.boxCuller = boxCuller;
    }

    private void addPlane(Vector4f plane) {
        planes[planeCount] = plane;
        planeCount += 1;
    }

    // adds the back planes of the player frustum as seen from the light, which removes many chunks when looking at the sun or moon
    private boolean[] addBackPlanes(BaseClippingPlanes baseClippingPlanes) {
        Vector4f[] planes = baseClippingPlanes.getPlanes();
        boolean[] isBack = new boolean[planes.length];

        for (int planeIndex = 0; planeIndex < planes.length; planeIndex++) {
            Vector4f plane = planes[planeIndex];
            Vector3f planeNormal = truncate(plane);

            // back planes have a normal within 90 degrees of the light vector, so the dot product is zero or more

            float dot = planeNormal.dot(shadowLightVectorFromOrigin);

            boolean back = dot > 0.0;
            boolean edge = dot == 0.0;

            isBack[planeIndex] = back;

            if (back || edge) {
                addPlane(plane);
            }
        }

        return isBack;
    }

    private void addEdgePlanes(BaseClippingPlanes baseClippingPlanes, boolean[] isBack) {
        Vector4f[] planes = baseClippingPlanes.getPlanes();

        for (int planeIndex = 0; planeIndex < planes.length; planeIndex++) {
            if (!isBack[planeIndex]) {
                continue;
            }

            Vector4f plane = planes[planeIndex];

            NeighboringPlaneSet neighbors = NeighboringPlaneSet.forPlane(planeIndex);

            if (!isBack[neighbors.plane0()]) {
                addEdgePlane(plane, planes[neighbors.plane0()]);
            }

            if (!isBack[neighbors.plane1()]) {
                addEdgePlane(plane, planes[neighbors.plane1()]);
            }

            if (!isBack[neighbors.plane2()]) {
                addEdgePlane(plane, planes[neighbors.plane2()]);
            }

            if (!isBack[neighbors.plane3()]) {
                addEdgePlane(plane, planes[neighbors.plane3()]);
            }
        }
    }

    private Vector3f truncate(Vector4f base) {
        return new Vector3f(base.x(), base.y(), base.z());
    }

    private Vector4f extend(Vector3f base, float w) {
        return new Vector4f(base.x(), base.y(), base.z(), w);
    }

    private float lengthSquared(Vector3f v) {
        float x = v.x();
        float y = v.y();
        float z = v.z();

        return x * x + y * y + z * z;
    }

    private Vector3f cross(Vector3f first, Vector3f second) {
        Vector3f result = new Vector3f(first.x(), first.y(), first.z());
        result.cross(second);

        return result;
    }

    private void addEdgePlane(Vector4f backPlane4, Vector4f frontPlane4) {
        Vector3f backPlaneNormal = truncate(backPlane4);
        Vector3f frontPlaneNormal = truncate(frontPlane4);

        // vector along the intersection of the two planes
        Vector3f intersection = cross(backPlaneNormal, frontPlaneNormal);

        // the edge plane normal must be perpendicular to the light vector
        Vector3f edgePlaneNormal = cross(intersection, shadowLightVectorFromOrigin);

        // solve d so the 3 planes meet in a line, using a point on the intersection line
        // a plane through the origin with its normal along the line meets both planes at that point
        Vector3f point;

        {
            // "Line of intersection between two planes"
            // https://stackoverflow.com/a/32410473 by ideasman42, CC BY-SA 3.0
            // (a modified version of "Intersection of 2-planes" from Graphics Gems 1, page 305

            // the intersection vector can be assumed to have a non-zero length
            Vector3f ixb = cross(intersection, backPlaneNormal);
            Vector3f fxi = cross(frontPlaneNormal, intersection);

            ixb.mul(-frontPlane4.w());
            fxi.mul(-backPlane4.w());

            ixb.add(fxi);

            point = ixb;
            point.mul(1.0F / lengthSquared(intersection));
        }

        Vector4f plane;

        {
            float d = edgePlaneNormal.dot(point);
            float w = -d;

            plane = extend(edgePlaneNormal, w);
        }

        addPlane(plane);
    }

    @Override
    public void prepare(double cameraX, double cameraY, double cameraZ) {
        if (this.boxCuller != null) {
            boxCuller.setPosition(cameraX, cameraY, cameraZ);
        }

        this.x = cameraX;
        this.y = cameraY;
        this.z = cameraZ;
    }

    @Override
    public boolean isVisible(Box aabb) {
        if (boxCuller != null && boxCuller.isCulled(aabb)) {
            return false;
        }

        return this.isVisible(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ) != 0;
    }

    protected int isVisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        float f = (float) (minX - this.x);
        float g = (float) (minY - this.y);
        float h = (float) (minZ - this.z);
        float i = (float) (maxX - this.x);
        float j = (float) (maxY - this.y);
        float k = (float) (maxZ - this.z);
        return this.checkCornerVisibility(f, g, h, i, j, k);
    }

    // 0 when nothing is visible, 1 when everything is, 2 when only some corners are
    protected int checkCornerVisibility(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        float outsideBoundX;
        float outsideBoundY;
        float outsideBoundZ;

        for (int i = 0; i < planeCount; ++i) {
            Vector4f plane = this.planes[i];

            // ported from joml's FrustumIntersection

            if (plane.x() < 0) {
                outsideBoundX = minX;
            } else {
                outsideBoundX = maxX;
            }

            if (plane.y() < 0) {
                outsideBoundY = minY;
            } else {
                outsideBoundY = maxY;
            }

            if (plane.z() < 0) {
                outsideBoundZ = minZ;
            } else {
                outsideBoundZ = maxZ;
            }

            if (Math.fma(plane.x(), outsideBoundX, Math.fma(plane.y(), outsideBoundY, plane.z() * outsideBoundZ)) < -plane.w()) {
                return 0;
            }
        }

        return 2;
    }

    @Override
    public boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        return (boxCuller == null || !boxCuller.isCulledCeleritas(minX, minY, minZ, maxX, maxY, maxZ)) && this.checkCornerVisibility(minX, minY, minZ, maxX, maxY, maxZ) > 0;
    }

    // tri-state checkCornerVisibility from joml's FrustumIntersection#intersectAab, also tests the near corner to report fully inside boxes
    protected int intersectCorners(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        boolean inside = true;

        for (int i = 0; i < planeCount; ++i) {
            Vector4f plane = this.planes[i];
            float px = plane.x();
            float py = plane.y();
            float pz = plane.z();
            float pw = plane.w();

            // the box is fully outside when the far corner along the normal is behind the plane
            if (Math.fma(px, px < 0 ? minX : maxX, Math.fma(py, py < 0 ? minY : maxY, pz * (pz < 0 ? minZ : maxZ))) < -pw) {
                return OUTSIDE;
            }

            // the box straddles the plane when the near corner is behind it
            if (Math.fma(px, px < 0 ? maxX : minX, Math.fma(py, py < 0 ? maxY : minY, pz * (pz < 0 ? maxZ : minZ))) < -pw) {
                inside = false;
            }
        }

        return inside ? FULLY_INSIDE : PARTIALLY_INSIDE;
    }

    @Override
    public int intersectAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        if (boxCuller != null && boxCuller.isCulledCeleritas(minX, minY, minZ, maxX, maxY, maxZ)) {
            return OUTSIDE;
        }

        int result = intersectCorners(minX, minY, minZ, maxX, maxY, maxZ);

        // a box is only fully inside if it is also inside the box culler bounds, otherwise a sub-box could be culled
        if (result == FULLY_INSIDE && boxCuller != null
                && !boxCuller.isFullyInsideCeleritas(minX, minY, minZ, maxX, maxY, maxZ)) {
            return PARTIALLY_INSIDE;
        }

        return result;
    }

    private final Vector3d position = new Vector3d();

    @Override
    public Viewport sodium$createViewport() {
        return new Viewport(this, position.set(x, y, z));
    }

    @Override
    public boolean supportsOcclusionSearch() {
        return true;
    }

    @Override
    public float shadowLightX() {
        return this.shadowLightVectorFromOrigin.x();
    }

    @Override
    public float shadowLightY() {
        return this.shadowLightVectorFromOrigin.y();
    }

    @Override
    public float shadowLightZ() {
        return this.shadowLightVectorFromOrigin.z();
    }
}
