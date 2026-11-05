package us.z1x.lucerna.uniforms.custom.cached;

import us.z1x.lucerna.gl.LucernaRenderSystem;
import us.z1x.lucerna.stareval.function.FunctionReturn;
import us.z1x.lucerna.stareval.function.Type;

import java.util.function.IntSupplier;

public class IntCachedUniform extends CachedUniform {

    final private IntSupplier supplier;
    private int cached;

    public IntCachedUniform(String name, IntSupplier supplier) {
        super(name);
        this.supplier = supplier;
    }

    @Override
    protected boolean doUpdate() {
        int prev = this.cached;
        this.cached = this.supplier.getAsInt();
        return prev != cached;
    }

    @Override
    public void push(int location) {
        LucernaRenderSystem.uniform1i(location, this.cached);
    }

    @Override
    public void writeTo(FunctionReturn functionReturn) {
        functionReturn.intReturn = this.cached;
    }

    @Override
    public Type getType() {
        return Type.Int;
    }
}
