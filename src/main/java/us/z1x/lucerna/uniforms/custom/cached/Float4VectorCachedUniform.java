package us.z1x.lucerna.uniforms.custom.cached;

import org.joml.Vector4f;
import us.z1x.lucerna.gl.LucernaRenderSystem;
import us.z1x.lucerna.parsing.VectorType;

import java.util.function.Supplier;

public class Float4VectorCachedUniform extends VectorCachedUniform<Vector4f> {

    public Float4VectorCachedUniform(String name, Supplier<Vector4f> supplier) {
        super(name, new Vector4f(), supplier);
    }

    @Override
    protected void setFrom(Vector4f other) {
        this.cached.set(other);
    }

    @Override
    public void push(int location) {
        LucernaRenderSystem.uniform4f(location, this.cached.x, this.cached.y, this.cached.z, this.cached.w);
    }

    @Override
    public VectorType getType() {
        return VectorType.VEC4;
    }
}
