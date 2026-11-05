package us.z1x.lucerna.uniforms.custom.cached;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import us.z1x.lucerna.gl.LucernaRenderSystem;
import us.z1x.lucerna.parsing.MatrixType;

import java.nio.FloatBuffer;
import java.util.function.Supplier;

public class Float4MatrixCachedUniform extends VectorCachedUniform<Matrix4f> {
    final private FloatBuffer buffer = BufferUtils.createFloatBuffer(16);

    public Float4MatrixCachedUniform(String name, Supplier<Matrix4f> supplier) {
        super(name, new Matrix4f(), supplier);
    }

    @Override
    protected void setFrom(Matrix4f other) {
        this.cached.set(other);
    }

    @Override
    public void push(int location) {
        this.cached.get(buffer);
        LucernaRenderSystem.uniformMatrix4fv(location, false, buffer);
    }

    @Override
    public MatrixType<Matrix4f> getType() {
        return MatrixType.MAT4;
    }
}
