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
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLabel;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.debug.DebuggerTags;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.instrumentation.StandardTags.CallTag;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.bytecode.SLBytecodeRootNode;
import com.oracle.truffle.sl.bytecode.SLBytecodeRootNodeGen;
import com.oracle.truffle.sl.bytecode.SLBytecodeSerialization;
import com.oracle.truffle.sl.parser.SimpleLanguageBytecodeParser.ArithmeticContext;
import com.oracle.truffle.sl.parser.SimpleLanguageBytecodeParser.BlockContext;
import com.oracle.truffle.sl.parser.SimpleLanguageBytecodeParser.Break_statementContext;
import com.oracle.truffle.sl.parser.SimpleLanguageBytecodeParser.Continue_statementContext;
import com.oracle.truffle.sl.parser.SimpleLanguageBytecodeParser.Debugger_statementContext;
import com.oracle.truffle.sl.parser.SimpleLanguageBytecodeParser.ExpressionContext;
import com.oracle.truffle.sl.parser.SimpleLanguageBytecodeParser.FunctionContext;
import com.oracle.truffle.sl.parser.SimpleLanguageBytecodeParser.If_statementContext;
import com.oracle.truffle.sl.parser.SimpleLanguageBytecodeParser.Logic_factorContext;
import com.oracle.truffle.sl.parser.SimpleLanguageBytecodeParser.Logic_termContext;
import com.oracle.truffle.sl.parser.SimpleLanguageBytecodeParser.MemberAssignContext;
import com.oracle.truffle.sl.parser.SimpleLanguageBytecodeParser.MemberCallContext;
import com.oracle.truffle.sl.parser.SimpleLanguageBytecodeParser.MemberFieldContext;
import com.oracle.truffle.sl.parser.SimpleLanguageBytecodeParser.MemberIndexContext;
import com.oracle.truffle.sl.parser.SimpleLanguageBytecodeParser.Member_expressionContext;
import com.oracle.truffle.sl.parser.SimpleLanguageBytecodeParser.NameAccessContext;
import com.oracle.truffle.sl.parser.SimpleLanguageBytecodeParser.NumericLiteralContext;
import com.oracle.truffle.sl.parser.SimpleLanguageBytecodeParser.Return_statementContext;
import com.oracle.truffle.sl.parser.SimpleLanguageBytecodeParser.StatementContext;
import com.oracle.truffle.sl.parser.SimpleLanguageBytecodeParser.StringLiteralContext;
import com.oracle.truffle.sl.parser.SimpleLanguageBytecodeParser.TermContext;
import com.oracle.truffle.sl.parser.SimpleLanguageBytecodeParser.While_statementContext;
import com.oracle.truffle.sl.runtime.SLBigInteger;
import com.oracle.truffle.sl.runtime.SLNull;

/**
 * SL AST visitor that uses the Bytecode DSL for generating code.
 */
public final class SLBytecodeVisitor extends SLBaseVisitor {

    private static final boolean DO_LOG_NODE_CREATION = false;
    private static final boolean FORCE_SERIALIZE = false;

    private static final Class<?>[] EXPRESSION = new Class[]{ExpressionTag.class};
    private static final Class<?>[] STATEMENT = new Class[]{StatementTag.class};
    private static final Class<?>[] CALL = new Class[]{CallTag.class, ExpressionTag.class};

    public static void parseSL(SLLanguage language, Source source, Map<TruffleString, RootCallTarget> functions) {
        BytecodeParser<SLBytecodeRootNodeGen.Builder> slParser = (b) -> {
            SLBytecodeVisitor visitor = new SLBytecodeVisitor(language, source, b);
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
            nodes = SLBytecodeRootNodeGen.create(BytecodeConfig.DEFAULT, slParser);
        }

        for (SLBytecodeRootNode node : nodes.getNodes()) {
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

    private SLBytecodeVisitor(SLLanguage language, Source source, SLBytecodeRootNodeGen.Builder builder) {
        super(language, source);
        this.b = builder;
    }

    private final SLBytecodeRootNodeGen.Builder b;

    private BytecodeLabel breakLabel;
    private BytecodeLabel continueLabel;

    private final ArrayList<BytecodeLocal> locals = new ArrayList<>();

    @Override
    public Void visit(ParseTree tree) {
        beginSourceSection(tree);
        super.visit(tree);
        b.endSourceSection();
        return null;
    }

    private void beginSourceSection(ParseTree tree) throws AssertionError {
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
    }

    @Override
    public Void visitFunction(FunctionContext ctx) {
        TruffleString name = asTruffleString(ctx.IDENTIFIER(0).getSymbol(), false);
        beginSourceSection(ctx);

        b.beginRoot(language);
        b.beginBlock();

        int parameterCount = enterFunction(ctx).size();

        for (int i = 0; i < parameterCount; i++) {
            Token paramToken = ctx.IDENTIFIER(i + 1).getSymbol();

            TruffleString paramName = asTruffleString(paramToken, false);
            BytecodeLocal argLocal = b.createLocal(FrameSlotKind.Illegal, paramName, null);
            locals.add(argLocal);

            b.beginStoreLocal(argLocal);
            b.emitLoadArgument(i);
            b.endStoreLocal();
        }

        b.beginBlock();

        visit(ctx.body);

        exitFunction();
        locals.clear();

        b.endBlock();

        b.beginReturn();
        b.emitLoadConstant(SLNull.SINGLETON);
        b.endReturn();

        b.endBlock();

        SLBytecodeRootNode node = b.endRoot();
        node.setTSName(name);

        b.endSourceSection();
        return null;
    }

    @Override
    public Void visitBlock(BlockContext ctx) {
        b.beginBlock();

        List<TruffleString> newLocals = enterBlock(ctx);
        for (TruffleString newLocal : newLocals) {
            locals.add(b.createLocal(FrameSlotKind.Illegal, newLocal, null));
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

        b.beginTag(STATEMENT);
        b.emitBranch(breakLabel);
        b.endTag(STATEMENT);

        return null;
    }

    @Override
    public Void visitContinue_statement(Continue_statementContext ctx) {
        if (continueLabel == null) {
            semErr(ctx.c, "continue used outside of loop");
        }

        b.beginTag(STATEMENT);
        b.emitBranch(continueLabel);
        b.endTag(STATEMENT);

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
        BytecodeLabel oldBreak = breakLabel;
        BytecodeLabel oldContinue = continueLabel;

        b.beginTag(STATEMENT);
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
        b.endTag(STATEMENT);

        breakLabel = oldBreak;
        continueLabel = oldContinue;

        return null;
    }

    @Override
    public Void visitIf_statement(If_statementContext ctx) {
        b.beginTag(STATEMENT);

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

        b.endTag(STATEMENT);
        return null;
    }

    @Override
    public Void visitReturn_statement(Return_statementContext ctx) {
        b.beginTag(STATEMENT);
        b.beginReturn();

        if (ctx.expression() == null) {
            b.emitLoadConstant(SLNull.SINGLETON);
        } else {
            visit(ctx.expression());
        }

        b.endReturn();
        b.endTag(STATEMENT);

        return null;
    }

    @Override
    public Void visitExpression(ExpressionContext ctx) {
        b.beginTag(EXPRESSION);
        List<Logic_termContext> terms = ctx.logic_term();
        if (terms.size() == 1) {
            visit(terms.get(0));
        } else {
            b.beginSLOr();
            emitShortCircuitOperands(terms);
            b.endSLOr();
        }
        b.endTag(EXPRESSION);
        return null;
    }

    @Override
    public Void visitLogic_term(Logic_termContext ctx) {
        b.beginTag(EXPRESSION);
        List<Logic_factorContext> factors = ctx.logic_factor();
        if (factors.size() == 1) {
            visit(factors.get(0));
        } else {
            b.beginSLAnd();
            emitShortCircuitOperands(factors);
            b.endSLAnd();
        }
        b.endTag(EXPRESSION);

        return null;
    }

    private void emitShortCircuitOperands(List<? extends ParseTree> operands) {
        for (int i = 0; i < operands.size(); i++) {
            if (i == operands.size() - 1) {
                // Short circuit operations don't convert the last operand to a boolean.
                b.beginSLToBoolean();
                visit(operands.get(i));
                b.endSLToBoolean();
            } else {
                visit(operands.get(i));
            }
        }
    }

    @Override
    public Void visitLogic_factor(Logic_factorContext ctx) {
        if (ctx.arithmetic().size() == 1) {
            return visit(ctx.arithmetic(0));
        }

        b.beginTag(EXPRESSION);

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

        b.endTag(EXPRESSION);

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
            b.beginTag(EXPRESSION);

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

            b.endTag(EXPRESSION);
        } else {
            visit(ctx.term(0));
        }

        return null;
    }

    @Override
    public Void visitTerm(TermContext ctx) {
        if (!ctx.OP_MUL().isEmpty()) {
            b.beginTag(EXPRESSION);

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

            b.endTag(EXPRESSION);
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

        if (last instanceof MemberCallContext lastCtx) {
            b.beginTag(CALL);
            b.beginSLInvoke();

            buildMemberExpressionRead(ident, members, idx - 1);

            for (ExpressionContext arg : lastCtx.expression()) {
                visit(arg);
            }

            b.endSLInvoke();
            b.endTag(CALL);
        } else if (last instanceof MemberAssignContext lastCtx) {
            int index = idx - 1;

            if (index == -1) {
                int localIdx = getNameIndex(ident);
                assert localIdx != -1;

                b.beginBlock();
                b.beginStoreLocal(locals.get(localIdx));
                visit(lastCtx.expression());
                b.endStoreLocal();
                b.emitLoadLocal(locals.get(localIdx));
                b.endBlock();

            } else {
                Member_expressionContext last1 = members.get(index);

                if (last1 instanceof MemberCallContext) {
                    semErr(lastCtx.expression().start, "invalid assignment target");
                } else if (last1 instanceof MemberAssignContext) {
                    semErr(lastCtx.expression().start, "invalid assignment target");
                } else if (last1 instanceof MemberFieldContext lastCtx1) {
                    b.beginTag(EXPRESSION);
                    b.beginSLWriteProperty();
                    buildMemberExpressionRead(ident, members, index - 1);
                    b.emitLoadConstant(asTruffleString(lastCtx1.IDENTIFIER().getSymbol(), false));
                    visit(lastCtx.expression());
                    b.endSLWriteProperty();
                    b.endTag(EXPRESSION);

                } else {
                    MemberIndexContext lastCtx2 = (MemberIndexContext) last1;

                    b.beginTag(EXPRESSION);
                    b.beginSLWriteProperty();
                    buildMemberExpressionRead(ident, members, index - 1);
                    visit(lastCtx2.expression());
                    visit(lastCtx.expression());
                    b.endSLWriteProperty();
                    b.endTag(EXPRESSION);
                }

            }
        } else if (last instanceof MemberFieldContext lastCtx) {

            b.beginTag(EXPRESSION);
            b.beginSLReadProperty();
            buildMemberExpressionRead(ident, members, idx - 1);
            b.emitLoadConstant(asTruffleString(lastCtx.IDENTIFIER().getSymbol(), false));
            b.endSLReadProperty();
            b.endTag(EXPRESSION);
        } else {
            MemberIndexContext lastCtx = (MemberIndexContext) last;

            b.beginTag(EXPRESSION);
            b.beginSLReadProperty();
            buildMemberExpressionRead(ident, members, idx - 1);
            visit(lastCtx.expression());
            b.endSLReadProperty();
            b.endTag(EXPRESSION);
        }
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
