package us.z1x.lucerna.uniforms.custom.cached;

import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import us.z1x.lucerna.parsing.VectorType;
import us.z1x.lucerna.stareval.expression.Expression;
import us.z1x.lucerna.stareval.expression.VariableExpression;
import us.z1x.lucerna.stareval.function.FunctionContext;
import us.z1x.lucerna.stareval.function.FunctionReturn;
import us.z1x.lucerna.stareval.function.Type;

public abstract class CachedUniform implements VariableExpression {
    private final String name;
    private int version = 1;

    public CachedUniform(String name) {
        this.name = name;
    }

    static public CachedUniform forExpression(String name, Type type, Expression expression, FunctionContext context) {
        final FunctionReturn held = new FunctionReturn();
        if (type.equals(Type.Boolean)) {
            return new BooleanCachedUniform(name, () -> {
                expression.evaluateTo(context, held);
                return held.booleanReturn;
            });
        } else if (type.equals(Type.Int)) {
            return new IntCachedUniform(name, () -> {
                expression.evaluateTo(context, held);
                return held.intReturn;
            });
        } else if (type.equals(Type.Float)) {
            return new FloatCachedUniform(name, () -> {
                expression.evaluateTo(context, held);
                return held.floatReturn;
            });
        } else if (type.equals(VectorType.VEC2)) {
            return new Float2VectorCachedUniform(name, () -> {
                expression.evaluateTo(context, held);
                return (Vector2f) held.objectReturn;
            });
        } else if (type.equals(VectorType.VEC3)) {
            return new Float3VectorCachedUniform(name, () -> {
                expression.evaluateTo(context, held);
                return (Vector3f) held.objectReturn;
            });
        } else if (type.equals(VectorType.VEC4)) {
            return new Float4VectorCachedUniform(name, () -> {
                expression.evaluateTo(context, held);
                return (Vector4f) held.objectReturn;
            });
        } else {
            throw new IllegalArgumentException("Custom uniforms of type: " + type + " are currently not supported");
        }
    }

    public int getVersion() {
        return this.version;
    }

    public void update() {
        if (doUpdate()) {
            this.version++;
        }
    }

    protected abstract boolean doUpdate();

    public abstract void push(int location);

    @Override
    public void evaluateTo(FunctionContext context, FunctionReturn functionReturn) {
        this.writeTo(functionReturn);
    }

    public abstract void writeTo(FunctionReturn functionReturn);

    public abstract Type getType();

    public String getName() {
        return name;
    }
}
