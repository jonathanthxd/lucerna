package us.z1x.lucerna.mixin;

import net.minecraft.client.render.platform.GLX;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import us.z1x.lucerna.gl.LucernaRenderSystem;

@Mixin(GLX.class)
public class MixinGLX {
    @Inject(method = "bindFramebuffer", at = @At("RETURN"))
    private static void lucerna$onBindFramebuffer(int target, int framebuffer, CallbackInfo ci) {
        LucernaRenderSystem.onFramebufferBound(target, framebuffer);
    }
}
