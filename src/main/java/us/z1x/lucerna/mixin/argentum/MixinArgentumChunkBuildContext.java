package us.z1x.lucerna.mixin.argentum;

import com.llamalad7.mixinextras.sugar.Local;
import dev.rdh.argentum.impl.render.terrain.compile.ArgentumChunkBuildContext;
import dev.rdh.argentum.impl.world.cloned.ChunkRenderContext;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.util.math.BlockPos;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.compat.celeritas.TerrainBlockContext;
import us.z1x.lucerna.compat.celeritas.TerrainContextRanges;

@Mixin(ArgentumChunkBuildContext.class)
public abstract class MixinArgentumChunkBuildContext implements TerrainContextRanges {
    @Unique
    private final IntArrayList[] lucerna$ranges = new IntArrayList[BlockLayer.values().length];

    @Unique
    private IntArrayList lucerna$activeRanges;

    @Unique
    private int lucerna$activeIndex;

    @Shadow(remap = false)
    public abstract BufferBuilder getBuffer(BlockLayer layer);

    @Override
    public void lucerna$recordBlock(BlockLayer layer, int packedId, BlockPos pos) {
        IntArrayList ranges = lucerna$ranges[layer.ordinal()];

        if (ranges == null) {
            ranges = lucerna$ranges[layer.ordinal()] = new IntArrayList();
        }

        ranges.add(getBuffer(layer).getVertexCount());
        ranges.add(packedId);
        ranges.add((pos.getX() & 15) | (pos.getY() & 15) << 4 | (pos.getZ() & 15) << 8);
    }

    @Inject(method = "renderFluid", remap = false, at = @At("HEAD"))
    private void lucerna$beginFluid(BlockState state, BlockPos pos, ChunkRenderContext world, BlockLayer layer, CallbackInfo ci) {
        if (Lucerna.isShaderPackActive()) {
            lucerna$recordBlock(layer, TerrainBlockContext.packId(state), pos);
        }
    }

    @Inject(method = "finishSection", remap = false, at = @At(value = "INVOKE", target = "Ldev/rdh/argentum/impl/render/terrain/compile/ArgentumChunkBuildContext;copyRawBuffer(Ljava/nio/IntBuffer;ILorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildBuffers;Lorg/embeddedt/embeddium/impl/render/chunk/terrain/material/Material;)V"))
    private void lucerna$beginLayer(ChunkBuildBuffers buffers, CallbackInfo ci, @Local(name = "i") int layer) {
        lucerna$activeRanges = lucerna$ranges[layer];
        lucerna$activeIndex = 0;
    }

    @Inject(method = "finishSection", remap = false, at = @At("RETURN"))
    private void lucerna$endSection(ChunkBuildBuffers buffers, CallbackInfo ci) {
        lucerna$activeRanges = null;

        for (IntArrayList ranges : lucerna$ranges) {
            if (ranges != null) {
                ranges.clear();
            }
        }
    }

    @Inject(method = "outputQuads", remap = false, at = @At(value = "INVOKE", target = "Lorg/embeddedt/embeddium/impl/render/chunk/vertex/builder/ChunkMeshBufferBuilder;push([Lorg/embeddedt/embeddium/impl/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;Lorg/embeddedt/embeddium/impl/render/chunk/terrain/material/Material;)V"))
    private void lucerna$beginQuad(CallbackInfo ci, @Local(name = "quadIdx") int quadIdx) {
        IntArrayList ranges = lucerna$activeRanges;

        if (ranges == null || ranges.isEmpty()) {
            return;
        }

        int vertex = quadIdx * 4;
        int index = lucerna$activeIndex;

        while (index + 3 < ranges.size() && ranges.getInt(index + 3) <= vertex) {
            index += 3;
        }

        lucerna$activeIndex = index;

        if (ranges.getInt(index) <= vertex) {
            int pos = ranges.getInt(index + 2);
            TerrainBlockContext.get().set(ranges.getInt(index + 1), pos, pos >> 4, pos >> 8);
        }
    }
}
