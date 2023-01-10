// Generated from /home/prof/graalvm/graal/truffle/src/com.oracle.truffle.sl/src/com/oracle/truffle/sl/parser/operations/SimpleLanguageOperations.g4 by ANTLR 4.9.2
package com.oracle.truffle.sl.parser;

// DO NOT MODIFY - generated from SimpleLanguage.g4 using "mx create-sl-parser"

import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced by
 * {@link SimpleLanguageOperationsParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for operations with no return
 *            type.
 */
public interface SimpleLanguageOperationsVisitor<T> extends ParseTreeVisitor<T> {
    /**
     * Visit a parse tree produced by {@link SimpleLanguageOperationsParser#simplelanguage}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitSimplelanguage(SimpleLanguageOperationsParser.SimplelanguageContext ctx);

    /**
     * Visit a parse tree produced by {@link SimpleLanguageOperationsParser#function}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitFunction(SimpleLanguageOperationsParser.FunctionContext ctx);

    /**
     * Visit a parse tree produced by {@link SimpleLanguageOperationsParser#block}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitBlock(SimpleLanguageOperationsParser.BlockContext ctx);

    /**
     * Visit a parse tree produced by {@link SimpleLanguageOperationsParser#statement}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitStatement(SimpleLanguageOperationsParser.StatementContext ctx);

    /**
     * Visit a parse tree produced by {@link SimpleLanguageOperationsParser#break_statement}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitBreak_statement(SimpleLanguageOperationsParser.Break_statementContext ctx);

    /**
     * Visit a parse tree produced by {@link SimpleLanguageOperationsParser#continue_statement}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitContinue_statement(SimpleLanguageOperationsParser.Continue_statementContext ctx);

    /**
     * Visit a parse tree produced by {@link SimpleLanguageOperationsParser#expression_statement}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitExpression_statement(SimpleLanguageOperationsParser.Expression_statementContext ctx);

    /**
     * Visit a parse tree produced by {@link SimpleLanguageOperationsParser#debugger_statement}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitDebugger_statement(SimpleLanguageOperationsParser.Debugger_statementContext ctx);

    /**
     * Visit a parse tree produced by {@link SimpleLanguageOperationsParser#while_statement}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitWhile_statement(SimpleLanguageOperationsParser.While_statementContext ctx);

    /**
     * Visit a parse tree produced by {@link SimpleLanguageOperationsParser#if_statement}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitIf_statement(SimpleLanguageOperationsParser.If_statementContext ctx);

    /**
     * Visit a parse tree produced by {@link SimpleLanguageOperationsParser#return_statement}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitReturn_statement(SimpleLanguageOperationsParser.Return_statementContext ctx);

    /**
     * Visit a parse tree produced by {@link SimpleLanguageOperationsParser#expression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitExpression(SimpleLanguageOperationsParser.ExpressionContext ctx);

    /**
     * Visit a parse tree produced by {@link SimpleLanguageOperationsParser#logic_term}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitLogic_term(SimpleLanguageOperationsParser.Logic_termContext ctx);

    /**
     * Visit a parse tree produced by {@link SimpleLanguageOperationsParser#logic_factor}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitLogic_factor(SimpleLanguageOperationsParser.Logic_factorContext ctx);

    /**
     * Visit a parse tree produced by {@link SimpleLanguageOperationsParser#arithmetic}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitArithmetic(SimpleLanguageOperationsParser.ArithmeticContext ctx);

    /**
     * Visit a parse tree produced by {@link SimpleLanguageOperationsParser#term}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitTerm(SimpleLanguageOperationsParser.TermContext ctx);

    /**
     * Visit a parse tree produced by the {@code NameAccess} labeled alternative in
     * {@link SimpleLanguageOperationsParser#factor}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitNameAccess(SimpleLanguageOperationsParser.NameAccessContext ctx);

    /**
     * Visit a parse tree produced by the {@code StringLiteral} labeled alternative in
     * {@link SimpleLanguageOperationsParser#factor}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitStringLiteral(SimpleLanguageOperationsParser.StringLiteralContext ctx);

    /**
     * Visit a parse tree produced by the {@code NumericLiteral} labeled alternative in
     * {@link SimpleLanguageOperationsParser#factor}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitNumericLiteral(SimpleLanguageOperationsParser.NumericLiteralContext ctx);

    /**
     * Visit a parse tree produced by the {@code ParenExpression} labeled alternative in
     * {@link SimpleLanguageOperationsParser#factor}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitParenExpression(SimpleLanguageOperationsParser.ParenExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code MemberCall} labeled alternative in
     * {@link SimpleLanguageOperationsParser#member_expression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitMemberCall(SimpleLanguageOperationsParser.MemberCallContext ctx);

    /**
     * Visit a parse tree produced by the {@code MemberAssign} labeled alternative in
     * {@link SimpleLanguageOperationsParser#member_expression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitMemberAssign(SimpleLanguageOperationsParser.MemberAssignContext ctx);

    /**
     * Visit a parse tree produced by the {@code MemberField} labeled alternative in
     * {@link SimpleLanguageOperationsParser#member_expression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitMemberField(SimpleLanguageOperationsParser.MemberFieldContext ctx);

    /**
     * Visit a parse tree produced by the {@code MemberIndex} labeled alternative in
     * {@link SimpleLanguageOperationsParser#member_expression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitMemberIndex(SimpleLanguageOperationsParser.MemberIndexContext ctx);
}