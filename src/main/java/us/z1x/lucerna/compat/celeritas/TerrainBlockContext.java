package us.z1x.lucerna.compat.celeritas;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import us.z1x.lucerna.blockrendering.BlockRenderingSettings;
import us.z1x.lucerna.vertices.ExtendedDataHelper;

// the block that the chunk mesher on this thread emits quads for, read by XHFPTerrainVertex
public final class TerrainBlockContext {
    private static final ThreadLocal<TerrainBlockContext> CURRENT = ThreadLocal.withInitial(TerrainBlockContext::new);

    public short blockId = -1;
    public short renderType = -1;
    public byte lightValue;
    public int localPosX;
    public int localPosY;
    public int localPosZ;

    public static TerrainBlockContext get() {
        return CURRENT.get();
    }

    public static int packId(BlockState state) {
        Object2IntMap<BlockState> ids = BlockRenderingSettings.INSTANCE.getBlockStateIds();
        int id = ids == null ? -1 : ids.getInt(state);

        // legacy shader packs read the raw block id when the block is not listed in block.properties
        if (id == -1) {
            id = Block.getId(state.getBlock());
        }

        Block block = state.getBlock();
        short renderType = block.getRenderType() == 1 ? ExtendedDataHelper.FLUID_RENDER_TYPE : (short) block.getRenderType();

        return (id & 0xFFFF) | (renderType << 16) | ((block.getLight() & 0xFF) << 24);
    }

    public void set(int packed, int x, int y, int z) {
        this.blockId = (short) packed;
        this.renderType = (short) (byte) (packed >> 16);
        this.lightValue = (byte) (packed >>> 24);
        this.localPosX = x & 15;
        this.localPosY = y & 15;
        this.localPosZ = z & 15;
    }
}
