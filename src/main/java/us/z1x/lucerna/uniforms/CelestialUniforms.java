package us.z1x.lucerna.uniforms;

import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import us.z1x.lucerna.gl.uniform.UniformHolder;

import java.util.Objects;

import static us.z1x.lucerna.gl.uniform.UniformUpdateFrequency.PER_FRAME;

public final class CelestialUniforms {
    private final float sunPathRotation;
    private final Matrix4f matrix = new Matrix4f();
    private final Vector4f position = new Vector4f();

    public CelestialUniforms(float sunPathRotation) {
        this.sunPathRotation = sunPathRotation;
    }

    public void addCelestialUniforms(UniformHolder uniforms) {
        uniforms
            .uniform1f(PER_FRAME, "sunAngle", CelestialUniforms::getSunAngle)
            .uniformTruncated3f(PER_FRAME, "sunPosition", this::getSunPosition)
            .uniformTruncated3f(PER_FRAME, "moonPosition", this::getMoonPosition)
            .uniform1f(PER_FRAME, "shadowAngle", CelestialUniforms::getShadowAngle)
            .uniformTruncated3f(PER_FRAME, "shadowLightPosition", this::getShadowLightPosition)
            .uniformTruncated3f(PER_FRAME, "upPosition", this::getUpPosition);
    }

    public static float getSunAngle() {
        float skyAngle = getSkyAngle();

        if (skyAngle < 0.75F) {
            return skyAngle + 0.25F;
        } else {
            return skyAngle - 0.75F;
        }
    }

    private static float getShadowAngle() {
        float shadowAngle = getSunAngle();

        if (!isDay()) {
            shadowAngle -= 0.5F;
        }

        return shadowAngle;
    }

    private Vector4f getSunPosition() {
        return getCelestialPosition(100.0F);
    }

    private Vector4f getMoonPosition() {
        return getCelestialPosition(-100.0F);
    }

    public Vector4f getShadowLightPosition() {
        return isDay() ? getSunPosition() : getMoonPosition();
    }

    public Vector4f getShadowLightPositionInWorldSpace() {
        return isDay() ? getCelestialPositionInWorldSpace(100.0F) : getCelestialPositionInWorldSpace(-100.0F);
    }

    private Vector4f getCelestialPositionInWorldSpace(float y) {
        // same transform as renderSky, needed before vanilla applies it
        return matrix.identity()
            .rotateY((float) Math.toRadians(-90.0F))
            .rotateZ((float) Math.toRadians(sunPathRotation))
            .rotateX((float) Math.toRadians(getSkyAngle() * 360.0F))
            .transform(position.set(0.0F, y, 0.0F, 0.0F));
    }

    private Vector4f getCelestialPosition(float y) {
        // same transform as renderSky, needed before vanilla applies it
        return matrix.set(CapturedRenderingState.INSTANCE.getGbufferModelView())
            .rotateY((float) Math.toRadians(-90.0F))
            .rotateZ((float) Math.toRadians(sunPathRotation))
            .rotateX((float) Math.toRadians(getSkyAngle() * 360.0F))
            .transform(position.set(0.0F, y, 0.0F, 0.0F));
    }

    private Vector4f getUpPosition() {
        // mirrors the fixed rotation of renderSky but skips the sky angle rotation
        return matrix.set(CapturedRenderingState.INSTANCE.getGbufferModelView())
            .rotateY((float) Math.toRadians(-90.0F))
            .transform(position.set(0.0F, 100.0F, 0.0F, 0.0F));
    }

    public static boolean isDay() {
        // World#isDay does calculations that do not work here
        return getSunAngle() <= 0.5;
    }

    private static ClientWorld getWorld() {
        return Objects.requireNonNull(Minecraft.getInstance().world);
    }

    private static float getSkyAngle() {
        return getWorld().getTimeOfDay(CapturedRenderingState.INSTANCE.getTickDelta());
    }
}
