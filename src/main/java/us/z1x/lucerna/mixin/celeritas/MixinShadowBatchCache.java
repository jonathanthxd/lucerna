package us.z1x.lucerna.mixin.celeritas;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.render.chunk.DefaultChunkRenderer;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.BatchAssembler;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.CachedBatch;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import us.z1x.lucerna.pipeline.ShadowRenderer;

import java.util.Map;
import java.util.WeakHashMap;

@Mixin(value = DefaultChunkRenderer.class, remap = false)
public class MixinShadowBatchCache {
    // the face mask each shadow batch was built with, it changes when the light crosses an axis
    @Unique
    private static final Map<CachedBatch, Integer> LUCERNA_SHADOW_MASKS = new WeakHashMap<>();

    @Inject(method = "render", at = @At("HEAD"))
    private void lucerna$beginShadowPass(CallbackInfo ci, @Local(argsOnly = true) TerrainRenderPass pass) {
        if (ShadowRenderer.ACTIVE) {
            ShadowRenderer.beginTerrainPass(pass.isSorted());
        }
    }

    // regions cache two batches per storage keyed by the face culling flag, so sharing those keys rebuilt both passes every frame
    // the shadow pass keys by the pass's sorting instead, which never matches the main pass key in one storage
    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lorg/embeddedt/embeddium/impl/render/chunk/data/SectionRenderDataStorage$BatchCacheParams;<init>(Z)V"))
    private boolean lucerna$separateShadowBatches(boolean useBlockFaceCulling, @Local(argsOnly = true) TerrainRenderPass pass) {
        return ShadowRenderer.ACTIVE ? pass.isSorted() : useBlockFaceCulling;
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lorg/embeddedt/embeddium/impl/render/chunk/data/SectionRenderDataStorage;getCachedMultiDrawBatch(Lorg/embeddedt/embeddium/impl/render/chunk/data/SectionRenderDataStorage$BatchCacheParams;)Lorg/embeddedt/embeddium/impl/render/chunk/multidraw/CachedBatch;"))
    private CachedBatch lucerna$dropStaleShadowBatch(SectionRenderDataStorage storage, SectionRenderDataStorage.BatchCacheParams params, Operation<CachedBatch> original) {
        CachedBatch cached = original.call(storage, params);
        return cached != null && ShadowRenderer.ACTIVE && !LUCERNA_SHADOW_MASKS.getOrDefault(cached, -1).equals(ShadowRenderer.faceMask) ? null : cached;
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lorg/embeddedt/embeddium/impl/render/chunk/multidraw/BatchAssembler;createCachedBatch(Lorg/embeddedt/embeddium/impl/render/chunk/region/RenderRegion;Lorg/embeddedt/embeddium/impl/render/chunk/data/SectionRenderDataStorage;Lorg/embeddedt/embeddium/impl/render/chunk/lists/ChunkRenderList;Lorg/embeddedt/embeddium/impl/render/viewport/CameraTransform;Lorg/embeddedt/embeddium/impl/render/chunk/terrain/TerrainRenderPass;ZLorg/embeddedt/embeddium/impl/gl/device/CommandList;Lorg/embeddedt/embeddium/impl/render/chunk/multidraw/BatchAssembler$TessellationProvider;)Lorg/embeddedt/embeddium/impl/render/chunk/multidraw/CachedBatch;"))
    private CachedBatch lucerna$recordShadowMask(RenderRegion region, SectionRenderDataStorage storage, ChunkRenderList list, CameraTransform camera,
                                                 TerrainRenderPass pass, boolean useBlockFaceCulling, CommandList commandList,
                                                 BatchAssembler.TessellationProvider tessellation, Operation<CachedBatch> original) {
        CachedBatch batch = original.call(region, storage, list, camera, pass, useBlockFaceCulling, commandList, tessellation);

        if (ShadowRenderer.ACTIVE) {
            LUCERNA_SHADOW_MASKS.put(batch, ShadowRenderer.faceMask);
        }

        return batch;
    }
}
