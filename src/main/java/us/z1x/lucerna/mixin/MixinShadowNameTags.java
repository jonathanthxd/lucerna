package us.z1x.lucerna.mixin;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import us.z1x.lucerna.pipeline.ShadowRenderer;

// name tags never cast shadows, and drawing their text into the shadow map is most of the shadow pass cost
@Mixin({EntityRenderer.class, LivingEntityRenderer.class})
public class MixinShadowNameTags {
    @Inject(method = "renderNameTag", at = @At("HEAD"), cancellable = true)
    private void lucerna$skipNameTagInShadowPass(CallbackInfo ci) {
        if (ShadowRenderer.ACTIVE) {
            ci.cancel();
        }
    }
}
