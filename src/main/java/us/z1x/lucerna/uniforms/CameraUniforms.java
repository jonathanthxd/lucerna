package us.z1x.lucerna.uniforms;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;
import us.z1x.lucerna.gl.uniform.UniformHolder;

import static us.z1x.lucerna.gl.uniform.UniformUpdateFrequency.ONCE;
import static us.z1x.lucerna.gl.uniform.UniformUpdateFrequency.PER_FRAME;

public class CameraUniforms {
    private static final Minecraft client = Minecraft.getInstance();

    private CameraUniforms() {
    }

    public static void addCameraUniforms(UniformHolder uniforms, FrameUpdateNotifier notifier) {
        CameraPositionTracker tracker = new CameraPositionTracker(notifier);

        uniforms
            .uniform1f(ONCE, "near", () -> 0.05)
            .uniform1f(PER_FRAME, "far", CameraUniforms::getRenderDistanceInBlocks)
            .uniform3d(PER_FRAME, "cameraPosition", tracker::getCurrentCameraPosition)
            .uniform3d(PER_FRAME, "previousCameraPosition", tracker::getPreviousCameraPosition)
            .uniform3i(PER_FRAME, "cameraPositionInt", () -> tracker.currentInt)
            .uniform3f(PER_FRAME, "cameraPositionFract", () -> tracker.currentFract)
            .uniform3i(PER_FRAME, "previousCameraPositionInt", () -> tracker.previousInt)
            .uniform3f(PER_FRAME, "previousCameraPositionFract", () -> tracker.previousFract);
    }

    private static int getRenderDistanceInBlocks() {
        return client.options.viewDistance * 16;
    }

    public static Vector3d getUnshiftedCameraPosition(Vector3d dest) {
        Entity camera = client.getCamera();
        double tickDelta = CapturedRenderingState.INSTANCE.getTickDelta();

        return dest.set(camera.prevX + (camera.x - camera.prevX) * tickDelta,
            camera.prevY + (camera.y - camera.prevY) * tickDelta,
            camera.prevZ + (camera.z - camera.prevZ) * tickDelta);
    }

    static class CameraPositionTracker {
        // small enough to keep float precision, big enough that shifts (visible in shader animations) are rare
        // optifine uses 1000024, which is too much
        private static final double WALK_RANGE = 30000;
        private static final double TP_RANGE = 1000;

        private Vector3d previousCameraPosition = new Vector3d();
        private Vector3d currentCameraPosition = new Vector3d();
        private final Vector3d unshifted = new Vector3d();
        private final Vector3d shift = new Vector3d();
        // unshifted positions split for precision, as modern iris provides them
        private final Vector3i currentInt = new Vector3i();
        private final Vector3f currentFract = new Vector3f();
        private final Vector3i previousInt = new Vector3i();
        private final Vector3f previousFract = new Vector3f();

        CameraPositionTracker(FrameUpdateNotifier notifier) {
            notifier.addListener(this::update);
        }

        private void update() {
            getUnshiftedCameraPosition(unshifted);
            previousInt.set(currentInt);
            previousFract.set(currentFract);
            currentInt.set((int) Math.floor(unshifted.x), (int) Math.floor(unshifted.y), (int) Math.floor(unshifted.z));
            currentFract.set((float) (unshifted.x - currentInt.x), (float) (unshifted.y - currentInt.y), (float) (unshifted.z - currentInt.z));

            Vector3d recycled = previousCameraPosition;
            previousCameraPosition = currentCameraPosition;
            currentCameraPosition = recycled.set(unshifted).add(shift);

            updateShift();
        }

        private void updateShift() {
            double dX = getShift(currentCameraPosition.x, previousCameraPosition.x);
            double dZ = getShift(currentCameraPosition.z, previousCameraPosition.z);

            if (dX != 0.0 || dZ != 0.0) {
                applyShift(dX, dZ);
            }
        }

        private static double getShift(double value, double prevValue) {
            if (Math.abs(value) > WALK_RANGE || Math.abs(value - prevValue) > TP_RANGE) {
                // only shift by increments of WALK_RANGE, some packs like seus ptgi need this
                return -(value - (value % WALK_RANGE));
            } else {
                return 0.0;
            }
        }

        // shifts both positions so their difference stays the same
        private void applyShift(double dX, double dZ) {
            shift.x += dX;
            currentCameraPosition.x += dX;
            previousCameraPosition.x += dX;

            shift.z += dZ;
            currentCameraPosition.z += dZ;
            previousCameraPosition.z += dZ;
        }

        public Vector3d getCurrentCameraPosition() {
            return currentCameraPosition;
        }

        public Vector3d getPreviousCameraPosition() {
            return previousCameraPosition;
        }
    }
}
