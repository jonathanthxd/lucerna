package us.z1x.lucerna.mixin.argentum;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.pipeline.WorldRenderingPipeline;

// the query boxes have no texture or color, so pack programs can discard them and hide every entity behind nothing
@Mixin(targets = "dev.rdh.argentum.impl.render.entity.EntityOcclusionCuller", remap = false)
public class MixinEntityOcclusionCuller {
    @Inject(method = "beginQueryPass", at = @At("HEAD"))
    private static void lucerna$useFixedFunction(CallbackInfo ci) {
        WorldRenderingPipeline pipeline = Lucerna.getPipelineManager().getPipelineNullable();

        if (pipeline != null) {
            pipeline.getRenderTargetStateListener().beginPostChain();
        }
    }

    @Inject(method = "endQueryPass", at = @At("TAIL"))
    private static void lucerna$restorePipeline(CallbackInfo ci) {
        WorldRenderingPipeline pipeline = Lucerna.getPipelineManager().getPipelineNullable();

        if (pipeline != null) {
            pipeline.getRenderTargetStateListener().endPostChain();
        }
    }
}
