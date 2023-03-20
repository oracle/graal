// Generated from /home/prof/graalvm/graal/truffle/src/com.oracle.truffle.sl/src/com/oracle/truffle/sl/parser/operations/SimpleLanguageOperations.g4 by ANTLR 4.9.2
package com.oracle.truffle.sl.parser;

// DO NOT MODIFY - generated from SimpleLanguage.g4 using "mx create-sl-parser"

import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;

/**
 * This class provides an empty implementation of {@link SimpleLanguageOperationsVisitor}, which can
 * be extended to create a visitor which only needs to handle a subset of the available methods.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for operations with no return
 *            type.
 */
public class SimpleLanguageOperationsBaseVisitor<T> extends AbstractParseTreeVisitor<T> implements SimpleLanguageOperationsVisitor<T> {
    /**
     * {@inheritDoc}
     *
     * <p>
     * The default implementation returns the result of calling {@link #visitChildren} on
     * {@code ctx}.
     * </p>
     */
    @Override
    public T visitSimplelanguage(SimpleLanguageOperationsParser.SimplelanguageContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The default implementation returns the result of calling {@link #visitChildren} on
     * {@code ctx}.
     * </p>
     */
    @Override
    public T visitFunction(SimpleLanguageOperationsParser.FunctionContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The default implementation returns the result of calling {@link #visitChildren} on
     * {@code ctx}.
     * </p>
     */
    @Override
    public T visitBlock(SimpleLanguageOperationsParser.BlockContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The default implementation returns the result of calling {@link #visitChildren} on
     * {@code ctx}.
     * </p>
     */
    @Override
    public T visitStatement(SimpleLanguageOperationsParser.StatementContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The default implementation returns the result of calling {@link #visitChildren} on
     * {@code ctx}.
     * </p>
     */
    @Override
    public T visitBreak_statement(SimpleLanguageOperationsParser.Break_statementContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The default implementation returns the result of calling {@link #visitChildren} on
     * {@code ctx}.
     * </p>
     */
    @Override
    public T visitContinue_statement(SimpleLanguageOperationsParser.Continue_statementContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The default implementation returns the result of calling {@link #visitChildren} on
     * {@code ctx}.
     * </p>
     */
    @Override
    public T visitExpression_statement(SimpleLanguageOperationsParser.Expression_statementContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The default implementation returns the result of calling {@link #visitChildren} on
     * {@code ctx}.
     * </p>
     */
    @Override
    public T visitDebugger_statement(SimpleLanguageOperationsParser.Debugger_statementContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The default implementation returns the result of calling {@link #visitChildren} on
     * {@code ctx}.
     * </p>
     */
    @Override
    public T visitWhile_statement(SimpleLanguageOperationsParser.While_statementContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The default implementation returns the result of calling {@link #visitChildren} on
     * {@code ctx}.
     * </p>
     */
    @Override
    public T visitIf_statement(SimpleLanguageOperationsParser.If_statementContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The default implementation returns the result of calling {@link #visitChildren} on
     * {@code ctx}.
     * </p>
     */
    @Override
    public T visitReturn_statement(SimpleLanguageOperationsParser.Return_statementContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The default implementation returns the result of calling {@link #visitChildren} on
     * {@code ctx}.
     * </p>
     */
    @Override
    public T visitExpression(SimpleLanguageOperationsParser.ExpressionContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The default implementation returns the result of calling {@link #visitChildren} on
     * {@code ctx}.
     * </p>
     */
    @Override
    public T visitLogic_term(SimpleLanguageOperationsParser.Logic_termContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The default implementation returns the result of calling {@link #visitChildren} on
     * {@code ctx}.
     * </p>
     */
    @Override
    public T visitLogic_factor(SimpleLanguageOperationsParser.Logic_factorContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The default implementation returns the result of calling {@link #visitChildren} on
     * {@code ctx}.
     * </p>
     */
    @Override
    public T visitArithmetic(SimpleLanguageOperationsParser.ArithmeticContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The default implementation returns the result of calling {@link #visitChildren} on
     * {@code ctx}.
     * </p>
     */
    @Override
    public T visitTerm(SimpleLanguageOperationsParser.TermContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The default implementation returns the result of calling {@link #visitChildren} on
     * {@code ctx}.
     * </p>
     */
    @Override
    public T visitNameAccess(SimpleLanguageOperationsParser.NameAccessContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The default implementation returns the result of calling {@link #visitChildren} on
     * {@code ctx}.
     * </p>
     */
    @Override
    public T visitStringLiteral(SimpleLanguageOperationsParser.StringLiteralContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The default implementation returns the result of calling {@link #visitChildren} on
     * {@code ctx}.
     * </p>
     */
    @Override
    public T visitNumericLiteral(SimpleLanguageOperationsParser.NumericLiteralContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The default implementation returns the result of calling {@link #visitChildren} on
     * {@code ctx}.
     * </p>
     */
    @Override
    public T visitParenExpression(SimpleLanguageOperationsParser.ParenExpressionContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The default implementation returns the result of calling {@link #visitChildren} on
     * {@code ctx}.
     * </p>
     */
    @Override
    public T visitMemberCall(SimpleLanguageOperationsParser.MemberCallContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The default implementation returns the result of calling {@link #visitChildren} on
     * {@code ctx}.
     * </p>
     */
    @Override
    public T visitMemberAssign(SimpleLanguageOperationsParser.MemberAssignContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The default implementation returns the result of calling {@link #visitChildren} on
     * {@code ctx}.
     * </p>
     */
    @Override
    public T visitMemberField(SimpleLanguageOperationsParser.MemberFieldContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The default implementation returns the result of calling {@link #visitChildren} on
     * {@code ctx}.
     * </p>
     */
    @Override
    public T visitMemberIndex(SimpleLanguageOperationsParser.MemberIndexContext ctx) {
        return visitChildren(ctx);
    }
}