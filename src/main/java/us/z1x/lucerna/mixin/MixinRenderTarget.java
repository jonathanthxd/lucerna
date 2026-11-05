package us.z1x.lucerna.mixin;

import net.minecraft.client.render.pipeline.RenderTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import us.z1x.lucerna.rendertarget.Blaze3dRenderTargetExt;

// lets FinalPassRenderer detect a recreated color texture and attach it again
@Mixin(RenderTarget.class)
public class MixinRenderTarget implements Blaze3dRenderTargetExt {
    @Unique
    private int lucerna$colorBufferVersion;

    @Inject(method = "destroyBuffers()V", at = @At("HEAD"))
    private void lucerna$onDestroyBuffers(CallbackInfo ci) {
        lucerna$colorBufferVersion++;
    }

    @Override
    public int lucerna$getColorBufferVersion() {
        return lucerna$colorBufferVersion;
    }
}
