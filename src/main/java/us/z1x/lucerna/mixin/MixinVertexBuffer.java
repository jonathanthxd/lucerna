package us.z1x.lucerna.mixin;

import net.minecraft.client.render.vertex.VertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.pipeline.WorldRenderingPipeline;

@Mixin(VertexBuffer.class)
public class MixinVertexBuffer {
    @Inject(method = "draw", at = @At("HEAD"))
    private void lucerna$beforeDraw(int mode, CallbackInfo ci) {
        WorldRenderingPipeline pipeline = Lucerna.getPipelineManager().getPipelineNullable();

        if (pipeline != null) {
            pipeline.syncProgram();
        }
    }
}
