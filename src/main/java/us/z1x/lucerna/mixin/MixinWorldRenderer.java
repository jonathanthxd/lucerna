package us.z1x.lucerna.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.pipeline.CloudRenderer;
import us.z1x.lucerna.pipeline.WorldRenderingPhase;
import us.z1x.lucerna.pipeline.WorldRenderingPipeline;

@Mixin(WorldRenderer.class)
public class MixinWorldRenderer {
    @Shadow
    @Final
    private static Identifier CLOUDS_LOCATION;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private TextureManager textureManager;

    @Shadow
    private ClientWorld world;

    @Shadow
    private int ticks;

    @Unique
    private static final String LUCERNA_RENDER_SKY = "renderSky(FI)V";

    @Unique
    private static void lucerna$setPhase(WorldRenderingPhase phase) {
        WorldRenderingPipeline pipeline = Lucerna.getPipelineManager().getPipelineNullable();

        if (pipeline != null) {
            pipeline.setPhase(phase);
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/render/block/BlockLayer;DILnet/minecraft/entity/Entity;)I", at = @At("HEAD"))
    private void lucerna$beginTerrainLayer(BlockLayer layer, double tickDelta, int anaglyphRenderPass, Entity camera, CallbackInfoReturnable<Integer> cir) {
        WorldRenderingPipeline pipeline = Lucerna.getPipelineManager().getPipelineNullable();

        if (pipeline != null) {
            pipeline.setPhase(WorldRenderingPhase.fromTerrainRenderType(layer));
            pipeline.beginTerrainRendering();
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/render/block/BlockLayer;DILnet/minecraft/entity/Entity;)I", at = @At("RETURN"))
    private void lucerna$endTerrainLayer(BlockLayer layer, double tickDelta, int anaglyphRenderPass, Entity camera, CallbackInfoReturnable<Integer> cir) {
        WorldRenderingPipeline pipeline = Lucerna.getPipelineManager().getPipelineNullable();

        if (pipeline != null) {
            pipeline.endTerrainRendering();
            pipeline.setPhase(WorldRenderingPhase.NONE);
        }
    }

    @Inject(method = LUCERNA_RENDER_SKY, at = @At("HEAD"))
    private void lucerna$beginSky(float tickDelta, int anaglyphRenderPass, CallbackInfo ci) {
        lucerna$setPhase(WorldRenderingPhase.SKY);
    }

    @Inject(method = LUCERNA_RENDER_SKY, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/dimension/Dimension;getSunriseColor(FF)[F"))
    private void lucerna$beginSunset(float tickDelta, int anaglyphRenderPass, CallbackInfo ci) {
        lucerna$setPhase(WorldRenderingPhase.SUNSET);
    }

    @Inject(method = LUCERNA_RENDER_SKY, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientWorld;getTimeOfDay(F)F", ordinal = 1))
    private void lucerna$tiltSun(float tickDelta, int anaglyphRenderPass, CallbackInfo ci) {
        WorldRenderingPipeline pipeline = Lucerna.getPipelineManager().getPipelineNullable();

        if (pipeline != null) {
            GlStateManager.rotatef(pipeline.getSunPathRotation(), 0.0F, 0.0F, 1.0F);
        }
    }

    @Inject(method = LUCERNA_RENDER_SKY, at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/world/WorldRenderer;SUN_LOCATION:Lnet/minecraft/resource/Identifier;"))
    private void lucerna$beginSun(float tickDelta, int anaglyphRenderPass, CallbackInfo ci) {
        lucerna$setPhase(WorldRenderingPhase.SUN);
    }

    @Inject(method = LUCERNA_RENDER_SKY, at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/world/WorldRenderer;MOON_PHASES_LOCATION:Lnet/minecraft/resource/Identifier;"))
    private void lucerna$beginMoon(float tickDelta, int anaglyphRenderPass, CallbackInfo ci) {
        lucerna$setPhase(WorldRenderingPhase.MOON);
    }

    @Inject(method = LUCERNA_RENDER_SKY, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientWorld;getStarBrightness(F)F"))
    private void lucerna$beginStars(float tickDelta, int anaglyphRenderPass, CallbackInfo ci) {
        lucerna$setPhase(WorldRenderingPhase.STARS);
    }

    @Inject(method = LUCERNA_RENDER_SKY, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/living/player/LocalClientPlayerEntity;getEyePosition(F)Lnet/minecraft/util/math/Vec3d;"))
    private void lucerna$beginVoid(float tickDelta, int anaglyphRenderPass, CallbackInfo ci) {
        lucerna$setPhase(WorldRenderingPhase.VOID);
    }

    @WrapOperation(method = LUCERNA_RENDER_SKY, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V", ordinal = 1))
    private void lucerna$renderSun(Tesselator tesselator, Operation<Void> original) {
        WorldRenderingPipeline pipeline = Lucerna.getPipelineManager().getPipelineNullable();
        lucerna$endOrDiscard(tesselator, original, pipeline == null || pipeline.shouldRenderSun());
    }

    @WrapOperation(method = LUCERNA_RENDER_SKY, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V", ordinal = 2))
    private void lucerna$renderMoon(Tesselator tesselator, Operation<Void> original) {
        WorldRenderingPipeline pipeline = Lucerna.getPipelineManager().getPipelineNullable();
        lucerna$endOrDiscard(tesselator, original, pipeline == null || pipeline.shouldRenderMoon());
    }

    @Unique
    private static void lucerna$endOrDiscard(Tesselator tesselator, Operation<Void> original, boolean draw) {
        if (draw) {
            original.call(tesselator);
        } else {
            // the buffer still has to finish building, or the next sky draw cannot begin
            tesselator.getBuffer().end();
            tesselator.getBuffer().clear();
        }
    }

    @Inject(method = LUCERNA_RENDER_SKY, at = @At("RETURN"))
    private void lucerna$endSky(float tickDelta, int anaglyphRenderPass, CallbackInfo ci) {
        lucerna$setPhase(WorldRenderingPhase.NONE);
    }

    @Inject(method = "renderFancyClouds", at = @At("HEAD"), cancellable = true)
    private void lucerna$renderCachedClouds(float tickDelta, int anaglyphRenderPass, CallbackInfo ci) {
        if (!Lucerna.isShaderPipelineActive()) {
            return;
        }

        Entity camera = minecraft.getCamera();
        double ticks = this.ticks + tickDelta;
        double x = (camera.lastX + (camera.x - camera.lastX) * tickDelta + ticks * 0.03F) / 12.0;
        double z = (camera.lastZ + (camera.z - camera.lastZ) * tickDelta) / 12.0 + 0.33F;
        float height = world.dimension.getCloudHeight() - (float) (camera.prevY + (camera.y - camera.prevY) * tickDelta) + 0.33F;
        Vec3d color = world.getCloudColor(tickDelta);
        float red = (float) color.x;
        float green = (float) color.y;
        float blue = (float) color.z;

        if (anaglyphRenderPass != 2) {
            float gray = (red * 30.0F + green * 59.0F + blue * 11.0F) / 100.0F;
            float redGreen = (red * 30.0F + green * 70.0F) / 100.0F;
            float redBlue = (red * 30.0F + blue * 70.0F) / 100.0F;
            red = gray;
            green = redGreen;
            blue = redBlue;
        }

        GlStateManager.disableCull();
        textureManager.bind(CLOUDS_LOCATION);
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        CloudRenderer.INSTANCE.render(x - MathHelper.floor(x / 2048.0) * 2048, z - MathHelper.floor(z / 2048.0) * 2048, height, red, green, blue, anaglyphRenderPass);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        ci.cancel();
    }

    @Inject(method = "renderEntities", at = @At("HEAD"))
    private void lucerna$beginEntities(Entity camera, Culler culler, float tickDelta, CallbackInfo ci) {
        lucerna$setPhase(WorldRenderingPhase.ENTITIES);
    }

    @Inject(method = "renderEntities", at = @At("RETURN"))
    private void lucerna$endEntities(Entity camera, Culler culler, float tickDelta, CallbackInfo ci) {
        lucerna$setPhase(WorldRenderingPhase.NONE);
    }

    @Inject(method = "renderBlockOutline", at = @At("HEAD"))
    private void lucerna$beginOutline(PlayerEntity camera, HitResult hit, int mode, float tickDelta, CallbackInfo ci) {
        lucerna$setPhase(WorldRenderingPhase.OUTLINE);
    }

    @Inject(method = "renderBlockOutline", at = @At("RETURN"))
    private void lucerna$endOutline(PlayerEntity camera, HitResult hit, int mode, float tickDelta, CallbackInfo ci) {
        lucerna$setPhase(WorldRenderingPhase.NONE);
    }

    @Inject(method = "renderMiningProgress", at = @At("HEAD"))
    private void lucerna$beginDestroy(Tesselator tesselator, BufferBuilder bufferBuilder, Entity camera, float tickDelta, CallbackInfo ci) {
        lucerna$setPhase(WorldRenderingPhase.DESTROY);
    }

    @Inject(method = "renderMiningProgress", at = @At("RETURN"))
    private void lucerna$endDestroy(Tesselator tesselator, BufferBuilder bufferBuilder, Entity camera, float tickDelta, CallbackInfo ci) {
        lucerna$setPhase(WorldRenderingPhase.NONE);
    }

    @Inject(method = "renderWorldBorder", at = @At("HEAD"))
    private void lucerna$beginWorldBorder(Entity camera, float tickDelta, CallbackInfo ci) {
        lucerna$setPhase(WorldRenderingPhase.WORLD_BORDER);
    }

    @Inject(method = "renderWorldBorder", at = @At("RETURN"))
    private void lucerna$endWorldBorder(Entity camera, float tickDelta, CallbackInfo ci) {
        lucerna$setPhase(WorldRenderingPhase.NONE);
    }
}
