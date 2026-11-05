package us.z1x.lucerna.compat.celeritas;

import org.lwjgl.system.MemoryUtil;
import us.z1x.lucerna.vertices.views.QuadView;

public final class QuadViewTerrain implements QuadView {
    private long writePointer;
    private int stride;

    public void setup(long writePointer, int stride) {
        this.writePointer = writePointer;
        this.stride = stride;
    }

    @Override
    public float x(int index) {
        return MemoryUtil.memGetFloat(writePointer - (long) stride * (3 - index));
    }

    @Override
    public float y(int index) {
        return MemoryUtil.memGetFloat(writePointer + 4 - (long) stride * (3 - index));
    }

    @Override
    public float z(int index) {
        return MemoryUtil.memGetFloat(writePointer + 8 - (long) stride * (3 - index));
    }

    @Override
    public float u(int index) {
        return MemoryUtil.memGetFloat(writePointer + 16 - (long) stride * (3 - index));
    }

    @Override
    public float v(int index) {
        return MemoryUtil.memGetFloat(writePointer + 20 - (long) stride * (3 - index));
    }
}
