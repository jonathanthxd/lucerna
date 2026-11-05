package us.z1x.lucerna.stareval.parser;

import us.z1x.lucerna.stareval.element.AccessibleExpressionElement;
import us.z1x.lucerna.stareval.element.Element;
import us.z1x.lucerna.stareval.element.ExpressionElement;
import us.z1x.lucerna.stareval.element.PriorityOperatorElement;
import us.z1x.lucerna.stareval.element.token.IdToken;
import us.z1x.lucerna.stareval.element.token.NumberToken;
import us.z1x.lucerna.stareval.element.token.UnaryOperatorToken;
import us.z1x.lucerna.stareval.element.tree.AccessExpressionElement;
import us.z1x.lucerna.stareval.element.tree.FunctionCall;
import us.z1x.lucerna.stareval.element.tree.partial.PartialBinaryExpression;
import us.z1x.lucerna.stareval.element.tree.partial.UnfinishedArgsExpression;
import us.z1x.lucerna.stareval.exception.MissingTokenException;
import us.z1x.lucerna.stareval.exception.ParseException;
import us.z1x.lucerna.stareval.exception.UnexpectedTokenException;

import java.util.ArrayList;
import java.util.List;

// bottom-up parser without lookahead, a binary operator first reduces its left side until it binds tighter than the operator
// an opening parenthesis pushes an argument list that commas fill, a closing parenthesis after an id makes a call
public class Parser {
    private final List<Element> stack = new ArrayList<>();

    Parser() {
    }

    public static ExpressionElement parse(String input, ParserOptions options) throws ParseException {
        return Tokenizer.parse(input, options);
    }

    private Element peek() {
        if (!this.stack.isEmpty()) {
            return this.stack.get(this.stack.size() - 1);
        }

        return null;
    }

    private Element pop() {
        if (this.stack.isEmpty()) {
            throw new IllegalStateException("Internal token stack is empty");
        }

        return this.stack.remove(this.stack.size() - 1);
    }

    private void push(Element element) {
        this.stack.add(element);
    }

    private ExpressionElement expressionReducePop() {
        return this.expressionReducePop(Integer.MAX_VALUE);
    }

    private ExpressionElement expressionReducePop(int priority) {
        ExpressionElement token = (ExpressionElement) this.pop();

        while (!this.stack.isEmpty()) {
            Element x = this.peek();

            if (x instanceof PriorityOperatorElement && ((PriorityOperatorElement) x).getPriority() <= priority) {
                this.pop();
                token = ((PriorityOperatorElement) x).resolveWith(token);
            } else {
                break;
            }
        }

        return token;
    }

    private void commaReduce(int index) throws ParseException {
        ExpressionElement expr = this.expressionReducePop();
        Element args = this.peek();

        if (args == null) {
            throw new MissingTokenException(
                "Expected an opening bracket '(' before seeing a comma ',' or closing bracket ')'",
                index
            );
        }

        if (args instanceof UnfinishedArgsExpression) {
            ((UnfinishedArgsExpression) args).tokens.add(expr);
        } else {
            throw new UnexpectedTokenException(
                "Expected to see an opening bracket '(' or a comma ',' right before an expression followed by a " +
                    "closing bracket ')' or a comma ','", index);
        }
    }

    void visitId(String id) {
        this.push(new IdToken(id));
    }

    boolean canReadAccess() {
        return this.peek() instanceof AccessibleExpressionElement;
    }

    // assumes canReadAccess returned true
    void visitAccess(String access) {
        AccessibleExpressionElement pop = (AccessibleExpressionElement) this.pop();
        this.push(new AccessExpressionElement(pop, access));
    }

    void visitNumber(String numberString) {
        this.push(new NumberToken(numberString));
    }

    void visitOpeningParenthesis() {
        this.push(new UnfinishedArgsExpression());
    }

    void visitComma(int index) throws ParseException {
        if (this.peek() instanceof ExpressionElement) {
            this.commaReduce(index);
        } else {
            throw new UnexpectedTokenException("Expected an expression before a comma ','", index);
        }
    }

    // allows a trailing comma
    void visitClosingParenthesis(int index) throws ParseException {
        boolean expressionOnTop = this.peek() instanceof ExpressionElement;
        if (expressionOnTop) {
            this.commaReduce(index);
        }

        UnfinishedArgsExpression args;
        {
            if (this.stack.isEmpty()) {
                throw new MissingTokenException("A closing bracket ')' can't be the first character of an expression", index);
            }

            Element pop = this.pop();

            if (!(pop instanceof UnfinishedArgsExpression)) {
                throw new UnexpectedTokenException(
                    "Expected to see an opening bracket '(' or a comma ',' right before an expression followed by a " +
                        "closing bracket ')' or a comma ','", index);
            }
            args = (UnfinishedArgsExpression) pop;
        }

        Element top = this.peek();

        if (top instanceof IdToken) {
            this.pop();
            this.push(new FunctionCall(((IdToken) top).getId(), args.tokens));
        } else {
            if (args.tokens.isEmpty()) {
                throw new MissingTokenException("Encountered empty brackets that aren't a call", index);
            } else if (args.tokens.size() > 1) {
                throw new UnexpectedTokenException("Encountered too many expressions in brackets that aren't a call", index);
            } else if (!expressionOnTop) {
                throw new UnexpectedTokenException("Encountered a trailing comma in brackets that aren't a call", index);
            } else {
                this.push(args.tokens.get(0));
            }
        }
    }

    boolean canReadBinaryOp() {
        return this.peek() instanceof ExpressionElement;
    }

    void visitBinaryOperator(BinaryOp binaryOp) {
        ExpressionElement left = this.expressionReducePop(binaryOp.getPriority());

        this.stack.add(new PartialBinaryExpression(left, binaryOp));
    }

    void visitUnaryOperator(UnaryOp unaryOp) {
        this.push(new UnaryOperatorToken(unaryOp));
    }

    ExpressionElement getFinal(int endIndex) throws ParseException {
        if (!this.stack.isEmpty()) {
            if (this.peek() instanceof ExpressionElement) {
                ExpressionElement result = this.expressionReducePop();

                if (this.stack.isEmpty()) {
                    return result;
                }

                if (this.peek() instanceof UnfinishedArgsExpression) {
                    throw new MissingTokenException("Expected a closing bracket", endIndex);
                } else {
                    throw new UnexpectedTokenException(
                        "The stack of tokens isn't empty at the end of the expression: " + this.stack +
                            " top: " + result, endIndex);
                }
            } else {
                Element top = this.peek();
                if (top instanceof UnfinishedArgsExpression) {
                    throw new MissingTokenException("Expected a closing bracket", endIndex);
                } else if (top instanceof PriorityOperatorElement) {
                    throw new MissingTokenException(
                        "Expected a identifier, constant or subexpression on the right side of the operator",
                        endIndex);
                } else {
                    throw new UnexpectedTokenException(
                        "The stack of tokens contains an unexpected token at the top: " + this.stack,
                        endIndex);
                }
            }
        } else {
            throw new MissingTokenException("The input seems to be empty", endIndex);
        }
    }
}
