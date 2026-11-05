package us.z1x.lucerna.gl.uniform;

import org.joml.Vector4i;
import us.z1x.lucerna.gl.LucernaRenderSystem;
import us.z1x.lucerna.gl.state.ValueUpdateNotifier;

import java.util.function.Supplier;

public class Vector4IntegerJomlUniform extends Uniform {
    private final Vector4i cachedValue = new Vector4i();
    private boolean initialized;
    private final Supplier<Vector4i> value;

    Vector4IntegerJomlUniform(int location, Supplier<Vector4i> value) {
        this(location, value, null);
    }

    Vector4IntegerJomlUniform(int location, Supplier<Vector4i> value, ValueUpdateNotifier notifier) {
        super(location, notifier);

        this.value = value;
    }

    @Override
    public void update() {
        updateValue();

        if (notifier != null) {
            notifier.setListener(this::updateValue);
        }
    }

    private void updateValue() {
        Vector4i newValue = value.get();

        if (!initialized || !newValue.equals(cachedValue)) {
            initialized = true;
            cachedValue.set(newValue);
            LucernaRenderSystem.uniform4i(this.location, newValue.x, newValue.y, newValue.z, newValue.w);
        }
    }
}
