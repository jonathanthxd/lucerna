package us.z1x.lucerna.shadows;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import us.z1x.lucerna.uniforms.CameraUniforms;
import us.z1x.lucerna.uniforms.CelestialUniforms;

public class ShadowMatrices {
    private static final Vector3d CAMERA_POSITION = new Vector3d();
    private static final float NEAR = 0.05f;
    private static final float FAR = 256.0f;

    // these matrices are column-major

    public static float[] createOrthoMatrix(float halfPlaneLength) {
        return new float[] {
                1.0f / halfPlaneLength, 0f, 0f, 0f,
                0f, 1.0f / halfPlaneLength, 0f, 0f,
                0f, 0f, 2.0f / (NEAR - FAR), 0f,
                0f, 0f, -(FAR + NEAR) / (FAR - NEAR), 1f
        };
    }

    public static float[] createPerspectiveMatrix(float fov) {
        float yScale = (float) (1.0f / Math.tan(Math.toRadians(fov) * 0.5f));
        return new float[] {
                yScale, 0f, 0f, 0f,
                0f, yScale, 0f, 0f,
                0f, 0f, (FAR + NEAR) / (NEAR - FAR), -1.0F,
                0f, 0f, 2.0F * FAR * NEAR / (NEAR - FAR), 0f
        };
    }

    public static Matrix4f createShadowModelView(float sunPathRotation, float intervalSize) {
        Vector3d cameraPos = CameraUniforms.getUnshiftedCameraPosition(CAMERA_POSITION);

        return createModelViewMatrix(new Matrix4f(), getShadowAngle(), intervalSize, sunPathRotation, cameraPos.x, cameraPos.y, cameraPos.z);
    }

    public static float getShadowAngle() {
        float shadowAngle = CelestialUniforms.getSunAngle();

        if (!CelestialUniforms.isDay()) {
            shadowAngle -= 0.5F;
        }

        return shadowAngle;
    }

    public static void createBaselineModelViewMatrix(Matrix4f target, float shadowAngle, float sunPathRotation) {
        float skyAngle;

        if (shadowAngle < 0.25f) {
            skyAngle = shadowAngle + 0.75f;
        } else {
            skyAngle = shadowAngle - 0.25f;
        }

        target.identity()
            .translate(0.0f, 0.0f, -100.0f)
            .rotateX((float) Math.toRadians(90.0F))
            .rotateZ((float) Math.toRadians(skyAngle * -360.0f))
            .rotateX((float) Math.toRadians(sunPathRotation));
    }

    public static void snapModelViewToGrid(Matrix4f target, float shadowIntervalSize, double cameraX, double cameraY, double cameraZ) {
        if (Math.abs(shadowIntervalSize) == 0.0F) {
            // a zero interval means no snapping
            return;
        }

        // offsets are in (-interval, interval), not [0, interval), since % keeps the sign of negative inputs
        float offsetX = (float) cameraX % shadowIntervalSize;
        float offsetY = (float) cameraY % shadowIntervalSize;
        float offsetZ = (float) cameraZ % shadowIntervalSize;

        float halfIntervalSize = shadowIntervalSize / 2.0f;

        // because of that, values can lie in (-3 * interval / 2, interval / 2) after the shift
        offsetX -= halfIntervalSize;
        offsetY -= halfIntervalSize;
        offsetZ -= halfIntervalSize;

        target.translate(offsetX, offsetY, offsetZ);
    }

    public static Matrix4f createModelViewMatrix(Matrix4f target, float shadowAngle, float shadowIntervalSize,
                                                 float sunPathRotation, double cameraX, double cameraY, double cameraZ) {
        createBaselineModelViewMatrix(target, shadowAngle, sunPathRotation);
        snapModelViewToGrid(target, shadowIntervalSize, cameraX, cameraY, cameraZ);

        return target;
    }
}
