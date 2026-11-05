package us.z1x.lucerna.compat.celeritas;

import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;
import us.z1x.lucerna.vertices.ExtendedDataHelper;
import us.z1x.lucerna.vertices.NormI8;
import us.z1x.lucerna.vertices.NormalHelper;

import static us.z1x.lucerna.compat.celeritas.XHFPModelVertexType.STRIDE;

public class XHFPTerrainVertex implements ChunkVertexEncoder {
    private static final int MID_TEX_OFFSET = 32;
    private static final int TANGENT_OFFSET = 36;
    private static final int NORMAL_OFFSET = 40;
    private static final int MC_ENTITY_OFFSET = 44;
    private static final int MID_BLOCK_OFFSET = 48;

    private final QuadViewTerrain quad = new QuadViewTerrain();
    private final Vector3f normal = new Vector3f();

    private TerrainBlockContext context;
    private int vertexCount;
    private float uSum;
    private float vSum;

    @Override
    public boolean supportsBilinearCorrection() {
        return false;
    }

    @Override
    public long write(long ptr, Material material, Vertex vertex, int chunkId) {
        if (vertexCount == 0) {
            context = TerrainBlockContext.get();
        }

        uSum += vertex.u;
        vSum += vertex.v;
        vertexCount++;

        MemoryUtil.memPutFloat(ptr, vertex.x);
        MemoryUtil.memPutFloat(ptr + 4, vertex.y);
        MemoryUtil.memPutFloat(ptr + 8, vertex.z);
        MemoryUtil.memPutInt(ptr + 12, vertex.color);
        MemoryUtil.memPutFloat(ptr + 16, Math.min(0.99999997F, vertex.u));
        MemoryUtil.memPutFloat(ptr + 20, Math.min(0.99999997F, vertex.v));
        MemoryUtil.memPutShort(ptr + 24, (short) (vertex.light & 0xFF));
        MemoryUtil.memPutShort(ptr + 26, (short) ((vertex.light >> 16) & 0xFF));
        MemoryUtil.memPutInt(ptr + 28, (chunkId & 0xFF) | ((material.bits() & 0xFF) << 8));

        MemoryUtil.memPutShort(ptr + MC_ENTITY_OFFSET + 2, context.renderType);
        MemoryUtil.memPutInt(ptr + MID_BLOCK_OFFSET, ExtendedDataHelper.computeMidBlock(vertex.x, vertex.y, vertex.z, context.localPosX, context.localPosY, context.localPosZ));
        MemoryUtil.memPutByte(ptr + MID_BLOCK_OFFSET + 3, context.lightValue);

        if (vertexCount == 4) {
            vertexCount = 0;

            int midUV = XHFPModelVertexType.encodeTexture(uSum * 0.25f, vSum * 0.25f);
            short blockId = context.blockId;

            uSum = 0;
            vSum = 0;

            // https://github.com/IrisShaders/ShaderDoc/blob/master/vertex-format-extensions.md#surface-normal-vector
            quad.setup(ptr, STRIDE);
            NormalHelper.computeFaceNormal(normal, quad);
            int packedNormal = NormI8.pack(normal);
            int tangent = NormalHelper.computeTangent(normal.x, normal.y, normal.z, quad);

            for (int i = 0; i < 4; i++) {
                long vertexPtr = ptr - (long) STRIDE * i;
                MemoryUtil.memPutInt(vertexPtr + MID_TEX_OFFSET, midUV);
                MemoryUtil.memPutShort(vertexPtr + MC_ENTITY_OFFSET, blockId);
                MemoryUtil.memPutInt(vertexPtr + NORMAL_OFFSET, packedNormal);
                MemoryUtil.memPutInt(vertexPtr + TANGENT_OFFSET, tangent);
            }
        }

        return ptr + STRIDE;
    }
}
