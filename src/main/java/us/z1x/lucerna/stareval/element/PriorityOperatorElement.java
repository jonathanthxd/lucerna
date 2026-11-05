package us.z1x.lucerna.stareval.element;

public interface PriorityOperatorElement extends Element {
    int getPriority();

    ExpressionElement resolveWith(ExpressionElement right);
}
