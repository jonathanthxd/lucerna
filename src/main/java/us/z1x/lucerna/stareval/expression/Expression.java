package us.z1x.lucerna.stareval.expression;

import us.z1x.lucerna.stareval.function.FunctionContext;
import us.z1x.lucerna.stareval.function.FunctionReturn;

import java.util.Collection;

public interface Expression {
    void evaluateTo(FunctionContext context, FunctionReturn functionReturn);

    default Expression partialEval(FunctionContext context, FunctionReturn functionReturn) {
        return this;
    }

    void listVariables(Collection<? super VariableExpression> variables);
}
