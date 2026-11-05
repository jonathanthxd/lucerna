package us.z1x.lucerna.stareval.element.token;

import us.z1x.lucerna.stareval.element.ExpressionElement;
import us.z1x.lucerna.stareval.element.PriorityOperatorElement;
import us.z1x.lucerna.stareval.element.tree.UnaryExpressionElement;
import us.z1x.lucerna.stareval.parser.UnaryOp;

public class UnaryOperatorToken extends Token implements PriorityOperatorElement {
    private final UnaryOp op;

    public UnaryOperatorToken(UnaryOp op) {
        this.op = op;
    }

    @Override
    public String toString() {
        return "UnaryOp{" + this.op + "}";
    }

    @Override
    public int getPriority() {
        return -1;
    }

    @Override
    public UnaryExpressionElement resolveWith(ExpressionElement right) {
        return new UnaryExpressionElement(this.op, right);
    }
}
