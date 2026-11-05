package us.z1x.lucerna.mixin;

import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.BufferUploader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.pipeline.WorldRenderingPipeline;
import us.z1x.lucerna.uniforms.CapturedRenderingState;
import us.z1x.lucerna.vertices.VanillaVertexAttributes;

@Mixin(BufferUploader.class)
public class MixinBufferUploader {
    @Inject(method = "end", at = @At("HEAD"))
    private void lucerna$beforeDraw(BufferBuilder builder, CallbackInfo ci) {
        WorldRenderingPipeline pipeline = Lucerna.getPipelineManager().getPipelineNullable();

        if (pipeline == null) {
            return;
        }

        if (CapturedRenderingState.INSTANCE.isCompilingDisplayList()) {
            // glUseProgram would be recorded into the display list, and the list can be replayed in any pass
            if (Lucerna.isShaderPipelineActive()) {
                VanillaVertexAttributes.bind(builder);
            }
        } else {
            pipeline.syncProgram();

            if (pipeline.usesExtendedVertexAttributes()) {
                VanillaVertexAttributes.bind(builder);
            }
        }
    }

    @Inject(method = "end", at = @At("RETURN"))
    private void lucerna$afterDraw(BufferBuilder builder, CallbackInfo ci) {
        VanillaVertexAttributes.unbind();
    }
}
