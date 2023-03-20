/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.parser;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.debug.DebuggerTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.operation.OperationConfig;
import com.oracle.truffle.api.operation.OperationLabel;
import com.oracle.truffle.api.operation.OperationLocal;
import com.oracle.truffle.api.operation.OperationNodes;
import com.oracle.truffle.api.operation.OperationParser;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.operations.SLOperationRootNode;
import com.oracle.truffle.sl.operations.SLOperationRootNodeGen;
import com.oracle.truffle.sl.operations.SLOperationSerialization;
import com.oracle.truffle.sl.parser.SimpleLanguageOperationsParser.ArithmeticContext;
import com.oracle.truffle.sl.parser.SimpleLanguageOperationsParser.BlockContext;
import com.oracle.truffle.sl.parser.SimpleLanguageOperationsParser.Break_statementContext;
import com.oracle.truffle.sl.parser.SimpleLanguageOperationsParser.Continue_statementContext;
import com.oracle.truffle.sl.parser.SimpleLanguageOperationsParser.Debugger_statementContext;
import com.oracle.truffle.sl.parser.SimpleLanguageOperationsParser.ExpressionContext;
import com.oracle.truffle.sl.parser.SimpleLanguageOperationsParser.FunctionContext;
import com.oracle.truffle.sl.parser.SimpleLanguageOperationsParser.If_statementContext;
import com.oracle.truffle.sl.parser.SimpleLanguageOperationsParser.Logic_factorContext;
import com.oracle.truffle.sl.parser.SimpleLanguageOperationsParser.Logic_termContext;
import com.oracle.truffle.sl.parser.SimpleLanguageOperationsParser.MemberAssignContext;
import com.oracle.truffle.sl.parser.SimpleLanguageOperationsParser.MemberCallContext;
import com.oracle.truffle.sl.parser.SimpleLanguageOperationsParser.MemberFieldContext;
import com.oracle.truffle.sl.parser.SimpleLanguageOperationsParser.MemberIndexContext;
import com.oracle.truffle.sl.parser.SimpleLanguageOperationsParser.Member_expressionContext;
import com.oracle.truffle.sl.parser.SimpleLanguageOperationsParser.NameAccessContext;
import com.oracle.truffle.sl.parser.SimpleLanguageOperationsParser.NumericLiteralContext;
import com.oracle.truffle.sl.parser.SimpleLanguageOperationsParser.Return_statementContext;
import com.oracle.truffle.sl.parser.SimpleLanguageOperationsParser.StatementContext;
import com.oracle.truffle.sl.parser.SimpleLanguageOperationsParser.StringLiteralContext;
import com.oracle.truffle.sl.parser.SimpleLanguageOperationsParser.TermContext;
import com.oracle.truffle.sl.parser.SimpleLanguageOperationsParser.While_statementContext;
import com.oracle.truffle.sl.runtime.SLBigInteger;
import com.oracle.truffle.sl.runtime.SLNull;

/**
 * SL AST visitor that uses the Operation DSL for generating code.
 */
public final class SLOperationsVisitor extends SLBaseVisitor {

    private static final boolean DO_LOG_NODE_CREATION = false;
    private static final boolean FORCE_SERIALIZE = true;

    public static void parseSL(SLLanguage language, Source source, Map<TruffleString, RootCallTarget> functions) {
        OperationParser<SLOperationRootNodeGen.Builder> slParser = (b) -> {
            SLOperationsVisitor visitor = new SLOperationsVisitor(language, source, b);
            parseSLImpl(source, visitor);
        };

        OperationNodes<SLOperationRootNode> nodes;
        if (FORCE_SERIALIZE) {
            try {
                byte[] serializedData = SLOperationSerialization.serializeNodes(slParser);
                nodes = SLOperationSerialization.deserializeNodes(language, serializedData);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        } else {
            nodes = SLOperationRootNodeGen.create(OperationConfig.WITH_SOURCE, slParser);
        }

        for (SLOperationRootNode node : nodes.getNodes()) {
            TruffleString name = node.getTSName();
            RootCallTarget callTarget = node.getCallTarget();
            functions.put(name, callTarget);

            if (DO_LOG_NODE_CREATION) {
                try {
                    System./**/out.println("----------------------------------------------");
                    System./**/out.printf(" Node: %s%n", name);
                    System./**/out.println(node.dump());
                    System./**/out.println("----------------------------------------------");
                } catch (Exception ex) {
                    System./**/out.println("error while dumping: ");
                    ex.printStackTrace(System.out);
                }
            }
        }
    }

    public static Map<TruffleString, RootCallTarget> parseSL(SLLanguage language, Source source) {
        Map<TruffleString, RootCallTarget> roots = new HashMap<>();
        parseSL(language, source, roots);
        return roots;
    }

    private SLOperationsVisitor(SLLanguage language, Source source, SLOperationRootNodeGen.Builder builder) {
        super(language, source);
        this.b = builder;
    }

    private final SLOperationRootNodeGen.Builder b;

    private OperationLabel breakLabel;
    private OperationLabel continueLabel;

    private final ArrayList<OperationLocal> locals = new ArrayList<>();

    @Override
    public Void visit(ParseTree tree) {
        int sourceStart;
        int sourceEnd;

        if (tree instanceof ParserRuleContext) {
            ParserRuleContext ctx = (ParserRuleContext) tree;
            sourceStart = ctx.getStart().getStartIndex();
            sourceEnd = ctx.getStop().getStopIndex() + 1;
        } else if (tree instanceof TerminalNode) {
            TerminalNode node = (TerminalNode) tree;
            sourceStart = node.getSymbol().getStartIndex();
            sourceEnd = node.getSymbol().getStopIndex() + 1;
        } else {
            throw new AssertionError("unknown tree type: " + tree);
        }

        b.beginSourceSection(sourceStart, sourceEnd - sourceStart);
        super.visit(tree);
        b.endSourceSection();
        return null;
    }

    @Override
    public Void visitFunction(FunctionContext ctx) {
        TruffleString name = asTruffleString(ctx.IDENTIFIER(0).getSymbol(), false);
        b.beginRoot(language);

// b.setMethodName(name);

        b.beginSource(source);
        b.beginTag(StandardTags.RootTag.class);
        b.beginBlock();

        int numArguments = enterFunction(ctx).size();

        for (int i = 0; i < numArguments; i++) {
            OperationLocal argLocal = b.createLocal();
            locals.add(argLocal);

            b.beginStoreLocal(argLocal);
            b.emitLoadArgument(i);
            b.endStoreLocal();
        }

        b.beginTag(StandardTags.RootBodyTag.class);
        b.beginBlock();

        visit(ctx.body);

        exitFunction();
        locals.clear();

        b.endBlock();
        b.endTag();

        b.beginReturn();
        b.emitLoadConstant(SLNull.SINGLETON);
        b.endReturn();

        b.endBlock();
        b.endTag();
        b.endSource();

        SLOperationRootNode node = b.endRoot();
        node.setTSName(name);

        return null;
    }

    @Override
    public Void visitBlock(BlockContext ctx) {
        b.beginBlock();

        int numLocals = enterBlock(ctx).size();
        for (int i = 0; i < numLocals; i++) {
            locals.add(b.createLocal());
        }

        for (StatementContext child : ctx.statement()) {
            visit(child);
        }

        exitBlock();

        b.endBlock();
        return null;
    }

    @Override
    public Void visitBreak_statement(Break_statementContext ctx) {
        if (breakLabel == null) {
            semErr(ctx.b, "break used outside of loop");
        }

        b.beginTag(StandardTags.StatementTag.class);
        b.emitBranch(breakLabel);
        b.endTag();

        return null;
    }

    @Override
    public Void visitContinue_statement(Continue_statementContext ctx) {
        if (continueLabel == null) {
            semErr(ctx.c, "continue used outside of loop");
        }

        b.beginTag(StandardTags.StatementTag.class);
        b.emitBranch(continueLabel);
        b.endTag();

        return null;
    }

    @Override
    public Void visitDebugger_statement(Debugger_statementContext ctx) {
        b.beginTag(DebuggerTags.AlwaysHalt.class);
        b.endTag();

        return null;
    }

    @Override
    public Void visitWhile_statement(While_statementContext ctx) {
        OperationLabel oldBreak = breakLabel;
        OperationLabel oldContinue = continueLabel;

        b.beginTag(StandardTags.StatementTag.class);
        b.beginBlock();

        breakLabel = b.createLabel();
        continueLabel = b.createLabel();

        b.emitLabel(continueLabel);
        b.beginWhile();

        b.beginSLToBoolean();
        visit(ctx.condition);
        b.endSLToBoolean();

        visit(ctx.body);
        b.endWhile();
        b.emitLabel(breakLabel);

        b.endBlock();
        b.endTag();

        breakLabel = oldBreak;
        continueLabel = oldContinue;

        return null;
    }

    @Override
    public Void visitIf_statement(If_statementContext ctx) {
        b.beginTag(StandardTags.StatementTag.class);

        if (ctx.alt == null) {
            b.beginIfThen();

            b.beginSLToBoolean();
            visit(ctx.condition);
            b.endSLToBoolean();

            visit(ctx.then);
            b.endIfThen();
        } else {
            b.beginIfThenElse();

            b.beginSLToBoolean();
            visit(ctx.condition);
            b.endSLToBoolean();

            visit(ctx.then);

            visit(ctx.alt);
            b.endIfThenElse();
        }

        b.endTag();
        return null;
    }

    @Override
    public Void visitReturn_statement(Return_statementContext ctx) {
        b.beginTag(StandardTags.StatementTag.class);
        b.beginReturn();

        if (ctx.expression() == null) {
            b.emitLoadConstant(SLNull.SINGLETON);
        } else {
            visit(ctx.expression());
        }

        b.endReturn();
        b.endTag();

        return null;
    }

    @Override
    public Void visitExpression(ExpressionContext ctx) {

        b.beginTag(StandardTags.ExpressionTag.class);

        b.beginSLOr();
        for (Logic_termContext term : ctx.logic_term()) {
            visit(term);
        }
        b.endSLOr();

        b.endTag();

        return null;
    }

    @Override
    public Void visitLogic_term(Logic_termContext ctx) {

        b.beginTag(StandardTags.ExpressionTag.class);
        b.beginSLUnbox();

        b.beginSLAnd();
        for (Logic_factorContext factor : ctx.logic_factor()) {
            visit(factor);
        }
        b.endSLAnd();

        b.endSLUnbox();
        b.endTag();

        return null;
    }

    @Override
    public Void visitLogic_factor(Logic_factorContext ctx) {
        if (ctx.arithmetic().size() == 1) {
            return visit(ctx.arithmetic(0));
        }

        b.beginTag(StandardTags.ExpressionTag.class);
        b.beginSLUnbox();

        switch (ctx.OP_COMPARE().getText()) {
            case "<":
                b.beginSLLessThan();
                visit(ctx.arithmetic(0));
                visit(ctx.arithmetic(1));
                b.endSLLessThan();
                break;
            case "<=":
                b.beginSLLessOrEqual();
                visit(ctx.arithmetic(0));
                visit(ctx.arithmetic(1));
                b.endSLLessOrEqual();
                break;
            case ">":
                b.beginSLLogicalNot();
                b.beginSLLessOrEqual();
                visit(ctx.arithmetic(0));
                visit(ctx.arithmetic(1));
                b.endSLLessOrEqual();
                b.endSLLogicalNot();
                break;
            case ">=":
                b.beginSLLogicalNot();
                b.beginSLLessThan();
                visit(ctx.arithmetic(0));
                visit(ctx.arithmetic(1));
                b.endSLLessThan();
                b.endSLLogicalNot();
                break;
            case "==":
                b.beginSLEqual();
                visit(ctx.arithmetic(0));
                visit(ctx.arithmetic(1));
                b.endSLEqual();
                break;
            case "!=":
                b.beginSLLogicalNot();
                b.beginSLEqual();
                visit(ctx.arithmetic(0));
                visit(ctx.arithmetic(1));
                b.endSLEqual();
                b.endSLLogicalNot();
                break;
            default:
                throw new UnsupportedOperationException();
        }

        b.endSLUnbox();
        b.endTag();

        return null;
    }

    @Override
    public Void visitArithmetic(ArithmeticContext ctx) {

        if (!ctx.OP_ADD().isEmpty()) {
            b.beginTag(StandardTags.ExpressionTag.class);
            b.beginSLUnbox();
        }

        for (int i = ctx.OP_ADD().size() - 1; i >= 0; i--) {
            switch (ctx.OP_ADD(i).getText()) {
                case "+":
                    b.beginSLAdd();
                    break;
                case "-":
                    b.beginSLSub();
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
        }

        visit(ctx.term(0));

        for (int i = 0; i < ctx.OP_ADD().size(); i++) {
            visit(ctx.term(i + 1));

            switch (ctx.OP_ADD(i).getText()) {
                case "+":
                    b.endSLAdd();
                    break;
                case "-":
                    b.endSLSub();
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
        }

        if (!ctx.OP_ADD().isEmpty()) {
            b.endSLUnbox();
            b.endTag();
        }

        return null;
    }

    @Override
    public Void visitTerm(TermContext ctx) {
        if (!ctx.OP_MUL().isEmpty()) {
            b.beginTag(StandardTags.ExpressionTag.class);
            b.beginSLUnbox();
        }
        for (int i = ctx.OP_MUL().size() - 1; i >= 0; i--) {
            switch (ctx.OP_MUL(i).getText()) {
                case "*":
                    b.beginSLMul();
                    break;
                case "/":
                    b.beginSLDiv();
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
        }

        b.beginSLUnbox();
        visit(ctx.factor(0));
        b.endSLUnbox();

        for (int i = 0; i < ctx.OP_MUL().size(); i++) {
            b.beginSLUnbox();
            visit(ctx.factor(i + 1));
            b.endSLUnbox();

            switch (ctx.OP_MUL(i).getText()) {
                case "*":
                    b.endSLMul();
                    break;
                case "/":
                    b.endSLDiv();
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
        }

        if (!ctx.OP_MUL().isEmpty()) {
            b.endSLUnbox();
            b.endTag();
        }

        return null;
    }

    @Override
    public Void visitNameAccess(NameAccessContext ctx) {
        buildMemberExpressionRead(ctx.IDENTIFIER().getSymbol(), ctx.member_expression(), ctx.member_expression().size() - 1);
        return null;
    }

    private void buildMemberExpressionRead(Token ident, List<Member_expressionContext> members, int idx) {
        if (idx == -1) {
            int localIdx = getNameIndex(ident);
            if (localIdx != -1) {
                b.emitLoadLocal(locals.get(localIdx));
            } else {
                b.beginSLFunctionLiteral();
                b.emitLoadConstant(asTruffleString(ident, false));
                b.endSLFunctionLiteral();
            }
            return;
        }

        Member_expressionContext last = members.get(idx);

        if (last instanceof MemberCallContext) {
            MemberCallContext lastCtx = (MemberCallContext) last;
            b.beginTag(StandardTags.ExpressionTag.class);
            b.beginTag(StandardTags.CallTag.class);
            b.beginSLInvoke();

            buildMemberExpressionRead(ident, members, idx - 1);

            for (ExpressionContext arg : lastCtx.expression()) {
                visit(arg);
            }

            b.endSLInvoke();
            b.endTag();
            b.endTag();
        } else if (last instanceof MemberAssignContext) {
            MemberAssignContext lastCtx = (MemberAssignContext) last;

            buildMemberExpressionWriteBefore(ident, members, idx - 1, lastCtx.expression().start);
            visit(lastCtx.expression());
            buildMemberExpressionWriteAfter(ident, members, idx - 1);
        } else if (last instanceof MemberFieldContext) {
            MemberFieldContext lastCtx = (MemberFieldContext) last;

            b.beginTag(StandardTags.ExpressionTag.class);
            b.beginSLReadProperty();
            buildMemberExpressionRead(ident, members, idx - 1);
            b.emitLoadConstant(asTruffleString(lastCtx.IDENTIFIER().getSymbol(), false));
            b.endSLReadProperty();
            b.endTag();
        } else {
            MemberIndexContext lastCtx = (MemberIndexContext) last;

            b.beginTag(StandardTags.ExpressionTag.class);
            b.beginSLReadProperty();
            buildMemberExpressionRead(ident, members, idx - 1);
            visit(lastCtx.expression());
            b.endSLReadProperty();
            b.endTag();
        }
    }

    private final ArrayList<Integer> writeLocalsStack = new ArrayList<>();

    private void buildMemberExpressionWriteBefore(Token ident, List<Member_expressionContext> members, int idx, Token errorToken) {
        if (idx == -1) {
            int localIdx = getNameIndex(ident);
            assert localIdx != -1;
            writeLocalsStack.add(localIdx);

            b.beginBlock();
            b.beginStoreLocal(locals.get(localIdx));
            return;
        }

        Member_expressionContext last = members.get(idx);

        if (last instanceof MemberCallContext) {
            semErr(errorToken, "invalid assignment target");
        } else if (last instanceof MemberAssignContext) {
            semErr(errorToken, "invalid assignment target");
        } else if (last instanceof MemberFieldContext) {
            MemberFieldContext lastCtx = (MemberFieldContext) last;

            b.beginTag(StandardTags.ExpressionTag.class);
            b.beginSLWriteProperty();
            buildMemberExpressionRead(ident, members, idx - 1);
            b.emitLoadConstant(asTruffleString(lastCtx.IDENTIFIER().getSymbol(), false));
        } else {
            MemberIndexContext lastCtx = (MemberIndexContext) last;

            b.beginTag(StandardTags.ExpressionTag.class);
            b.beginSLWriteProperty();
            buildMemberExpressionRead(ident, members, idx - 1);
            visit(lastCtx.expression());
        }
    }

    @SuppressWarnings("unused")
    private void buildMemberExpressionWriteAfter(Token ident, List<Member_expressionContext> members, int idx) {
        if (idx == -1) {
            int localIdx = writeLocalsStack.remove(writeLocalsStack.size() - 1);
            b.endStoreLocal();
            b.emitLoadLocal(locals.get(localIdx));
            b.endBlock();
            return;
        }

        b.endSLWriteProperty();
        b.endTag();
    }

    @Override
    public Void visitStringLiteral(StringLiteralContext ctx) {
        b.emitLoadConstant(asTruffleString(ctx.STRING_LITERAL().getSymbol(), true));
        return null;
    }

    @Override
    public Void visitNumericLiteral(NumericLiteralContext ctx) {
        Object value;
        try {
            value = Long.parseLong(ctx.NUMERIC_LITERAL().getText());
        } catch (NumberFormatException ex) {
            value = new SLBigInteger(new BigInteger(ctx.NUMERIC_LITERAL().getText()));
        }
        b.emitLoadConstant(value);
        return null;
    }

}
