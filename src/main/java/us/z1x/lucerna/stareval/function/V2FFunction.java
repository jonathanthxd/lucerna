package us.z1x.lucerna.stareval.function;

import us.z1x.lucerna.stareval.expression.Expression;

@FunctionalInterface
public interface V2FFunction extends TypedFunction {
    float eval();

    @Override
    default void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
        functionReturn.floatReturn = this.eval();
    }

    @Override
    default Type getReturnType() {
        return Type.Float;
    }

    @Override
    default Parameter[] getParameters() {
        return new Parameter[]{};
    }
}
