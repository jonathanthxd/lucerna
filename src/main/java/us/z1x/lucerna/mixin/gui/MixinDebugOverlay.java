package us.z1x.lucerna.mixin.gui;

import net.minecraft.client.gui.overlay.DebugOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.pipeline.WorldRenderingPipeline;

import java.util.List;

@Mixin(DebugOverlay.class)
public class MixinDebugOverlay {
    @Inject(method = "getSystemInfo", at = @At("RETURN"))
    private void lucerna$addPipelineInfo(CallbackInfoReturnable<List<String>> cir) {
        WorldRenderingPipeline pipeline = Lucerna.getPipelineManager().getPipelineNullable();

        if (pipeline != null) {
            pipeline.addDebugText(cir.getReturnValue());
        }
    }
}
