/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
// Checkstyle: stop
//@formatter:off
// Generated from /Users/matt/code/graal/truffle/src/com.oracle.truffle.sl/src/com/oracle/truffle/sl/parser/SimpleLanguageBytecode.g4 by ANTLR 4.12.0
package com.oracle.truffle.sl.parser;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link SimpleLanguageBytecodeParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface SimpleLanguageBytecodeVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link SimpleLanguageBytecodeParser#simplelanguage}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSimplelanguage(SimpleLanguageBytecodeParser.SimplelanguageContext ctx);
	/**
	 * Visit a parse tree produced by {@link SimpleLanguageBytecodeParser#function}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFunction(SimpleLanguageBytecodeParser.FunctionContext ctx);
	/**
	 * Visit a parse tree produced by {@link SimpleLanguageBytecodeParser#block}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBlock(SimpleLanguageBytecodeParser.BlockContext ctx);
	/**
	 * Visit a parse tree produced by {@link SimpleLanguageBytecodeParser#statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatement(SimpleLanguageBytecodeParser.StatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link SimpleLanguageBytecodeParser#break_statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBreak_statement(SimpleLanguageBytecodeParser.Break_statementContext ctx);
	/**
	 * Visit a parse tree produced by {@link SimpleLanguageBytecodeParser#continue_statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitContinue_statement(SimpleLanguageBytecodeParser.Continue_statementContext ctx);
	/**
	 * Visit a parse tree produced by {@link SimpleLanguageBytecodeParser#expression_statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpression_statement(SimpleLanguageBytecodeParser.Expression_statementContext ctx);
	/**
	 * Visit a parse tree produced by {@link SimpleLanguageBytecodeParser#debugger_statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDebugger_statement(SimpleLanguageBytecodeParser.Debugger_statementContext ctx);
	/**
	 * Visit a parse tree produced by {@link SimpleLanguageBytecodeParser#while_statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitWhile_statement(SimpleLanguageBytecodeParser.While_statementContext ctx);
	/**
	 * Visit a parse tree produced by {@link SimpleLanguageBytecodeParser#if_statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIf_statement(SimpleLanguageBytecodeParser.If_statementContext ctx);
	/**
	 * Visit a parse tree produced by {@link SimpleLanguageBytecodeParser#return_statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitReturn_statement(SimpleLanguageBytecodeParser.Return_statementContext ctx);
	/**
	 * Visit a parse tree produced by {@link SimpleLanguageBytecodeParser#expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpression(SimpleLanguageBytecodeParser.ExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link SimpleLanguageBytecodeParser#logic_term}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLogic_term(SimpleLanguageBytecodeParser.Logic_termContext ctx);
	/**
	 * Visit a parse tree produced by {@link SimpleLanguageBytecodeParser#logic_factor}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLogic_factor(SimpleLanguageBytecodeParser.Logic_factorContext ctx);
	/**
	 * Visit a parse tree produced by {@link SimpleLanguageBytecodeParser#arithmetic}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArithmetic(SimpleLanguageBytecodeParser.ArithmeticContext ctx);
	/**
	 * Visit a parse tree produced by {@link SimpleLanguageBytecodeParser#term}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTerm(SimpleLanguageBytecodeParser.TermContext ctx);
	/**
	 * Visit a parse tree produced by the {@code NameAccess}
	 * labeled alternative in {@link SimpleLanguageBytecodeParser#factor}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNameAccess(SimpleLanguageBytecodeParser.NameAccessContext ctx);
	/**
	 * Visit a parse tree produced by the {@code StringLiteral}
	 * labeled alternative in {@link SimpleLanguageBytecodeParser#factor}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStringLiteral(SimpleLanguageBytecodeParser.StringLiteralContext ctx);
	/**
	 * Visit a parse tree produced by the {@code NumericLiteral}
	 * labeled alternative in {@link SimpleLanguageBytecodeParser#factor}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNumericLiteral(SimpleLanguageBytecodeParser.NumericLiteralContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ParenExpression}
	 * labeled alternative in {@link SimpleLanguageBytecodeParser#factor}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParenExpression(SimpleLanguageBytecodeParser.ParenExpressionContext ctx);
	/**
	 * Visit a parse tree produced by the {@code MemberCall}
	 * labeled alternative in {@link SimpleLanguageBytecodeParser#member_expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMemberCall(SimpleLanguageBytecodeParser.MemberCallContext ctx);
	/**
	 * Visit a parse tree produced by the {@code MemberAssign}
	 * labeled alternative in {@link SimpleLanguageBytecodeParser#member_expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMemberAssign(SimpleLanguageBytecodeParser.MemberAssignContext ctx);
	/**
	 * Visit a parse tree produced by the {@code MemberField}
	 * labeled alternative in {@link SimpleLanguageBytecodeParser#member_expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMemberField(SimpleLanguageBytecodeParser.MemberFieldContext ctx);
	/**
	 * Visit a parse tree produced by the {@code MemberIndex}
	 * labeled alternative in {@link SimpleLanguageBytecodeParser#member_expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMemberIndex(SimpleLanguageBytecodeParser.MemberIndexContext ctx);
}
