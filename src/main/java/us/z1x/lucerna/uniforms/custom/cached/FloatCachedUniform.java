package us.z1x.lucerna.uniforms.custom.cached;

import us.z1x.lucerna.gl.LucernaRenderSystem;
import us.z1x.lucerna.gl.uniform.FloatSupplier;
import us.z1x.lucerna.stareval.function.FunctionReturn;
import us.z1x.lucerna.stareval.function.Type;

public class FloatCachedUniform extends CachedUniform {

    final private FloatSupplier supplier;
    private float cached;

    public FloatCachedUniform(String name, FloatSupplier supplier) {
        super(name);
        this.supplier = supplier;
    }

    @Override
    protected boolean doUpdate() {
        float prev = this.cached;
        this.cached = this.supplier.getAsFloat();
        return prev != cached;
    }

    @Override
    public void push(int location) {
        LucernaRenderSystem.uniform1f(location, this.cached);
    }

    @Override
    public void writeTo(FunctionReturn functionReturn) {
        functionReturn.floatReturn = this.cached;
    }

    @Override
    public Type getType() {
        return Type.Float;
    }
}
