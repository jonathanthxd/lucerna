package us.z1x.lucerna.stareval.expression;

import us.z1x.lucerna.stareval.function.Type;

import java.util.Collection;

public abstract class ConstantExpression implements Expression {
    private final Type type;

    protected ConstantExpression(Type type) {
        this.type = type;
    }

    public Type getType() {
        return this.type;
    }

    @Override
    public void listVariables(Collection<? super VariableExpression> variables) {
    }
}
