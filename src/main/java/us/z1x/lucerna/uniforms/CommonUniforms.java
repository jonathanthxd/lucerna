package us.z1x.lucerna.uniforms;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.AbstractTexture;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import org.joml.Math;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector4i;
import us.z1x.lucerna.gl.state.StateUpdateNotifiers;
import us.z1x.lucerna.gl.uniform.DynamicUniformHolder;
import us.z1x.lucerna.gl.uniform.UniformHolder;
import us.z1x.lucerna.layer.GbufferPrograms;
import us.z1x.lucerna.shaderpack.IdMap;
import us.z1x.lucerna.shaderpack.PackDirectives;
import us.z1x.lucerna.texture.TextureInfoCache;
import us.z1x.lucerna.texture.TextureInfoCache.TextureInfo;
import us.z1x.lucerna.texture.TextureTracker;
import us.z1x.lucerna.uniforms.transforms.SmoothedFloat;
import us.z1x.lucerna.uniforms.transforms.SmoothedVec2f;

import java.util.function.Predicate;

import static us.z1x.lucerna.gl.uniform.UniformUpdateFrequency.ONCE;
import static us.z1x.lucerna.gl.uniform.UniformUpdateFrequency.PER_FRAME;
import static us.z1x.lucerna.gl.uniform.UniformUpdateFrequency.PER_TICK;

public final class CommonUniforms {
    private static final Minecraft client = Minecraft.getInstance();
    private static final Vector2i ZERO_VECTOR_2i = new Vector2i();
    private static final Vector4i ZERO_VECTOR_4i = new Vector4i(0, 0, 0, 0);
    private static final Vector3d ZERO_VECTOR_3D = new Vector3d();
    private static final Vector2i ATLAS_SIZE = new Vector2i();
    private static final Vector2i TEXTURE_SIZE = new Vector2i();
    private static final Vector4i BLEND_FUNC = new Vector4i();
    private static final Vector2i EYE_BRIGHTNESS = new Vector2i();
    private static final Vector2i EYE_BRIGHTNESS_SMOOTH = new Vector2i();
    private static final Vector3d SKY_COLOR = new Vector3d();
    private static final BlockPos.Mutable EYE_BLOCK_POS = new BlockPos.Mutable();

    private CommonUniforms() {
    }

    public static void addCommonUniforms(DynamicUniformHolder uniforms, IdMap idMap, PackDirectives directives, FrameUpdateNotifier updateNotifier) {
        CommonUniforms.addNonDynamicUniforms(uniforms, idMap, directives, updateNotifier);
        CommonUniforms.addDynamicUniforms(uniforms);
    }

    public static void addDynamicUniforms(DynamicUniformHolder uniforms) {
        FogUniforms.addFogUniforms(uniforms);

        uniforms.uniform1i("entityId", CapturedRenderingState.INSTANCE::getCurrentRenderedEntity,
                CapturedRenderingState.INSTANCE.getEntityIdNotifier());

        uniforms.uniform1i("currentRenderedItemId", CapturedRenderingState.INSTANCE::getCurrentRenderedItem,
                CapturedRenderingState.INSTANCE.getItemIdNotifier());

        uniforms.uniform1i("blockEntityId", CapturedRenderingState.INSTANCE::getCurrentRenderedBlockEntity,
                CapturedRenderingState.INSTANCE.getBlockEntityIdNotifier());

        uniforms.uniform2i("atlasSize", () -> {
            int glId = GlStateManager.TEXTURES[0].texture;

            AbstractTexture texture = TextureTracker.INSTANCE.getTexture(glId);
            if (texture instanceof TextureAtlas) {
                TextureInfo info = TextureInfoCache.INSTANCE.getInfo(glId);
                return ATLAS_SIZE.set(info.getWidth(), info.getHeight());
            }

            return ZERO_VECTOR_2i;
        }, StateUpdateNotifiers.bindTextureNotifier);

        uniforms.uniform2i("gtextureSize", () -> {
            int glId = GlStateManager.TEXTURES[0].texture;

            TextureInfo info = TextureInfoCache.INSTANCE.getInfo(glId);
            return TEXTURE_SIZE.set(info.getWidth(), info.getHeight());
        }, StateUpdateNotifiers.bindTextureNotifier);

        uniforms.uniform4i("blendFunc", () -> {
            GlStateManager.Blend blend = GlStateManager.BLEND;

            if (blend.state.enabled) {
                return BLEND_FUNC.set(blend.sfactorRGB, blend.dfactorRGB, blend.sfactorAlpha, blend.dfactorAlpha);
            } else {
                return ZERO_VECTOR_4i;
            }
        }, StateUpdateNotifiers.blendFuncNotifier);

        uniforms.uniform1i("renderStage", () -> GbufferPrograms.getCurrentPhase().ordinal(), StateUpdateNotifiers.phaseChangeNotifier);

        // the hurt and creeper flash color of the entity that is being rendered
        uniforms.uniform4f("entityColor", CapturedRenderingState.INSTANCE::getCurrentEntityColor, CapturedRenderingState.INSTANCE.getEntityColorNotifier());
    }

    public static void addNonDynamicUniforms(UniformHolder uniforms, IdMap idMap, PackDirectives directives, FrameUpdateNotifier updateNotifier) {
        CameraUniforms.addCameraUniforms(uniforms, updateNotifier);
        ViewportUniforms.addViewportUniforms(uniforms);
        WorldTimeUniforms.addWorldTimeUniforms(uniforms);
        SystemTimeUniforms.addSystemTimeUniforms(uniforms);
        BiomeUniforms.addBiomeUniforms(uniforms);
        new CelestialUniforms(directives.getSunPathRotation()).addCelestialUniforms(uniforms);
        IdMapUniforms.addIdMapUniforms(updateNotifier, uniforms, idMap, directives.isOldHandLight());
        LucernaExclusiveUniforms.addLucernaExclusiveUniforms(uniforms);
        MatrixUniforms.addMatrixUniforms(uniforms, directives);
        CommonUniforms.generalCommonUniforms(uniforms, updateNotifier, directives);
    }

    public static void generalCommonUniforms(UniformHolder uniforms, FrameUpdateNotifier updateNotifier, PackDirectives directives) {
        ExternallyManagedUniforms.addExternallyManagedUniforms(uniforms);

        SmoothedVec2f eyeBrightnessSmooth = new SmoothedVec2f(directives.getEyeBrightnessHalfLife(), directives.getEyeBrightnessHalfLife(), CommonUniforms::getEyeBrightness, updateNotifier);

        uniforms
            .uniform1b(PER_FRAME, "hideGUI", () -> client.options.hideGui)
            .uniform1f(PER_FRAME, "eyeAltitude", () -> client.getCamera().y + client.getCamera().getEyeHeight())
            .uniform1i(PER_FRAME, "isEyeInWater", CommonUniforms::isEyeInWater)
            .uniform1f(PER_FRAME, "blindness", CommonUniforms::getBlindness)
            .uniform1f(PER_FRAME, "nightVision", CommonUniforms::getNightVision)
            // 1.8.9 has no darkness effect
            .uniform1f(PER_FRAME, "darknessFactor", () -> 0.0F)
            .uniform1f(PER_FRAME, "darknessLightFactor", () -> 0.0F)
            // 1.8.9 has no end flash
            .uniform1f(ONCE, "endFlashIntensity", () -> 0.0F)
            .uniform1b(PER_TICK, "is_sneaking", () -> cameraEntityIs(Entity::isSneaking))
            .uniform1b(PER_TICK, "is_sprinting", () -> cameraEntityIs(Entity::isSprinting))
            .uniform1b(PER_TICK, "is_invisible", () -> cameraEntityIs(Entity::isInvisible))
            .uniform1b(PER_TICK, "is_burning", () -> cameraEntityIs(Entity::isOnFire))
            .uniform1b(PER_TICK, "is_on_ground", () -> cameraEntityIs(entity -> entity.onGround))
            .uniform1b(PER_TICK, "is_hurt", () -> cameraEntityIs(entity -> entity instanceof LivingEntity living && living.damagedTimer > 0))
            // optifine's default alpha test reference
            .uniform1f(ONCE, "alphaTestRef", () -> 0.1F)
            // 1.8.9 fog is spherical, modern packs read the shape
            .uniform1i(ONCE, "fogShape", () -> 0)
            .uniform1f(PER_FRAME, "screenBrightness", () -> client.options.gamma)
            .uniform1f(PER_TICK, "playerMood", CommonUniforms::getPlayerMood)
            .uniform2i(PER_FRAME, "eyeBrightness", CommonUniforms::getEyeBrightness)
            .uniform2i(PER_FRAME, "eyeBrightnessSmooth", () -> {
                Vector2f smoothed = eyeBrightnessSmooth.get();
                return EYE_BRIGHTNESS_SMOOTH.set((int) smoothed.x(), (int) smoothed.y());
            })
            .uniform1f(PER_TICK, "rainStrength", CommonUniforms::getRainStrength)
            .uniform1f(PER_TICK, "wetness", new SmoothedFloat(directives.getWetnessHalfLife(), directives.getDrynessHalfLife(), CommonUniforms::getRainStrength, updateNotifier))
            .uniform3d(PER_FRAME, "skyColor", CommonUniforms::getSkyColor)
            .uniform3d(PER_FRAME, "fogColor", CapturedRenderingState.INSTANCE::getFogColor);
    }

    private static boolean cameraEntityIs(Predicate<Entity> condition) {
        Entity camera = client.getCamera();

        return camera != null && condition.test(camera);
    }

    private static Vector3d getSkyColor() {
        if (client.world == null || client.getCamera() == null) {
            return ZERO_VECTOR_3D;
        }

        Vec3d color = client.world.getSkyColor(client.getCamera(), CapturedRenderingState.INSTANCE.getTickDelta());
        return SKY_COLOR.set(color.x, color.y, color.z);
    }

    static float getBlindness() {
        Entity cameraEntity = client.getCamera();

        if (cameraEntity instanceof LivingEntity) {
            StatusEffectInstance blindness = ((LivingEntity) cameraEntity).getEffectInstance(StatusEffect.BLINDNESS);

            if (blindness != null) {
                return Math.clamp(0.0F, 1.0F, blindness.getDuration() / 20.0F);
            }
        }

        return 0.0F;
    }

    private static float getPlayerMood() {
        return 0.0F;
    }

    static float getRainStrength() {
        if (client.world == null) {
            return 0f;
        }

        return Math.clamp(0.0F, 1.0F, client.world.getRain(CapturedRenderingState.INSTANCE.getTickDelta()));
    }

    private static Vector2i getEyeBrightness() {
        Entity camera = client.getCamera();

        if (camera == null || client.world == null) {
            return ZERO_VECTOR_2i;
        }

        BlockPos eyeBlockPos = EYE_BLOCK_POS.set(MathHelper.floor(camera.x), MathHelper.floor(camera.y + camera.getEyeHeight()), MathHelper.floor(camera.z));

        int blockLight = client.world.getBrightness(LightType.BLOCK, eyeBlockPos);
        int skyLight = client.world.getBrightness(LightType.SKY, eyeBlockPos);

        return EYE_BRIGHTNESS.set(blockLight * 16, skyLight * 16);
    }

    private static float getNightVision() {
        Entity cameraEntity = client.getCamera();

        if (cameraEntity instanceof LivingEntity) {
            StatusEffectInstance nightVision = ((LivingEntity) cameraEntity).getEffectInstance(StatusEffect.NIGHTVISION);

            if (nightVision != null) {
                int duration = nightVision.getDuration();
                float tickDelta = CapturedRenderingState.INSTANCE.getTickDelta();
                return duration > 200 ? 1.0F : 0.7F + MathHelper.sin((duration - tickDelta) * (float) Math.PI * 0.2F) * 0.3F;
            }
        }

        return 0.0F;
    }

    static int isEyeInWater() {
        if (client.world == null || client.getCamera() == null) {
            return 0;
        }

        Block block = Camera.getBlockInside(client.world, client.getCamera(), CapturedRenderingState.INSTANCE.getTickDelta());
        Material material = block.getMaterial();

        if (material == Material.WATER) {
            return 1;
        } else if (material == Material.LAVA) {
            return 2;
        } else {
            return 0;
        }
    }

    static {
        GbufferPrograms.init();
    }
}
