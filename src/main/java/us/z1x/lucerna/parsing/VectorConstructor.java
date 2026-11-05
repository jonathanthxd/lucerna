package us.z1x.lucerna.parsing;

import us.z1x.lucerna.stareval.Util;
import us.z1x.lucerna.stareval.expression.Expression;
import us.z1x.lucerna.stareval.function.AbstractTypedFunction;
import us.z1x.lucerna.stareval.function.FunctionContext;
import us.z1x.lucerna.stareval.function.FunctionReturn;
import us.z1x.lucerna.stareval.function.Type;

import java.util.Arrays;

public class VectorConstructor extends AbstractTypedFunction {

    public VectorConstructor(Type inner, int size) {
        super(
            new VectorType.ArrayVector(inner, size),
            Util.make(new Type[size], params -> Arrays.fill(params, inner))
        );
    }

    @Override
    public VectorType.ArrayVector getReturnType() {
        return (VectorType.ArrayVector) super.getReturnType();
    }

    @Override
    public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn
        functionReturn) {
        VectorType.ArrayVector vectorType = this.getReturnType();
        vectorType.map(params, context, functionReturn, (i, p, ctx, fr) -> p[i].evaluateTo(ctx, fr));
    }
}
