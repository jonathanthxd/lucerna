package us.z1x.lucerna.gl.uniform;

import org.joml.Vector2i;
import us.z1x.lucerna.gl.LucernaRenderSystem;
import us.z1x.lucerna.gl.state.ValueUpdateNotifier;

import java.util.function.Supplier;

public class Vector2IntegerJomlUniform extends Uniform {
    private final Vector2i cachedValue = new Vector2i();
    private boolean initialized;
    private final Supplier<Vector2i> value;

    Vector2IntegerJomlUniform(int location, Supplier<Vector2i> value) {
        this(location, value, null);
    }

    Vector2IntegerJomlUniform(int location, Supplier<Vector2i> value, ValueUpdateNotifier notifier) {
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
        Vector2i newValue = value.get();

        if (!initialized || !newValue.equals(cachedValue)) {
            initialized = true;
            cachedValue.set(newValue);
            LucernaRenderSystem.uniform2i(this.location, newValue.x, newValue.y);
        }
    }
}
