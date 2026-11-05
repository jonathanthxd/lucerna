package us.z1x.lucerna.uniforms.custom.cached;

import org.joml.Vector2i;
import us.z1x.lucerna.gl.LucernaRenderSystem;
import us.z1x.lucerna.parsing.VectorType;

import java.util.function.Supplier;

public class Int2VectorCachedUniform extends VectorCachedUniform<Vector2i> {

    public Int2VectorCachedUniform(String name, Supplier<Vector2i> supplier) {
        super(name, new Vector2i(), supplier);
    }

    @Override
    protected void setFrom(Vector2i other) {
        this.cached.set(other);
    }

    @Override
    public void push(int location) {
        LucernaRenderSystem.uniform2i(location, this.cached.x, this.cached.y);
    }

    @Override
    public VectorType getType() {
        return VectorType.I_VEC2;
    }
}
