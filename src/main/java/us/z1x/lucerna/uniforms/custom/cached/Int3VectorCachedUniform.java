package us.z1x.lucerna.uniforms.custom.cached;

import org.joml.Vector3i;
import us.z1x.lucerna.gl.LucernaRenderSystem;
import us.z1x.lucerna.parsing.VectorType;

import java.util.function.Supplier;

public class Int3VectorCachedUniform extends VectorCachedUniform<Vector3i> {

    public Int3VectorCachedUniform(String name, Supplier<Vector3i> supplier) {
        super(name, new Vector3i(), supplier);
    }

    @Override
    protected void setFrom(Vector3i other) {
        this.cached.set(other);
    }

    @Override
    public void push(int location) {
        LucernaRenderSystem.uniform3i(location, this.cached.x, this.cached.y, this.cached.z);
    }

    @Override
    public VectorType getType() {
        return VectorType.I_VEC3;
    }
}
