package us.z1x.lucerna.uniforms.custom.cached;

import org.joml.Vector2f;
import us.z1x.lucerna.gl.LucernaRenderSystem;
import us.z1x.lucerna.parsing.VectorType;

import java.util.function.Supplier;

public class Float2VectorCachedUniform extends VectorCachedUniform<Vector2f> {

    public Float2VectorCachedUniform(String name, Supplier<Vector2f> supplier) {
        super(name, new Vector2f(), supplier);
    }

    @Override
    protected void setFrom(Vector2f other) {
        this.cached.set(other);
    }

    @Override
    public void push(int location) {
        LucernaRenderSystem.uniform2f(location, this.cached.x, this.cached.y);
    }

    @Override
    public VectorType getType() {
        return VectorType.VEC2;
    }
}
