package us.z1x.lucerna.mixin.celeritas;

import org.embeddedt.embeddium.impl.render.chunk.multidraw.BatchAssembler;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import us.z1x.lucerna.pipeline.ShadowRenderer;

// faces are culled against the camera, which in the shadow pass is still the player, so the shadow pass picks its own faces
@Mixin(value = BatchAssembler.class, remap = false)
public class MixinShadowFaceMask {
    @Inject(method = "uniformCullMask", at = @At("HEAD"), cancellable = true)
    private static void lucerna$cullTowardLight(RenderRegion region, CameraTransform camera, CallbackInfoReturnable<Integer> cir) {
        if (ShadowRenderer.ACTIVE) {
            cir.setReturnValue(ShadowRenderer.faceMask);
        }
    }
}
