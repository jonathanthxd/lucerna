package us.z1x.lucerna.mixin;

import net.minecraft.client.render.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.gbufferoverrides.state.StateTracker;
import us.z1x.lucerna.gl.blending.AlphaTestStorage;
import us.z1x.lucerna.gl.blending.BlendModeStorage;
import us.z1x.lucerna.gl.state.StateUpdateNotifiers;
import us.z1x.lucerna.pipeline.WorldRenderingPipeline;
import us.z1x.lucerna.samplers.LucernaSamplers;
import us.z1x.lucerna.texture.TextureInfoCache;
import us.z1x.lucerna.texture.TextureTracker;

@Mixin(GlStateManager.class)
public class MixinGlStateManager {
    @Unique
    private static final int LUCERNA_TEXTURE_UNITS = 32;

    @Shadow
    private static int texture;

    @Unique
    private static Runnable lucerna$fogToggleListener;
    @Unique
    private static Runnable lucerna$fogModeListener;
    @Unique
    private static Runnable lucerna$fogStartListener;
    @Unique
    private static Runnable lucerna$fogEndListener;
    @Unique
    private static Runnable lucerna$fogDensityListener;
    @Unique
    private static Runnable lucerna$blendFuncListener;

    static {
        StateUpdateNotifiers.fogToggleNotifier = listener -> lucerna$fogToggleListener = listener;
        StateUpdateNotifiers.fogModeNotifier = listener -> lucerna$fogModeListener = listener;
        StateUpdateNotifiers.fogStartNotifier = listener -> lucerna$fogStartListener = listener;
        StateUpdateNotifiers.fogEndNotifier = listener -> lucerna$fogEndListener = listener;
        StateUpdateNotifiers.fogDensityNotifier = listener -> lucerna$fogDensityListener = listener;
        StateUpdateNotifiers.blendFuncNotifier = listener -> lucerna$blendFuncListener = listener;
    }

    // vanilla only tracks 8 texture units, shader packs bind far more than that
    @ModifyConstant(method = "<clinit>", constant = @Constant(intValue = 8, ordinal = 1))
    private static int lucerna$increaseTextureArray(int units) {
        return LUCERNA_TEXTURE_UNITS;
    }

    @ModifyConstant(method = "<clinit>", constant = @Constant(intValue = 8, ordinal = 3))
    private static int lucerna$increaseTextureLoop(int units) {
        return LUCERNA_TEXTURE_UNITS;
    }

    @Unique
    private static void lucerna$updateInputs() {
        WorldRenderingPipeline pipeline = Lucerna.getPipelineManager().getPipelineNullable();

        if (pipeline != null) {
            pipeline.setInputs(StateTracker.INSTANCE.getInputs());
        }
    }

    @Inject(method = "enableTexture", at = @At("HEAD"))
    private static void lucerna$onEnableTexture(CallbackInfo ci) {
        if (texture == LucernaSamplers.ALBEDO_TEXTURE_UNIT) {
            StateTracker.INSTANCE.albedoSampler = true;
        } else if (texture == LucernaSamplers.LIGHTMAP_TEXTURE_UNIT) {
            StateTracker.INSTANCE.lightmapSampler = true;
        } else {
            return;
        }

        lucerna$updateInputs();
    }

    @Inject(method = "disableTexture", at = @At("HEAD"))
    private static void lucerna$onDisableTexture(CallbackInfo ci) {
        if (texture == LucernaSamplers.ALBEDO_TEXTURE_UNIT) {
            StateTracker.INSTANCE.albedoSampler = false;
        } else if (texture == LucernaSamplers.LIGHTMAP_TEXTURE_UNIT) {
            StateTracker.INSTANCE.lightmapSampler = false;
        } else {
            return;
        }

        lucerna$updateInputs();
    }

    @Inject(method = "callList", at = @At("HEAD"))
    private static void lucerna$beforeCallList(int list, CallbackInfo ci) {
        WorldRenderingPipeline pipeline = Lucerna.getPipelineManager().getPipelineNullable();

        if (pipeline != null) {
            pipeline.syncProgram();
        }
    }

    @Inject(method = "bindTexture", at = @At("RETURN"))
    private static void lucerna$onBindTexture(int id, CallbackInfo ci) {
        TextureTracker.INSTANCE.onBindTexture(id);
    }

    @Inject(method = "deleteTextures", at = @At("RETURN"))
    private static void lucerna$onDeleteTexture(int id, CallbackInfo ci) {
        TextureTracker.INSTANCE.onDeleteTexture(id);
        TextureInfoCache.INSTANCE.onDeleteTexture(id);
    }

    @Inject(method = {"enableFog", "disableFog"}, at = @At("RETURN"))
    private static void lucerna$onFogToggle(CallbackInfo ci) {
        if (lucerna$fogToggleListener != null) {
            lucerna$fogToggleListener.run();
        }
    }

    @Inject(method = "fogMode", at = @At("RETURN"))
    private static void lucerna$onFogMode(int mode, CallbackInfo ci) {
        if (lucerna$fogModeListener != null) {
            lucerna$fogModeListener.run();
        }
    }

    @Inject(method = "fogDensity", at = @At("RETURN"))
    private static void lucerna$onFogDensity(float density, CallbackInfo ci) {
        if (lucerna$fogDensityListener != null) {
            lucerna$fogDensityListener.run();
        }
    }

    @Inject(method = "fogStart", at = @At("RETURN"))
    private static void lucerna$onFogStart(float start, CallbackInfo ci) {
        if (lucerna$fogStartListener != null) {
            lucerna$fogStartListener.run();
        }
    }

    @Inject(method = "fogEnd", at = @At("RETURN"))
    private static void lucerna$onFogEnd(float end, CallbackInfo ci) {
        if (lucerna$fogEndListener != null) {
            lucerna$fogEndListener.run();
        }
    }

    @Inject(method = "disableBlend", at = @At("HEAD"), cancellable = true)
    private static void lucerna$blendDisableLock(CallbackInfo ci) {
        if (BlendModeStorage.isBlendLocked()) {
            BlendModeStorage.deferBlendModeToggle(false);
            ci.cancel();
        }
    }

    @Inject(method = "enableBlend", at = @At("HEAD"), cancellable = true)
    private static void lucerna$blendEnableLock(CallbackInfo ci) {
        if (BlendModeStorage.isBlendLocked()) {
            BlendModeStorage.deferBlendModeToggle(true);
            ci.cancel();
        }
    }

    @Inject(method = "blendFunc", at = @At("HEAD"), cancellable = true)
    private static void lucerna$blendFuncLock(int srcFactor, int dstFactor, CallbackInfo ci) {
        if (BlendModeStorage.isBlendLocked()) {
            BlendModeStorage.deferBlendFunc(srcFactor, dstFactor, srcFactor, dstFactor);
            ci.cancel();
        }
    }

    @Inject(method = "blendFuncSeparate", at = @At("HEAD"), cancellable = true)
    private static void lucerna$blendFuncSeparateLock(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha, CallbackInfo ci) {
        if (BlendModeStorage.isBlendLocked()) {
            BlendModeStorage.deferBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
            ci.cancel();
        }
    }

    @Inject(method = {"blendFunc", "blendFuncSeparate"}, at = @At("RETURN"))
    private static void lucerna$onBlendFunc(CallbackInfo ci) {
        if (lucerna$blendFuncListener != null) {
            lucerna$blendFuncListener.run();
        }
    }

    @Inject(method = "disableAlphaTest", at = @At("HEAD"), cancellable = true)
    private static void lucerna$alphaTestDisableLock(CallbackInfo ci) {
        if (AlphaTestStorage.isAlphaTestLocked()) {
            AlphaTestStorage.deferAlphaTestToggle(false);
            ci.cancel();
        }
    }

    @Inject(method = "enableAlphaTest", at = @At("HEAD"), cancellable = true)
    private static void lucerna$alphaTestEnableLock(CallbackInfo ci) {
        if (AlphaTestStorage.isAlphaTestLocked()) {
            AlphaTestStorage.deferAlphaTestToggle(true);
            ci.cancel();
        }
    }

    @Inject(method = "alphaFunc", at = @At("HEAD"), cancellable = true)
    private static void lucerna$alphaFuncLock(int function, float reference, CallbackInfo ci) {
        if (AlphaTestStorage.isAlphaTestLocked()) {
            AlphaTestStorage.deferAlphaFunc(function, reference);
            ci.cancel();
        }
    }
}
