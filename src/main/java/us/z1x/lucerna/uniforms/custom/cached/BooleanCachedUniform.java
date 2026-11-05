package us.z1x.lucerna.uniforms.custom.cached;

import us.z1x.lucerna.gl.LucernaRenderSystem;
import us.z1x.lucerna.stareval.function.FunctionReturn;
import us.z1x.lucerna.stareval.function.Type;

import java.util.function.BooleanSupplier;

public class BooleanCachedUniform extends CachedUniform {

    final private BooleanSupplier supplier;
    private boolean cached;

    public BooleanCachedUniform(String name, BooleanSupplier supplier) {
        super(name);
        this.supplier = supplier;
    }

    @Override
    protected boolean doUpdate() {
        boolean prev = this.cached;
        this.cached = this.supplier.getAsBoolean();
        return prev != cached;
    }

    @Override
    public void push(int location) {
        LucernaRenderSystem.uniform1i(location, this.cached ? 1 : 0);
    }

    @Override
    public void writeTo(FunctionReturn functionReturn) {
        functionReturn.booleanReturn = this.cached;
    }

    @Override
    public Type getType() {
        return Type.Boolean;
    }
}
