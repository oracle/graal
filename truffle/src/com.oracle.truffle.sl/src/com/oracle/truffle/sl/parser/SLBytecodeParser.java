/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLabel;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.BytecodeTier;
import com.oracle.truffle.api.instrumentation.StandardTags.CallTag;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.ReadVariableTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootBodyTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.StandardTags.WriteVariableTag;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.bytecode.SLBytecodeRootNode;
import com.oracle.truffle.sl.bytecode.SLBytecodeRootNodeGen;
import com.oracle.truffle.sl.bytecode.SLBytecodeSerialization;
import com.oracle.truffle.sl.parser.SimpleLanguageParser.ArithmeticContext;
import com.oracle.truffle.sl.parser.SimpleLanguageParser.BlockContext;
import com.oracle.truffle.sl.parser.SimpleLanguageParser.Break_statementContext;
import com.oracle.truffle.sl.parser.SimpleLanguageParser.Continue_statementContext;
import com.oracle.truffle.sl.parser.SimpleLanguageParser.Debugger_statementContext;
import com.oracle.truffle.sl.parser.SimpleLanguageParser.ExpressionContext;
import com.oracle.truffle.sl.parser.SimpleLanguageParser.FunctionContext;
import com.oracle.truffle.sl.parser.SimpleLanguageParser.If_statementContext;
import com.oracle.truffle.sl.parser.SimpleLanguageParser.Logic_factorContext;
import com.oracle.truffle.sl.parser.SimpleLanguageParser.Logic_termContext;
import com.oracle.truffle.sl.parser.SimpleLanguageParser.MemberAssignContext;
import com.oracle.truffle.sl.parser.SimpleLanguageParser.MemberCallContext;
import com.oracle.truffle.sl.parser.SimpleLanguageParser.MemberFieldContext;
import com.oracle.truffle.sl.parser.SimpleLanguageParser.MemberIndexContext;
import com.oracle.truffle.sl.parser.SimpleLanguageParser.Member_expressionContext;
import com.oracle.truffle.sl.parser.SimpleLanguageParser.NameAccessContext;
import com.oracle.truffle.sl.parser.SimpleLanguageParser.NumericLiteralContext;
import com.oracle.truffle.sl.parser.SimpleLanguageParser.Return_statementContext;
import com.oracle.truffle.sl.parser.SimpleLanguageParser.StatementContext;
import com.oracle.truffle.sl.parser.SimpleLanguageParser.StringLiteralContext;
import com.oracle.truffle.sl.parser.SimpleLanguageParser.TermContext;
import com.oracle.truffle.sl.parser.SimpleLanguageParser.While_statementContext;
import com.oracle.truffle.sl.runtime.SLBigInteger;
import com.oracle.truffle.sl.runtime.SLNull;

/**
 * SL AST visitor that parses to Bytecode DSL bytecode.
 */
public final class SLBytecodeParser extends SLBaseParser {

    private static final boolean DO_LOG_NODE_CREATION = false;
    private static final boolean FORCE_SERIALIZE = false;
    private static final boolean FORCE_MATERIALIZE_COMPLETE = false;

    private static final Class<?>[] EXPRESSION = new Class<?>[]{ExpressionTag.class};
    private static final Class<?>[] READ_VARIABLE = new Class<?>[]{ExpressionTag.class, ReadVariableTag.class};
    private static final Class<?>[] WRITE_VARIABLE = new Class<?>[]{ExpressionTag.class, WriteVariableTag.class};
    private static final Class<?>[] STATEMENT = new Class<?>[]{StatementTag.class};
    private static final Class<?>[] CONDITION = new Class<?>[]{StatementTag.class, ExpressionTag.class};
    private static final Class<?>[] CALL = new Class<?>[]{CallTag.class, ExpressionTag.class};

    public static void parseSL(SLLanguage language, Source source, Map<TruffleString, RootCallTarget> functions) {
        BytecodeParser<SLBytecodeRootNodeGen.Builder> slParser = (b) -> {
            SLBytecodeParser visitor = new SLBytecodeParser(language, source, b);
            b.beginSource(source);
            parseSLImpl(source, visitor);
            b.endSource();
        };

        BytecodeRootNodes<SLBytecodeRootNode> nodes;
        if (FORCE_SERIALIZE) {
            try {
                byte[] serializedData = SLBytecodeSerialization.serializeNodes(slParser);
                nodes = SLBytecodeSerialization.deserializeNodes(language, serializedData);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        } else {
            if (FORCE_MATERIALIZE_COMPLETE) {
                nodes = SLBytecodeRootNodeGen.create(language, BytecodeConfig.COMPLETE, slParser);
            } else {
                nodes = SLBytecodeRootNodeGen.create(language, BytecodeConfig.DEFAULT, slParser);
            }
        }

        for (SLBytecodeRootNode node : nodes.getNodes()) {
            TruffleString name = node.getTSName();
            RootCallTarget callTarget = node.getCallTarget();
            functions.put(name, callTarget);

            BytecodeTier tier = language.getForceBytecodeTier();
            if (tier != null) {
                switch (tier) {
                    case CACHED:
                        node.getBytecodeNode().setUncachedThreshold(0);
                        break;
                    case UNCACHED:
                        node.getBytecodeNode().setUncachedThreshold(Integer.MIN_VALUE);
                        break;
                    default:
                        throw CompilerDirectives.shouldNotReachHere("Unexpected tier: " + tier);
                }
            }

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

    private SLBytecodeParser(SLLanguage language, Source source, SLBytecodeRootNodeGen.Builder builder) {
        super(language, source);
        this.b = builder;
    }

    private final SLBytecodeRootNodeGen.Builder b;

    private BytecodeLabel breakLabel;
    private BytecodeLabel continueLabel;

    private final ArrayList<Object> locals = new ArrayList<>();

    private void beginSourceSection(Token token) throws AssertionError {
        b.beginSourceSection(token.getStartIndex(), token.getText().length());
    }

    @Override
    public Void visitFunction(FunctionContext ctx) {
        Token nameToken = ctx.IDENTIFIER(0).getSymbol();
        TruffleString name = asTruffleString(nameToken, false);
        int functionStartPos = nameToken.getStartIndex();
        int functionEndPos = ctx.getStop().getStopIndex();
        b.beginSourceSection(functionStartPos, functionEndPos - functionStartPos + 1);
        b.beginRoot();

        b.beginBlock();
        int parameterCount = enterFunction(ctx).size();
        for (int i = 0; i < parameterCount; i++) {
            Token paramToken = ctx.IDENTIFIER(i + 1).getSymbol();
            TruffleString paramName = asTruffleString(paramToken, false);
            BytecodeLocal argLocal = b.createLocal(paramName, null);
            locals.add(argLocal);

            b.beginStoreLocal(argLocal);
            beginSourceSection(paramToken);
            b.emitSLLoadArgument(i);
            b.endSourceSection();
            b.endStoreLocal();
        }
        b.beginTag(RootBodyTag.class);
        b.beginBlock();
        visit(ctx.body);
        exitFunction();
        locals.clear();

        b.endBlock();

        b.endTag(RootBodyTag.class);
        b.endBlock();

        b.beginReturn();
        b.emitLoadConstant(SLNull.SINGLETON);
        b.endReturn();

        SLBytecodeRootNode node = b.endRoot();
        node.setParameterCount(parameterCount);
        node.setTSName(name);

        b.endSourceSection();
        return null;
    }

    @Override
    public Void visitBlock(BlockContext ctx) {
        b.beginBlock();

        List<TruffleString> newLocals = enterBlock(ctx);
        for (TruffleString local : newLocals) {
            locals.add(local);
        }

        for (StatementContext child : ctx.statement()) {
            visit(child);
        }

        exitBlock();

        b.toString();

        b.endBlock();
        return null;
    }

    @Override
    public Void visitStatement(StatementContext ctx) {
        ParseTree tree = ctx;
        if (tree.getChildCount() == 1) {
            tree = tree.getChild(0);
        }
        if (tree instanceof Return_statementContext ret) {
            int start = ret.getStart().getStartIndex();
            int end;
            if (ret.expression() == null) {
                end = ctx.getStop().getStopIndex();
            } else {
                end = ret.expression().getStop().getStopIndex();
            }
            beginAttribution(STATEMENT, start, end);
            super.visitStatement(ctx);
            endAttribution(STATEMENT);
        } else if (tree instanceof If_statementContext || tree instanceof While_statementContext) {
            super.visitStatement(ctx);
        } else {
            // filter ; from the source section
            if (tree.getChildCount() == 2) {
                tree = tree.getChild(0);
            }
            beginAttribution(STATEMENT, tree);
            super.visitStatement(ctx);
            endAttribution(STATEMENT);
        }

        return null;
    }

    @Override
    public Void visitBreak_statement(Break_statementContext ctx) {
        if (breakLabel == null) {
            semErr(ctx.b, "break used outside of loop");
        }

        b.emitBranch(breakLabel);

        return null;
    }

    @Override
    public Void visitContinue_statement(Continue_statementContext ctx) {
        if (continueLabel == null) {
            semErr(ctx.c, "continue used outside of loop");
        }
        b.emitBranch(continueLabel);

        return null;
    }

    @Override
    public Void visitDebugger_statement(Debugger_statementContext ctx) {
        b.emitSLAlwaysHalt();
        return null;
    }

    @Override
    public Void visitWhile_statement(While_statementContext ctx) {
        BytecodeLabel oldBreak = breakLabel;
        BytecodeLabel oldContinue = continueLabel;

        b.beginBlock();

        breakLabel = b.createLabel();
        continueLabel = b.createLabel();

        b.emitLabel(continueLabel);
        b.beginWhile();

        b.beginSLToBoolean();
        beginAttribution(CONDITION, ctx.condition);
        visit(ctx.condition);
        endAttribution(CONDITION);
        b.endSLToBoolean();

        visit(ctx.body);
        b.endWhile();
        b.emitLabel(breakLabel);

        b.endBlock();

        breakLabel = oldBreak;
        continueLabel = oldContinue;

        return null;
    }

    @Override
    public Void visitIf_statement(If_statementContext ctx) {
        if (ctx.alt == null) {
            b.beginIfThen();

            beginAttribution(CONDITION, ctx.condition);
            b.beginSLToBoolean();
            visit(ctx.condition);
            b.endSLToBoolean();
            endAttribution(CONDITION);

            visit(ctx.then);
            b.endIfThen();
        } else {
            b.beginIfThenElse();

            beginAttribution(CONDITION, ctx.condition);
            b.beginSLToBoolean();
            visit(ctx.condition);
            b.endSLToBoolean();
            endAttribution(CONDITION);

            visit(ctx.then);

            visit(ctx.alt);
            b.endIfThenElse();
        }

        return null;
    }

    @Override
    public Void visitReturn_statement(Return_statementContext ctx) {
        b.beginReturn();

        if (ctx.expression() == null) {
            b.emitLoadConstant(SLNull.SINGLETON);
        } else {
            visit(ctx.expression());
        }

        b.endReturn();

        return null;
    }

    @Override
    public Void visitExpression(ExpressionContext ctx) {
        List<Logic_termContext> terms = ctx.logic_term();
        if (terms.size() == 1) {
            visit(terms.get(0));
        } else {
            b.beginSLOr();
            emitShortCircuitOperands(terms);
            b.endSLOr();
        }
        return null;
    }

    @Override
    public Void visitLogic_term(Logic_termContext ctx) {
        List<Logic_factorContext> factors = ctx.logic_factor();
        if (factors.size() == 1) {
            visit(factors.get(0));
        } else {
            beginAttribution(EXPRESSION, ctx);
            b.beginSLAnd();
            emitShortCircuitOperands(factors);
            b.endSLAnd();
            endAttribution(EXPRESSION);
        }

        return null;
    }

    private void emitShortCircuitOperands(List<? extends ParseTree> operands) {
        for (int i = 0; i < operands.size(); i++) {
            visit(operands.get(i));
        }
    }

    @Override
    public Void visitLogic_factor(Logic_factorContext ctx) {
        if (ctx.arithmetic().size() == 1) {
            return visit(ctx.arithmetic(0));
        }

        beginAttribution(EXPRESSION, ctx);
        switch (ctx.OP_COMPARE().getText()) {
            case "<":
                b.beginSLLessThan();
                visitUnboxed(ctx.arithmetic(0));
                visitUnboxed(ctx.arithmetic(1));
                b.endSLLessThan();
                break;
            case "<=":
                b.beginSLLessOrEqual();
                visitUnboxed(ctx.arithmetic(0));
                visitUnboxed(ctx.arithmetic(1));
                b.endSLLessOrEqual();
                break;
            case ">":
                b.beginSLLogicalNot();
                b.beginSLLessOrEqual();
                visitUnboxed(ctx.arithmetic(0));
                visitUnboxed(ctx.arithmetic(1));
                b.endSLLessOrEqual();
                b.endSLLogicalNot();
                break;
            case ">=":
                b.beginSLLogicalNot();
                b.beginSLLessThan();
                visitUnboxed(ctx.arithmetic(0));
                visitUnboxed(ctx.arithmetic(1));
                b.endSLLessThan();
                b.endSLLogicalNot();
                break;
            case "==":
                b.beginSLEqual();
                visitUnboxed(ctx.arithmetic(0));
                visitUnboxed(ctx.arithmetic(1));
                b.endSLEqual();
                break;
            case "!=":
                b.beginSLLogicalNot();
                b.beginSLEqual();
                visitUnboxed(ctx.arithmetic(0));
                visitUnboxed(ctx.arithmetic(1));
                b.endSLEqual();
                b.endSLLogicalNot();
                break;
            default:
                throw new UnsupportedOperationException();
        }

        endAttribution(EXPRESSION);

        return null;
    }

    private void visitUnboxed(RuleContext ctx) {
        if (needsUnboxing(ctx)) {
            // skip unboxing for constants
            b.beginSLUnbox();
            visit(ctx);
            b.endSLUnbox();
        } else {
            visit(ctx);
        }
    }

    private boolean needsUnboxing(ParseTree tree) {
        if (tree instanceof NumericLiteralContext || tree instanceof StringLiteralContext) {
            // constants are guaranteed to be already unboxed
            return false;
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            if (needsUnboxing(tree.getChild(i))) {
                return true;
            }
        }
        return tree.getChildCount() == 0;
    }

    @Override
    public Void visitArithmetic(ArithmeticContext ctx) {

        if (!ctx.OP_ADD().isEmpty()) {
            beginAttribution(EXPRESSION, ctx);

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

            visitUnboxed(ctx.term(0));

            for (int i = 0; i < ctx.OP_ADD().size(); i++) {
                visitUnboxed(ctx.term(i + 1));

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

            endAttribution(EXPRESSION);
        } else {
            visit(ctx.term(0));
        }

        return null;
    }

    @Override
    public Void visitTerm(TermContext ctx) {
        if (!ctx.OP_MUL().isEmpty()) {

            beginAttribution(EXPRESSION, ctx);

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
            visitUnboxed(ctx.factor(0));

            for (int i = 0; i < ctx.OP_MUL().size(); i++) {
                visitUnboxed(ctx.factor(i + 1));

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

            endAttribution(EXPRESSION);

        } else {
            visit(ctx.factor(0));
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
            int localIdx = getLocalIndex(ident);
            if (localIdx != -1) {
                beginAttribution(READ_VARIABLE, ident);
                b.emitLoadLocal(accessLocal(localIdx));
                endAttribution(READ_VARIABLE);
            } else {
                beginAttribution(EXPRESSION, ident);
                b.beginSLFunctionLiteral();
                b.emitLoadConstant(asTruffleString(ident, false));
                b.endSLFunctionLiteral();
                endAttribution(EXPRESSION);
            }
            return;
        }

        Member_expressionContext last = members.get(idx);

        if (last instanceof MemberCallContext lastCtx) {
            beginAttribution(CALL, ident.getStartIndex(), lastCtx.getStop().getStopIndex());
            b.beginSLInvoke();
            buildMemberExpressionRead(ident, members, idx - 1);
            for (ExpressionContext arg : lastCtx.expression()) {
                visit(arg);
            }
            b.endSLInvoke();
            endAttribution(CALL);
        } else if (last instanceof MemberAssignContext lastCtx) {
            int index = idx - 1;

            if (index == -1) {
                int localIdx = getLocalIndex(ident);
                assert localIdx != -1;

                beginAttribution(WRITE_VARIABLE, ident.getStartIndex(), lastCtx.getStop().getStopIndex());
                BytecodeLocal local = accessLocal(localIdx);
                b.beginBlock();
                b.beginStoreLocal(local);
                visit(lastCtx.expression());
                b.endStoreLocal();
                b.emitLoadLocal(local);
                b.endBlock();
                endAttribution(WRITE_VARIABLE);

            } else {
                Member_expressionContext last1 = members.get(index);

                if (last1 instanceof MemberCallContext) {
                    semErr(lastCtx.expression().start, "invalid assignment target");
                } else if (last1 instanceof MemberAssignContext) {
                    semErr(lastCtx.expression().start, "invalid assignment target");
                } else if (last1 instanceof MemberFieldContext lastCtx1) {
                    b.beginSLWriteProperty();
                    buildMemberExpressionRead(ident, members, index - 1);
                    b.emitLoadConstant(asTruffleString(lastCtx1.IDENTIFIER().getSymbol(), false));
                    visit(lastCtx.expression());
                    b.endSLWriteProperty();

                } else {
                    MemberIndexContext lastCtx2 = (MemberIndexContext) last1;

                    b.beginSLWriteProperty();
                    buildMemberExpressionRead(ident, members, index - 1);
                    visit(lastCtx2.expression());
                    visit(lastCtx.expression());
                    b.endSLWriteProperty();
                }

            }
        } else if (last instanceof MemberFieldContext lastCtx) {
            b.beginSLReadProperty();
            buildMemberExpressionRead(ident, members, idx - 1);
            b.emitLoadConstant(asTruffleString(lastCtx.IDENTIFIER().getSymbol(), false));
            b.endSLReadProperty();
        } else {
            MemberIndexContext lastCtx = (MemberIndexContext) last;

            b.beginSLReadProperty();
            buildMemberExpressionRead(ident, members, idx - 1);
            visit(lastCtx.expression());
            b.endSLReadProperty();
        }
    }

    private BytecodeLocal accessLocal(int localIdx) {
        Object local = locals.get(localIdx);
        if (local instanceof TruffleString s) {
            local = b.createLocal(s, null);
            locals.set(localIdx, local);
        }
        return (BytecodeLocal) local;
    }

    @Override
    public Void visitStringLiteral(StringLiteralContext ctx) {
        beginAttribution(EXPRESSION, ctx);
        b.emitLoadConstant(asTruffleString(ctx.STRING_LITERAL().getSymbol(), true));
        endAttribution(EXPRESSION);
        return null;
    }

    @Override
    public Void visitNumericLiteral(NumericLiteralContext ctx) {
        beginAttribution(EXPRESSION, ctx);
        Object value;
        try {
            value = Long.parseLong(ctx.NUMERIC_LITERAL().getText());
        } catch (NumberFormatException ex) {
            value = new SLBigInteger(new BigInteger(ctx.NUMERIC_LITERAL().getText()));
        }
        b.emitLoadConstant(value);
        endAttribution(EXPRESSION);
        return null;
    }

    private void beginAttribution(Class<?>[] tags, ParseTree tree) {
        beginAttribution(tags, getStartIndex(tree), getEndIndex(tree));
    }

    private static int getEndIndex(ParseTree tree) {
        if (tree instanceof ParserRuleContext ctx) {
            return ctx.getStop().getStopIndex();
        } else if (tree instanceof TerminalNode node) {
            return node.getSymbol().getStopIndex();
        } else {
            throw new AssertionError("unknown tree type: " + tree);
        }
    }

    private static int getStartIndex(ParseTree tree) {
        if (tree instanceof ParserRuleContext ctx) {
            return ctx.getStart().getStartIndex();
        } else if (tree instanceof TerminalNode node) {
            return node.getSymbol().getStartIndex();
        } else {
            throw new AssertionError("unknown tree type: " + tree);
        }
    }

    private void beginAttribution(Class<?>[] tags, Token token) {
        beginAttribution(tags, token.getStartIndex(), token.getStopIndex());
    }

    ArrayDeque<Class<?>[]> tagStack = new ArrayDeque<>();

    private void beginAttribution(Class<?>[] tags, int start, int end) {
        boolean parentCondition = tagStack.peek() == CONDITION;
        tagStack.push(tags);
        if (parentCondition) {
            return;
        }
        beginSourceSection(start, end);
        b.beginTag(tags);
    }

    private void beginSourceSection(int start, int end) {
        int length = end - start + 1;
        assert length >= 0;
        b.beginSourceSection(start, length);
    }

    private void endAttribution(Class<?>[] tags) {
        tagStack.pop();
        boolean parentCondition = tagStack.peek() == CONDITION;
        if (parentCondition) {
            return;
        }
        b.endTag(tags);
        b.endSourceSection();
    }

}
