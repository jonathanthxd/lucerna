package us.z1x.lucerna.mixin;

import net.minecraft.client.render.model.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import us.z1x.lucerna.uniforms.CapturedRenderingState;

@Mixin(ModelPart.class)
public class MixinModelPart {
    @Inject(method = "compile", at = @At("HEAD"))
    private void lucerna$beginCompile(float scale, CallbackInfo ci) {
        CapturedRenderingState.INSTANCE.setCompilingDisplayList(true);
    }

    @Inject(method = "compile", at = @At("RETURN"))
    private void lucerna$endCompile(float scale, CallbackInfo ci) {
        CapturedRenderingState.INSTANCE.setCompilingDisplayList(false);
    }
}
