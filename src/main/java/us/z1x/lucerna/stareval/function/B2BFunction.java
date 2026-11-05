package us.z1x.lucerna.stareval.function;

import us.z1x.lucerna.stareval.expression.Expression;

@FunctionalInterface
public interface B2BFunction extends TypedFunction {
    boolean eval(boolean a);

    @Override
    default void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
        params[0].evaluateTo(context, functionReturn);
        boolean a = functionReturn.booleanReturn;

        functionReturn.booleanReturn = this.eval(a);
    }

    @Override
    default Type getReturnType() {
        return Type.Boolean;
    }

    @Override
    default Parameter[] getParameters() {
        return new Parameter[]{Type.BooleanParameter};
    }
}
