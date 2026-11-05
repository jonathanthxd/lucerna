package us.z1x.lucerna.parsing;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import us.z1x.lucerna.stareval.function.Type;

import java.util.function.Supplier;

public class MatrixType<T> extends Type.ObjectType {
    public static MatrixType<Matrix3f> MAT3 = new MatrixType<>("mat3", Matrix3f::new);
    public static MatrixType<Matrix4f> MAT4 = new MatrixType<>("mat4", Matrix4f::new);
    final String name;
    private final Supplier<T> supplier;

    public MatrixType(String name, Supplier<T> supplier) {
        this.name = name;
        this.supplier = supplier;
    }

    @Override
    public String toString() {
        return name;
    }
}
