package us.z1x.lucerna.uniforms;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.global.LightningBoltEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.world.WorldSettings;
import org.joml.Math;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import us.z1x.lucerna.gl.uniform.UniformHolder;
import us.z1x.lucerna.gl.uniform.UniformUpdateFrequency;

public class LucernaExclusiveUniforms {
    private static final Vector3d CAMERA_POSITION = new Vector3d();
    private static final Vector3d EYE_POSITION = new Vector3d();
    private static final Vector3d RELATIVE_EYE_POSITION = new Vector3d();
    private static final Vector4f ZERO = new Vector4f(0, 0, 0, 0);
    private static final Vector3f DIRECTION = new Vector3f();

    public static void addLucernaExclusiveUniforms(UniformHolder uniforms) {
        WorldInfoUniforms.addWorldInfoUniforms(uniforms);

        uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "thunderStrength", LucernaExclusiveUniforms::getThunderStrength);
        uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "currentPlayerHealth", LucernaExclusiveUniforms::getCurrentHealth);
        uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "maxPlayerHealth", LucernaExclusiveUniforms::getMaxHealth);
        uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "currentPlayerHunger", LucernaExclusiveUniforms::getCurrentHunger);
        uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "maxPlayerHunger", () -> 20);
        uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "currentPlayerAir", LucernaExclusiveUniforms::getCurrentAir);
        uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "maxPlayerAir", LucernaExclusiveUniforms::getMaxAir);
        uniforms.uniform1b(UniformUpdateFrequency.PER_FRAME, "firstPersonCamera", () -> Minecraft.getInstance().options.perspective == 0);
        uniforms.uniform1b(UniformUpdateFrequency.PER_TICK, "isSpectator", LucernaExclusiveUniforms::isSpectator);
        uniforms.uniform3d(UniformUpdateFrequency.PER_FRAME, "eyePosition", LucernaExclusiveUniforms::getEyePosition);
        uniforms.uniform3d(UniformUpdateFrequency.PER_FRAME, "relativeEyePosition", () -> CameraUniforms.getUnshiftedCameraPosition(RELATIVE_EYE_POSITION).sub(getEyePosition()));
        uniforms.uniform3f(UniformUpdateFrequency.PER_FRAME, "playerLookVector", LucernaExclusiveUniforms::getPlayerLookVector);
        uniforms.uniform3f(UniformUpdateFrequency.PER_FRAME, "playerBodyVector", LucernaExclusiveUniforms::getPlayerBodyVector);
        // 1.8.9 has no left handed option
        uniforms.uniform1b(UniformUpdateFrequency.ONCE, "isRightHanded", () -> true);
        uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "cloudTime", LucernaExclusiveUniforms::getCloudTime);
        uniforms.uniform4f(UniformUpdateFrequency.PER_TICK, "lightningBoltPosition", LucernaExclusiveUniforms::getLightningBoltPosition);
    }

    private static Vector4f getLightningBoltPosition() {
        ClientWorld world = Minecraft.getInstance().world;

        if (world == null) {
            return ZERO;
        }

        for (Entity entity : world.globalEntities) {
            if (entity instanceof LightningBoltEntity) {
                Vector3d camera = CameraUniforms.getUnshiftedCameraPosition(CAMERA_POSITION);
                return new Vector4f((float) (entity.x - camera.x), (float) (entity.y - camera.y), (float) (entity.z - camera.z), 1);
            }
        }

        return ZERO;
    }

    private static float getThunderStrength() {
        // some custom servers send values out of the 0 to 1 range
        return Math.clamp(0.0F, 1.0F,
            Minecraft.getInstance().world.getThunder(CapturedRenderingState.INSTANCE.getTickDelta()));
    }

    private static LocalClientPlayerEntity getSurvivalPlayer() {
        Minecraft client = Minecraft.getInstance();

        if (client.player == null || !client.interactionManager.getGameMode().isSurvival()) {
            return null;
        }

        return client.player;
    }

    private static float getCurrentHealth() {
        LocalClientPlayerEntity player = getSurvivalPlayer();
        return player == null ? -1 : player.getHealth() / player.getMaxHealth();
    }

    private static float getCurrentHunger() {
        LocalClientPlayerEntity player = getSurvivalPlayer();
        return player == null ? -1 : player.getHungerManager().getFoodLevel() / 20f;
    }

    private static float getCurrentAir() {
        LocalClientPlayerEntity player = getSurvivalPlayer();
        return player == null ? -1 : player.getBreath() / 300f;
    }

    private static float getMaxAir() {
        return getSurvivalPlayer() == null ? -1 : 300;
    }

    private static float getMaxHealth() {
        LocalClientPlayerEntity player = getSurvivalPlayer();
        return player == null ? -1 : player.getMaxHealth();
    }

    private static boolean isSpectator() {
        return Minecraft.getInstance().interactionManager.getGameMode() == WorldSettings.GameMode.SPECTATOR;
    }

    private static Vector3d getEyePosition() {
        return CameraUniforms.getUnshiftedCameraPosition(EYE_POSITION).add(0.0, Minecraft.getInstance().getCamera().getEyeHeight(), 0.0);
    }

    private static Vector3f getPlayerLookVector() {
        Entity camera = Minecraft.getInstance().getCamera();
        float tickDelta = CapturedRenderingState.INSTANCE.getTickDelta();
        return direction(camera.lastYaw + (camera.yaw - camera.lastYaw) * tickDelta, camera.lastPitch + (camera.pitch - camera.lastPitch) * tickDelta);
    }

    private static Vector3f getPlayerBodyVector() {
        Entity camera = Minecraft.getInstance().getCamera();

        if (!(camera instanceof LivingEntity)) {
            return direction(camera.yaw, 0.0F);
        }

        LivingEntity living = (LivingEntity) camera;
        float tickDelta = CapturedRenderingState.INSTANCE.getTickDelta();
        return direction(living.lastBodyYaw + (living.bodyYaw - living.lastBodyYaw) * tickDelta, 0.0F);
    }

    private static Vector3f direction(float yaw, float pitch) {
        float yawRadians = Math.toRadians(yaw);
        float pitchRadians = Math.toRadians(pitch);
        float horizontal = Math.cos(pitchRadians);
        return DIRECTION.set(-Math.sin(yawRadians) * horizontal, -Math.sin(pitchRadians), Math.cos(yawRadians) * horizontal);
    }

    private static float getCloudTime() {
        ClientWorld world = Minecraft.getInstance().world;
        // vanilla moves clouds by 0.03 blocks per tick
        return world == null ? 0.0F : (world.getTime() + CapturedRenderingState.INSTANCE.getTickDelta()) * 0.03F;
    }

    public static class WorldInfoUniforms {
        public static void addWorldInfoUniforms(UniformHolder uniforms) {
            uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "bedrockLevel", () -> 0);
            uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "heightLimit", () -> 256);
            uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "logicalHeightLimit", () -> 256);
            uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "cloudHeight", () -> {
                ClientWorld world = Minecraft.getInstance().world;
                return world == null ? 128.0F : world.dimension.getCloudHeight();
            });
            uniforms.uniform1b(UniformUpdateFrequency.PER_FRAME, "hasCeiling", () -> {
                ClientWorld world = Minecraft.getInstance().world;
                return world != null && world.dimension.getId() == -1;
            });
            uniforms.uniform1b(UniformUpdateFrequency.PER_FRAME, "hasSkylight", () -> {
                ClientWorld world = Minecraft.getInstance().world;
                return world == null || !world.dimension.hasNoSky();
            });
            uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "ambientLight", () -> {
                ClientWorld world = Minecraft.getInstance().world;
                return world != null ? world.dimension.getBrightnessTable()[0] : 0f;
            });
        }
    }
}
