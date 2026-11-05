package us.z1x.lucerna.gl.uniform;

import org.joml.Vector3i;
import us.z1x.lucerna.gl.LucernaRenderSystem;

import java.util.function.Supplier;

public class Vector3IntegerUniform extends Uniform {
    private final Vector3i cachedValue = new Vector3i();
    private final Supplier<Vector3i> value;
    private boolean initialized;

    Vector3IntegerUniform(int location, Supplier<Vector3i> value) {
        super(location);

        this.value = value;
    }

    @Override
    public void update() {
        Vector3i newValue = value.get();

        if (!initialized || !newValue.equals(cachedValue)) {
            initialized = true;
            cachedValue.set(newValue);
            LucernaRenderSystem.uniform3i(location, newValue.x, newValue.y, newValue.z);
        }
    }
}
