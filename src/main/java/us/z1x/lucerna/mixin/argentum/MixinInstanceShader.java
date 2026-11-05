package us.z1x.lucerna.mixin.argentum;

import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import us.z1x.lucerna.compat.argentum.InstancedEntityPrograms;

@Mixin(targets = "dev.rdh.argentum.impl.render.entity.instancing.InstanceShader", remap = false)
public class MixinInstanceShader {
    @Inject(method = "setGlintPass", at = @At("RETURN"))
    private void lucerna$trackGlintPass(int pass, CallbackInfo ci) {
        InstancedEntityPrograms.setGlintPass(pass);
    }

    @Inject(method = "setItemGlintPass(I)V", at = @At("RETURN"))
    private void lucerna$trackItemGlintPass(int pass, CallbackInfo ci) {
        InstancedEntityPrograms.setItemGlintPass(pass);
    }

    @Inject(method = "setItemGlintPass(I[Lorg/joml/Matrix4fc;)V", at = @At("RETURN"))
    private void lucerna$trackItemGlintPass(int pass, Matrix4fc[] matrices, CallbackInfo ci) {
        InstancedEntityPrograms.setItemGlintPass(pass);
    }

    @Inject(method = "setChargePass", at = @At("RETURN"))
    private void lucerna$trackChargePass(int pass, CallbackInfo ci) {
        InstancedEntityPrograms.setChargePass(pass);
    }
}
