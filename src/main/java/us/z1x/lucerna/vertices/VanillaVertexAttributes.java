package us.z1x.lucerna.vertices;

import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.VertexFormat;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.system.MemoryUtil;
import us.z1x.lucerna.gl.LucernaRenderSystem;

import java.nio.ByteBuffer;

// vanilla vertex formats have no room for mc_midTexCoord and at_tangent, so supply them as client side arrays
public final class VanillaVertexAttributes {
    // mc_midTexCoord as two floats, then at_tangent as four normalized bytes
    private static final int STRIDE = 12;
    // locations bound in ProgramCreator
    private static final int MID_TEX_COORD_LOCATION = 12;
    private static final int TANGENT_LOCATION = 13;
    private static final BufferBuilderQuadView QUAD = new BufferBuilderQuadView();
    private static final Vector3f NORMAL = new Vector3f();

    private static ByteBuffer buffer = MemoryUtil.memAlloc(STRIDE * 4096);
    private static boolean bound;

    private VanillaVertexAttributes() {
    }

    public static void bind(BufferBuilder builder) {
        VertexFormat format = builder.getFormat();

        if (builder.getDrawMode() != GL11C.GL_QUADS || builder.getVertexCount() < 4 || !format.hasUv(0)) {
            return;
        }

        int stride = format.getVertexSize();
        int uvOffset = format.getUvOffset(0);
        int vertices = builder.getVertexCount() & ~3;

        if (buffer.capacity() < vertices * STRIDE) {
            buffer = MemoryUtil.memRealloc(buffer, vertices * STRIDE * 2);
        }

        long source = MemoryUtil.memAddress(builder.getBuffer(), 0);
        long target = MemoryUtil.memAddress(buffer, 0);

        for (int quad = 0; quad < vertices; quad += 4) {
            long address = source + (long) quad * stride;
            QUAD.setup(address, stride, uvOffset);
            NormalHelper.computeFaceNormal(NORMAL, QUAD);
            int tangent = NormalHelper.computeTangent(NORMAL.x, NORMAL.y, NORMAL.z, QUAD);
            float midU = (QUAD.u(0) + QUAD.u(1) + QUAD.u(2) + QUAD.u(3)) * 0.25F;
            float midV = (QUAD.v(0) + QUAD.v(1) + QUAD.v(2) + QUAD.v(3)) * 0.25F;

            for (int vertex = 0; vertex < 4; vertex++) {
                long pointer = target + (long) (quad + vertex) * STRIDE;
                MemoryUtil.memPutFloat(pointer, midU);
                MemoryUtil.memPutFloat(pointer + 4, midV);
                MemoryUtil.memPutInt(pointer + 8, tangent);
            }
        }

        GL20C.nglVertexAttribPointer(MID_TEX_COORD_LOCATION, 2, GL11C.GL_FLOAT, false, STRIDE, target);
        GL20C.nglVertexAttribPointer(TANGENT_LOCATION, 4, GL11C.GL_BYTE, true, STRIDE, target + 8);
        GL20C.glEnableVertexAttribArray(MID_TEX_COORD_LOCATION);
        GL20C.glEnableVertexAttribArray(TANGENT_LOCATION);
        bound = true;
    }

    public static void unbind() {
        if (!bound) {
            return;
        }

        bound = false;
        GL20C.glDisableVertexAttribArray(MID_TEX_COORD_LOCATION);
        GL20C.glDisableVertexAttribArray(TANGENT_LOCATION);
        // the current value of an attribute is undefined after an array draw
        LucernaRenderSystem.vertexAttrib4f(MID_TEX_COORD_LOCATION, 0.0F, 0.0F, 0.0F, 1.0F);
        LucernaRenderSystem.vertexAttrib4f(TANGENT_LOCATION, 1.0F, 0.0F, 0.0F, 1.0F);
    }
}
