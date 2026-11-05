package us.z1x.lucerna.stareval.function;

import us.z1x.lucerna.stareval.expression.Expression;

public interface FunctionContext {
    Expression getVariable(String name);

    boolean hasVariable(String name);
}
