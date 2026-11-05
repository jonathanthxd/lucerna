package us.z1x.lucerna.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleManager;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.ItemInHandRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.Project;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.gl.program.Program;
import us.z1x.lucerna.pipeline.HandRenderer;
import us.z1x.lucerna.pipeline.WorldRenderingPhase;
import us.z1x.lucerna.pipeline.WorldRenderingPipeline;
import us.z1x.lucerna.uniforms.CapturedRenderingState;
import us.z1x.lucerna.uniforms.SystemTimeUniforms;

import java.nio.FloatBuffer;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {
    @Unique
    private static final String LUCERNA_RENDER_WORLD_PASS = "render(IFJ)V";

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private float fogRed;

    @Shadow
    private float fogGreen;

    @Shadow
    private float fogBlue;

    @Shadow
    private float renderDistance;

    @Shadow
    @Final
    public ItemInHandRenderer itemInHandRenderer;

    @Unique
    private final FloatBuffer lucerna$matrixBuffer = BufferUtils.createFloatBuffer(16);

    @Unique
    private final Matrix4f lucerna$matrix = new Matrix4f();

    @Unique
    private WorldRenderingPipeline lucerna$pipeline;

    @Unique
    private boolean lucerna$deferParticlesAndWeather;

    @Shadow
    protected abstract float getFov(float tickDelta, boolean fovChanged);

    @Shadow
    protected abstract void setupFog(int mode, float tickDelta);

    @Shadow
    protected abstract void renderSnowAndRain(float tickDelta);

    @Shadow
    protected abstract void renderClouds(WorldRenderer worldRenderer, float tickDelta, int anaglyphRenderPass);

    @Shadow
    public abstract void enableLightMap();

    @Shadow
    public abstract void disableLightMap();

    @Inject(method = "render(FJ)V", at = @At("HEAD"))
    private void lucerna$tickBackgroundReload(float tickDelta, long renderTimeLimit, CallbackInfo ci) {
        Lucerna.tickBackgroundReload();
    }

    @Inject(method = "setupClearColor", at = @At("RETURN"))
    private void lucerna$captureFogColor(float tickDelta, CallbackInfo ci) {
        CapturedRenderingState.INSTANCE.setFogColor(fogRed, fogGreen, fogBlue);
    }

    // begin after the camera setup, so the gbuffer matrices are known
    @Inject(method = LUCERNA_RENDER_WORLD_PASS, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setup(Lnet/minecraft/entity/living/player/PlayerEntity;Z)V", shift = At.Shift.AFTER))
    private void lucerna$beginLevelRender(int anaglyphRenderPass, float tickDelta, long renderTimeLimit, CallbackInfo ci) {
        GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX, lucerna$matrixBuffer);
        CapturedRenderingState.INSTANCE.setGbufferModelView(lucerna$matrix.set(lucerna$matrixBuffer));
        GL11.glGetFloatv(GL11.GL_PROJECTION_MATRIX, lucerna$matrixBuffer);
        CapturedRenderingState.INSTANCE.setGbufferProjection(lucerna$matrix.set(lucerna$matrixBuffer));
        CapturedRenderingState.INSTANCE.setTickDelta(tickDelta);
        SystemTimeUniforms.COUNTER.beginFrame();
        SystemTimeUniforms.TIMER.beginFrame(System.nanoTime());

        Program.unbind();

        lucerna$pipeline = Lucerna.getPipelineManager().preparePipeline(Lucerna.getCurrentDimension());
        lucerna$deferParticlesAndWeather = Lucerna.isShaderPipelineActive() && !lucerna$pipeline.shouldRenderParticlesBeforeDeferred();

        lucerna$pipeline.beginLevelRendering();
    }

    // render shadows before the main terrain setup, since the shadow pass submits the terrain search for this frame
    @Inject(method = LUCERNA_RENDER_WORLD_PASS, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;setupRender(Lnet/minecraft/entity/Entity;DLnet/minecraft/client/render/Culler;IZ)V"))
    private void lucerna$renderShadows(int anaglyphRenderPass, float tickDelta, long renderTimeLimit, CallbackInfo ci, @Local Culler culler) {
        lucerna$pipeline.renderShadows(minecraft.worldRenderer, minecraft.getCamera(), culler);
    }

    @WrapOperation(method = LUCERNA_RENDER_WORLD_PASS, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;renderClouds(Lnet/minecraft/client/render/world/WorldRenderer;FI)V"))
    private void lucerna$renderClouds(GameRenderer gameRenderer, WorldRenderer worldRenderer, float tickDelta, int anaglyphRenderPass, Operation<Void> original) {
        // shader packs get the clouds after the translucent terrain, see lucerna$renderLateWorld
        if (!Lucerna.isShaderPipelineActive()) {
            original.call(gameRenderer, worldRenderer, tickDelta, anaglyphRenderPass);
        }
    }

    @WrapOperation(method = LUCERNA_RENDER_WORLD_PASS, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/ParticleManager;renderLit(Lnet/minecraft/entity/Entity;F)V"))
    private void lucerna$renderLitParticles(ParticleManager particleManager, Entity camera, float tickDelta, Operation<Void> original) {
        if (!lucerna$deferParticlesAndWeather) {
            lucerna$pipeline.setPhase(WorldRenderingPhase.PARTICLES);
            original.call(particleManager, camera, tickDelta);
            lucerna$pipeline.setPhase(WorldRenderingPhase.NONE);
        }
    }

    @WrapOperation(method = LUCERNA_RENDER_WORLD_PASS, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/ParticleManager;render(Lnet/minecraft/entity/Entity;F)V"))
    private void lucerna$renderParticles(ParticleManager particleManager, Entity camera, float tickDelta, Operation<Void> original) {
        if (!lucerna$deferParticlesAndWeather) {
            lucerna$pipeline.setPhase(WorldRenderingPhase.PARTICLES);
            original.call(particleManager, camera, tickDelta);
            lucerna$pipeline.setPhase(WorldRenderingPhase.NONE);
        }
    }

    @WrapOperation(method = LUCERNA_RENDER_WORLD_PASS, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;renderSnowAndRain(F)V"))
    private void lucerna$renderWeather(GameRenderer gameRenderer, float tickDelta, Operation<Void> original) {
        if (!lucerna$deferParticlesAndWeather) {
            lucerna$pipeline.setPhase(WorldRenderingPhase.RAIN_SNOW);
            GlStateManager.depthMask(lucerna$pipeline.shouldWriteRainAndSnowToDepthBuffer());
            original.call(gameRenderer, tickDelta);
            lucerna$pipeline.setPhase(WorldRenderingPhase.NONE);
        }
    }

    @Inject(method = LUCERNA_RENDER_WORLD_PASS, at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V", args = "ldc=translucent"))
    private void lucerna$beginTranslucents(int anaglyphRenderPass, float tickDelta, long renderTimeLimit, CallbackInfo ci) {
        if (!Lucerna.isShaderPipelineActive()) {
            return;
        }

        lucerna$pipeline.beginHand();

        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        HandRenderer.INSTANCE.renderSolid(tickDelta, lucerna$pipeline);
        // the item glint leaves a different alpha blend func, so restore the one vanilla set for translucents
        GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.enableBlend();
        GlStateManager.depthMask(false);

        minecraft.profiler.swap("lucerna_pre_translucent");
        lucerna$pipeline.beginTranslucents();

        // the deferred passes unbind every texture unit
        minecraft.getTextureManager().bind(TextureAtlas.BLOCKS_LOCATION);
    }

    // iris packs expect particles, clouds and weather after the deferred passes, but 1.8.9 draws the clouds before the terrain
    @Inject(method = LUCERNA_RENDER_WORLD_PASS, at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V", args = "ldc=hand"))
    private void lucerna$renderLateWorld(int anaglyphRenderPass, float tickDelta, long renderTimeLimit, CallbackInfo ci) {
        if (!Lucerna.isShaderPipelineActive()) {
            return;
        }

        if (lucerna$deferParticlesAndWeather) {
            Entity camera = minecraft.getCamera();

            lucerna$pipeline.setPhase(WorldRenderingPhase.PARTICLES);
            enableLightMap();
            minecraft.particleManager.renderLit(camera, tickDelta);
            Lighting.turnOff();
            setupFog(0, tickDelta);
            minecraft.particleManager.render(camera, tickDelta);
            disableLightMap();
        }

        lucerna$pipeline.setPhase(WorldRenderingPhase.CLOUDS);
        GlStateManager.depthMask(true);
        renderClouds(minecraft.worldRenderer, tickDelta, anaglyphRenderPass);

        if (lucerna$deferParticlesAndWeather) {
            lucerna$pipeline.setPhase(WorldRenderingPhase.RAIN_SNOW);
            GlStateManager.depthMask(lucerna$pipeline.shouldWriteRainAndSnowToDepthBuffer());
            GlStateManager.enableCull();
            renderSnowAndRain(tickDelta);
        }

        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.disableFog();
        lucerna$pipeline.setPhase(WorldRenderingPhase.NONE);
    }

    // vanilla skips the sky below 4 chunks, but packs expect the sky pass at any render distance
    @ModifyExpressionValue(method = LUCERNA_RENDER_WORLD_PASS, at = @At(value = "FIELD", target = "Lnet/minecraft/client/options/GameOptions;viewDistance:I"))
    private int lucerna$alwaysRenderSky(int viewDistance) {
        return Lucerna.isShaderPipelineActive() ? Math.max(viewDistance, 4) : viewDistance;
    }

    // the hand goes into the gbuffers before the translucent terrain, so skip the vanilla hand pass
    @ModifyExpressionValue(method = LUCERNA_RENDER_WORLD_PASS, at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/GameRenderer;newCamPitch:Z"))
    private boolean lucerna$skipVanillaHand(boolean renderHand) {
        return renderHand && !Lucerna.isShaderPipelineActive();
    }

    @Inject(method = LUCERNA_RENDER_WORLD_PASS, at = @At("RETURN"))
    private void lucerna$endLevelRender(int anaglyphRenderPass, float tickDelta, long renderTimeLimit, CallbackInfo ci) {
        minecraft.profiler.swap("lucerna_final");
        lucerna$pipeline.finalizeLevelRendering();
        lucerna$pipeline = null;
        Program.unbind();

        if (Lucerna.isShaderPipelineActive()) {
            lucerna$renderScreenEffects(tickDelta);
        }
    }

    @Unique
    private void lucerna$renderScreenEffects(float tickDelta) {
        Entity camera = minecraft.getCamera();

        if (minecraft.options.perspective != 0 || camera instanceof LivingEntity living && living.isSleeping()) {
            return;
        }

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.loadIdentity();
        Project.gluPerspective(getFov(tickDelta, false), (float) minecraft.width / minecraft.height, 0.05F, renderDistance * 2.0F);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.loadIdentity();

        itemInHandRenderer.renderScreenEffects(tickDelta);
    }
}
