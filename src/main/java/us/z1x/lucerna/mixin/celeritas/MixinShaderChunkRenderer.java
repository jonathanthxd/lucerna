package us.z1x.lucerna.mixin.celeritas;

import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.render.chunk.ShaderChunkRenderer;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import us.z1x.lucerna.compat.celeritas.LucernaChunkProgramOverrides;

@Mixin(value = ShaderChunkRenderer.class, remap = false)
public class MixinShaderChunkRenderer {
    @Unique
    private final LucernaChunkProgramOverrides lucerna$overrides = new LucernaChunkProgramOverrides();

    @Inject(method = "compileProgram", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("unchecked")
    private void lucerna$useOverride(ChunkShaderOptions options, CallbackInfoReturnable<GlProgram<ChunkShaderInterface>> cir) {
        var override = lucerna$overrides.getProgramOverride(options.pass());

        if (override != null) {
            cir.setReturnValue((GlProgram<ChunkShaderInterface>) (GlProgram<?>) override);
        }
    }

    @Inject(method = "delete", at = @At("HEAD"))
    private void lucerna$onDelete(CommandList commandList, CallbackInfo ci) {
        lucerna$overrides.deleteShaders();
    }
}
