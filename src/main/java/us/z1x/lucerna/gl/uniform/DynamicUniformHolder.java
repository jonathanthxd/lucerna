package us.z1x.lucerna.gl.uniform;

import org.joml.Vector2i;
import org.joml.Vector4f;
import org.joml.Vector4i;
import us.z1x.lucerna.gl.state.ValueUpdateNotifier;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

public interface DynamicUniformHolder extends UniformHolder {
    DynamicUniformHolder uniform1f(String name, FloatSupplier value, ValueUpdateNotifier notifier);
    DynamicUniformHolder uniform1i(String name, IntSupplier value, ValueUpdateNotifier notifier);
    DynamicUniformHolder uniform2i(String name, Supplier<Vector2i> value, ValueUpdateNotifier notifier);
    DynamicUniformHolder uniform4f(String name, Supplier<Vector4f> value, ValueUpdateNotifier notifier);
    DynamicUniformHolder uniform4i(String name, Supplier<Vector4i> value, ValueUpdateNotifier notifier);
}
