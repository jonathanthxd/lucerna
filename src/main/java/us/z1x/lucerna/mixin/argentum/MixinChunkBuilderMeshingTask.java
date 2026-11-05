package us.z1x.lucerna.mixin.argentum;

import com.llamalad7.mixinextras.sugar.Local;
import dev.rdh.argentum.impl.render.terrain.compile.task.ChunkBuilderMeshingTask;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.util.math.BlockPos;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildContext;
import org.embeddedt.embeddium.impl.util.task.CancellationToken;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.compat.celeritas.TerrainBlockContext;
import us.z1x.lucerna.compat.celeritas.TerrainContextRanges;

@Mixin(ChunkBuilderMeshingTask.class)
public class MixinChunkBuilderMeshingTask {
    @Inject(method = "execute", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;hasBlockEntity()Z", remap = true))
    private void lucerna$beginBlock(ChunkBuildContext context, CancellationToken cancellationToken, CallbackInfoReturnable<?> cir,
                                    @Local(name = "blockState") BlockState blockState, @Local(name = "blockPos") BlockPos.Mutable blockPos) {
        if (Lucerna.isShaderPackActive()) {
            TerrainBlockContext.get().set(TerrainBlockContext.packId(blockState), blockPos.getX(), blockPos.getY(), blockPos.getZ());
        }
    }

    @Inject(method = "execute", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/BlockRenderDispatcher;render(Lnet/minecraft/block/state/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/WorldView;Lnet/minecraft/client/render/vertex/BufferBuilder;)Z", remap = true))
    private void lucerna$beginVanillaBlock(ChunkBuildContext context, CancellationToken cancellationToken, CallbackInfoReturnable<?> cir,
                                           @Local(name = "blockState") BlockState blockState, @Local(name = "blockPos") BlockPos.Mutable blockPos,
                                           @Local(name = "pass") BlockLayer pass) {
        if (Lucerna.isShaderPackActive()) {
            ((TerrainContextRanges) context).lucerna$recordBlock(pass, TerrainBlockContext.packId(blockState), blockPos);
        }
    }
}
