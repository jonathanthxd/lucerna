package us.z1x.lucerna.stareval.element.tree.partial;

import us.z1x.lucerna.stareval.element.ExpressionElement;

import java.util.ArrayList;
import java.util.List;

public class UnfinishedArgsExpression extends PartialExpression {
    public final List<ExpressionElement> tokens = new ArrayList<>();

    @Override
    public String toString() {
        return "UnfinishedArgs{" + this.tokens + "}";
    }
}
