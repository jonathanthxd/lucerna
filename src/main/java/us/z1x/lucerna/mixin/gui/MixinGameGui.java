package us.z1x.lucerna.mixin.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GameGui;
import net.minecraft.client.render.Window;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.gui.screen.HudHideable;
import us.z1x.lucerna.pipeline.WorldRenderingPipeline;

@Mixin(GameGui.class)
public class MixinGameGui {
    @Shadow
    @Final
    private Minecraft minecraft;

    // cancel after the gui projection is set up, since screens rely on it when in a world
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;setupGuiState()V", shift = At.Shift.AFTER), cancellable = true)
    private void lucerna$handleHudHidingScreens(float tickDelta, CallbackInfo ci) {
        if (this.minecraft.screen instanceof HudHideable) {
            ci.cancel();
        }
    }

    @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
    private void lucerna$disableVignette(float brightnessAtEyes, Window window, CallbackInfo ci) {
        WorldRenderingPipeline pipeline = Lucerna.getPipelineManager().getPipelineNullable();

        if (pipeline != null && !pipeline.shouldRenderVignette()) {
            ci.cancel();
        }
    }
}
