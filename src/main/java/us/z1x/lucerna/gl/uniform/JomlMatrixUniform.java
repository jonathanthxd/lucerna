package us.z1x.lucerna.gl.uniform;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import us.z1x.lucerna.gl.LucernaRenderSystem;

import java.nio.FloatBuffer;
import java.util.function.Supplier;

public class JomlMatrixUniform extends Uniform {
    private final FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
    private Matrix4f cachedValue;
    private final Supplier<Matrix4f> value;

    JomlMatrixUniform(int location, Supplier<Matrix4f> value) {
        super(location);

        this.cachedValue = null;
        this.value = value;
    }

    @Override
    public void update() {
        Matrix4f newValue = value.get();

        if (cachedValue == null || !newValue.equals(cachedValue)) {
            if (cachedValue == null) {
                cachedValue = new Matrix4f(newValue);
            } else {
                cachedValue.set(newValue);
            }

            cachedValue.get(buffer);
            buffer.rewind();

            LucernaRenderSystem.uniformMatrix4fv(location, false, buffer);
        }
    }
}
