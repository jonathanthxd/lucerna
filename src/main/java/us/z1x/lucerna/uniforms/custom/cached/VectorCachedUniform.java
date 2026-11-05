package us.z1x.lucerna.uniforms.custom.cached;

import org.jetbrains.annotations.NotNull;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.stareval.function.FunctionReturn;
import us.z1x.lucerna.stareval.function.Type;

import java.util.function.Supplier;

public abstract class VectorCachedUniform<T> extends CachedUniform {

    final protected T cached;
    final private Supplier<T> supplier;

    public VectorCachedUniform(String name, T cache, Supplier<@NotNull T> supplier) {
        super(name);
        this.supplier = supplier;
        this.cached = cache;
    }

    abstract protected void setFrom(T other);

    @Override
    protected boolean doUpdate() {
        T other = this.supplier.get();
        if (other == null) {
            Lucerna.logger.warn("Cached Uniform supplier gave null back");
            return false;
        }
        if (!this.cached.equals(other)) {
            this.setFrom(other);
            return true;
        } else return false;
    }

    @Override
    public void writeTo(FunctionReturn functionReturn) {
        functionReturn.objectReturn = this.cached;
    }

    @Override
    public Type getType() {
        return Type.Float;
    }
}
