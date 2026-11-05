package us.z1x.lucerna.uniforms.custom.cached;

import org.joml.Vector3f;
import us.z1x.lucerna.gl.LucernaRenderSystem;
import us.z1x.lucerna.parsing.VectorType;

import java.util.function.Supplier;

public class Float3VectorCachedUniform extends VectorCachedUniform<Vector3f> {

    public Float3VectorCachedUniform(String name, Supplier<Vector3f> supplier) {
        super(name, new Vector3f(), supplier);
    }

    @Override
    protected void setFrom(Vector3f other) {
        this.cached.set(other);
    }

    @Override
    public void push(int location) {
        LucernaRenderSystem.uniform3f(location, this.cached.x, this.cached.y, this.cached.z);
    }

    @Override
    public VectorType getType() {
        return VectorType.VEC3;
    }
}
