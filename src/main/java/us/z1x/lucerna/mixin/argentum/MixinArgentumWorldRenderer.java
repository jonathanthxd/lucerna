package us.z1x.lucerna.mixin.argentum;

import dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.compat.celeritas.XHFPModelVertexType;

@Mixin(value = ArgentumWorldRenderer.class, remap = false)
public class MixinArgentumWorldRenderer {
    @ModifyVariable(method = "createRenderSectionManager", at = @At("STORE"))
    private ChunkVertexType lucerna$useExtendedVertexFormat(ChunkVertexType vertexType) {
        return Lucerna.isShaderPackActive() ? XHFPModelVertexType.INSTANCE : vertexType;
    }
}
