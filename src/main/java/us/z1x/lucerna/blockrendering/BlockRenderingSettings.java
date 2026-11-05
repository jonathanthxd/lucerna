package us.z1x.lucerna.blockrendering;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.render.block.BlockLayer;
import org.jetbrains.annotations.Nullable;
import us.z1x.lucerna.shaderpack.materialmap.NamespacedId;

import java.util.Map;

public class BlockRenderingSettings {
    public static final BlockRenderingSettings INSTANCE = new BlockRenderingSettings();

    private boolean reloadRequired;
    private Object2IntMap<BlockState> blockStateIds;
    private Map<Block, BlockLayer> blockTypeIds;
    private Object2IntFunction<NamespacedId> entityIds;
    private Object2IntFunction<NamespacedId> itemIds;
    private float ambientOcclusionLevel;
    private boolean disableDirectionalShading;
    private boolean useSeparateAo;
    private boolean useExtendedVertexFormat;

    public BlockRenderingSettings() {
        reloadRequired = false;
        blockStateIds = null;
        blockTypeIds = null;
        ambientOcclusionLevel = 1.0F;
        disableDirectionalShading = false;
        useSeparateAo = false;
        useExtendedVertexFormat = false;
    }

    public boolean isReloadRequired() {
        return reloadRequired;
    }

    public void clearReloadRequired() {
        reloadRequired = false;
    }

    @Nullable
    public Object2IntMap<BlockState> getBlockStateIds() {
        return blockStateIds;
    }

    @Nullable
    public Object2IntFunction<NamespacedId> getEntityIds() {
        return entityIds;
    }

    public void setBlockStateIds(Object2IntMap<BlockState> blockStateIds) {
        if (this.blockStateIds != null && this.blockStateIds.equals(blockStateIds)) {
            return;
        }

        this.reloadRequired = true;
        this.blockStateIds = blockStateIds;
    }

    public void setBlockTypeIds(Map<Block, BlockLayer> blockTypeIds) {
        if (this.blockTypeIds != null && this.blockTypeIds.equals(blockTypeIds)) {
            return;
        }

        this.reloadRequired = true;
        this.blockTypeIds = blockTypeIds;
    }

    @Nullable
    public Object2IntFunction<NamespacedId> getItemIds() {
        return itemIds;
    }

    public void setItemIds(Object2IntFunction<NamespacedId> itemIds) {
        this.itemIds = itemIds;
    }

    public void setEntityIds(Object2IntFunction<NamespacedId> entityIds) {
        // no reload needed, entities are rebuilt every frame
        this.entityIds = entityIds;
    }

    public float getAmbientOcclusionLevel() {
        return ambientOcclusionLevel;
    }

    public void setAmbientOcclusionLevel(float ambientOcclusionLevel) {
        if (ambientOcclusionLevel == this.ambientOcclusionLevel) {
            return;
        }

        this.reloadRequired = true;
        this.ambientOcclusionLevel = ambientOcclusionLevel;
    }

    public boolean shouldDisableDirectionalShading() {
        return disableDirectionalShading;
    }

    public void setDisableDirectionalShading(boolean disableDirectionalShading) {
        if (disableDirectionalShading == this.disableDirectionalShading) {
            return;
        }

        this.reloadRequired = true;
        this.disableDirectionalShading = disableDirectionalShading;
    }

    public boolean shouldUseSeparateAo() {
        return useSeparateAo;
    }

    public void setUseSeparateAo(boolean useSeparateAo) {
        if (useSeparateAo == this.useSeparateAo) {
            return;
        }

        this.reloadRequired = true;
        this.useSeparateAo = useSeparateAo;
    }

    public void setUseExtendedVertexFormat(boolean useExtendedVertexFormat) {
        if (useExtendedVertexFormat == this.useExtendedVertexFormat) {
            return;
        }

        this.reloadRequired = true;
        this.useExtendedVertexFormat = useExtendedVertexFormat;
    }
}
