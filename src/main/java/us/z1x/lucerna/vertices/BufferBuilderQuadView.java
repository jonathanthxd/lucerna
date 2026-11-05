package us.z1x.lucerna.vertices;

import org.lwjgl.system.MemoryUtil;
import us.z1x.lucerna.vertices.views.QuadView;

public final class BufferBuilderQuadView implements QuadView {
    private long address;
    private int stride;
    private int uvOffset;

    public void setup(long address, int stride, int uvOffset) {
        this.address = address;
        this.stride = stride;
        this.uvOffset = uvOffset;
    }

    @Override
    public float x(int index) {
        return MemoryUtil.memGetFloat(address + (long) stride * index);
    }

    @Override
    public float y(int index) {
        return MemoryUtil.memGetFloat(address + (long) stride * index + 4);
    }

    @Override
    public float z(int index) {
        return MemoryUtil.memGetFloat(address + (long) stride * index + 8);
    }

    @Override
    public float u(int index) {
        return MemoryUtil.memGetFloat(address + (long) stride * index + uvOffset);
    }

    @Override
    public float v(int index) {
        return MemoryUtil.memGetFloat(address + (long) stride * index + uvOffset + 4);
    }
}
