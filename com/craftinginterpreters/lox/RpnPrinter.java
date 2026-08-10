package com.craftinginterpreters.lox;

class RpnPrinter implements Expr.Visitor<String> {
    String print(Expr expr) {
        return expr.accept(this);
    }

    @Override
    public String visitBinaryExpr(Expr.Binary expr) {
        return expr.left.accept(this) + " " + expr.right.accept(this) + " " + expr.operator.lexeme;
    }

    @Override
    public String visitGroupingExpr(Expr.Grouping expr) {
        return expr.expression.accept(this);
    }

    @Override
    public String visitLiteralExpr(Expr.Literal expr) {
        if (expr.value == null) return "nil";
        return expr.value.toString();
    }

    @Override
    public String visitUnaryExpr(Expr.Unary expr) {
        // In RPN, unary operators are placed after the operand.
        // We can append the operator character directly.
        return expr.right.accept(this) + " " + expr.operator.lexeme;
    }

    @Override
    public String visitAssignExpr(Expr.Assign expr) {
        return expr.value.accept(this) + " " + expr.name.lexeme + " =";
    }

    @Override
    public String visitCallExpr(Expr.Call expr) {
        StringBuilder builder = new StringBuilder();
        for (Expr argument : expr.arguments) {
            builder.append(argument.accept(this)).append(" ");
        }
        builder.append(expr.callee.accept(this)).append(" call");
        return builder.toString();
    }

    @Override
    public String visitFunctionExpr(Expr.Function expr) {
        return "<fn>";
    }

    @Override
    public String visitLogicalExpr(Expr.Logical expr) {
        return expr.left.accept(this) + " " + expr.right.accept(this) + " " + expr.operator.lexeme;
    }

    @Override
    public String visitTernaryExpr(Expr.Ternary expr) {
        return expr.condition.accept(this) + " " + expr.thenBranch.accept(this) + " " + expr.elseBranch.accept(this) + " ?";
    }

    @Override
    public String visitVariableExpr(Expr.Variable expr) {
        return expr.name.lexeme;
    }

    @Override
    public String visitGetExpr(Expr.Get expr) {
        return expr.object.accept(this) + " " + expr.name.lexeme + " get";
    }

    @Override
    public String visitSetExpr(Expr.Set expr) {
        return expr.object.accept(this) + " " + expr.value.accept(this) + " " + expr.name.lexeme + " set";
    }

    @Override
    public String visitThisExpr(Expr.This expr) {
        return "this";
    }

    @Override
    public String visitSuperExpr(Expr.Super expr) {
        return expr.method.lexeme + " super";
    }

    @Override
    public String visitInnerExpr(Expr.Inner expr) {
        return "inner";
    }

    @Override
    public String visitArrayExpr(Expr.Array expr) {
        StringBuilder builder = new StringBuilder();
        for (Expr element : expr.elements) {
            builder.append(element.accept(this)).append(" ");
        }
        builder.append("array");
        return builder.toString();
    }

    @Override
    public String visitSubscriptGetExpr(Expr.SubscriptGet expr) {
        return expr.object.accept(this) + " " + expr.index.accept(this) + " []";
    }

    @Override
    public String visitSubscriptSetExpr(Expr.SubscriptSet expr) {
        return expr.object.accept(this) + " " + expr.index.accept(this) + " " + expr.value.accept(this) + " []=";
    }

    public static void main(String[] args) {
        // Test expression: (1 + 2) * (4 - 3)
        Expr expression = new Expr.Binary(
            new Expr.Grouping(
                new Expr.Binary(
                    new Expr.Literal(1),
                    new Token(TokenType.PLUS, "+", null, 1),
                    new Expr.Literal(2)
                )
            ),
            new Token(TokenType.STAR, "*", null, 1),
            new Expr.Grouping(
                new Expr.Binary(
                    new Expr.Literal(4),
                    new Token(TokenType.MINUS, "-", null, 1),
                    new Expr.Literal(3)
                )
            )
        );

        System.out.println(new RpnPrinter().print(expression));
    }
}
