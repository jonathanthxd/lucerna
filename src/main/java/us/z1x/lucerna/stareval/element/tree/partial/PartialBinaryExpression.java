package us.z1x.lucerna.stareval.element.tree.partial;

import us.z1x.lucerna.stareval.element.ExpressionElement;
import us.z1x.lucerna.stareval.element.PriorityOperatorElement;
import us.z1x.lucerna.stareval.element.tree.BinaryExpressionElement;
import us.z1x.lucerna.stareval.parser.BinaryOp;

public class PartialBinaryExpression extends PartialExpression implements PriorityOperatorElement {
    private final ExpressionElement left;
    private final BinaryOp op;

    public PartialBinaryExpression(ExpressionElement left, BinaryOp op) {
        this.left = left;
        this.op = op;
    }

    @Override
    public String toString() {
        return "PartialBinaryExpression{ {" + this.left + "} " + this.op + "}";
    }

    @Override
    public int getPriority() {
        return this.op.getPriority();
    }

    @Override
    public BinaryExpressionElement resolveWith(ExpressionElement right) {
        return new BinaryExpressionElement(this.op, this.left, right);
    }
}
