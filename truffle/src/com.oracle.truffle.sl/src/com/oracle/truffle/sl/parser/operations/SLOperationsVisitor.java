package com.oracle.truffle.sl.parser.operations;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.debug.DebuggerTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.operation.OperationLabel;
import com.oracle.truffle.api.operation.OperationsNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.nodes.SLOperationsRootNode;
import com.oracle.truffle.sl.operations.SLOperationsBuilder;
import com.oracle.truffle.sl.parser.operations.SimpleLanguageOperationsParser.ArithmeticContext;
import com.oracle.truffle.sl.parser.operations.SimpleLanguageOperationsParser.BlockContext;
import com.oracle.truffle.sl.parser.operations.SimpleLanguageOperationsParser.Break_statementContext;
import com.oracle.truffle.sl.parser.operations.SimpleLanguageOperationsParser.Continue_statementContext;
import com.oracle.truffle.sl.parser.operations.SimpleLanguageOperationsParser.Debugger_statementContext;
import com.oracle.truffle.sl.parser.operations.SimpleLanguageOperationsParser.ExpressionContext;
import com.oracle.truffle.sl.parser.operations.SimpleLanguageOperationsParser.FunctionContext;
import com.oracle.truffle.sl.parser.operations.SimpleLanguageOperationsParser.If_statementContext;
import com.oracle.truffle.sl.parser.operations.SimpleLanguageOperationsParser.Logic_factorContext;
import com.oracle.truffle.sl.parser.operations.SimpleLanguageOperationsParser.Logic_termContext;
import com.oracle.truffle.sl.parser.operations.SimpleLanguageOperationsParser.MemberAssignContext;
import com.oracle.truffle.sl.parser.operations.SimpleLanguageOperationsParser.MemberCallContext;
import com.oracle.truffle.sl.parser.operations.SimpleLanguageOperationsParser.MemberFieldContext;
import com.oracle.truffle.sl.parser.operations.SimpleLanguageOperationsParser.MemberIndexContext;
import com.oracle.truffle.sl.parser.operations.SimpleLanguageOperationsParser.Member_expressionContext;
import com.oracle.truffle.sl.parser.operations.SimpleLanguageOperationsParser.NameAccessContext;
import com.oracle.truffle.sl.parser.operations.SimpleLanguageOperationsParser.NumericLiteralContext;
import com.oracle.truffle.sl.parser.operations.SimpleLanguageOperationsParser.Return_statementContext;
import com.oracle.truffle.sl.parser.operations.SimpleLanguageOperationsParser.StringLiteralContext;
import com.oracle.truffle.sl.parser.operations.SimpleLanguageOperationsParser.TermContext;
import com.oracle.truffle.sl.parser.operations.SimpleLanguageOperationsParser.While_statementContext;
import com.oracle.truffle.sl.runtime.SLBigNumber;
import com.oracle.truffle.sl.runtime.SLNull;

public class SLOperationsVisitor extends SLBaseVisitor {

    private static final boolean DO_LOG_NODE_CREATION = false;

    public static Map<TruffleString, RootCallTarget> parseSL(SLLanguage language, Source source, SLOperationsBuilder builder) {
        return parseSLImpl(source, new SLOperationsVisitor(language, source, builder));
    }

    private SLOperationsVisitor(SLLanguage language, Source source, SLOperationsBuilder builder) {
        super(language, source);
        this.b = builder;
    }

    private final SLOperationsBuilder b;
    private LexicalScope scope;

    private OperationLabel breakLabel;
    private OperationLabel continueLabel;

    private static class LexicalScope {
        int count;
        Map<TruffleString, Integer> names = new HashMap<>();
        LexicalScope parent;

        LexicalScope(LexicalScope parent) {
            this.parent = parent;
            count = parent == null ? 0 : parent.count;
        }

        public Integer get(TruffleString name) {
// System.out.println("get " + name);
            Integer value = names.get(name);
            if (value != null) {
                return value;
            } else if (parent != null) {
                return parent.get(name);
            } else {
                return null;
            }
        }

        public int getOrCreate(TruffleString name) {
            Integer value = get(name);
            if (value == null) {
                return create(name);
            } else {
                return value;
            }
        }

        public int create(TruffleString name) {
            int value = create();
            names.put(name, value);
            return value;
        }

        public int create() {
            return count++;
        }
    }

    @Override
    public Void visit(ParseTree tree) {
        b.beginSourceSection(tree.getSourceInterval().a);
        super.visit(tree);
        b.endSourceSection(tree.getSourceInterval().length());

        return null;
    }

    @Override
    public Void visitFunction(FunctionContext ctx) {
        assert scope == null;
        TruffleString name = asTruffleString(ctx.IDENTIFIER(0).getSymbol(), false);

        b.beginSource(source);
        b.beginInstrumentation(StandardTags.RootTag.class);

        scope = new LexicalScope(null);

        for (int i = 1; i < ctx.IDENTIFIER().size(); i++) {
            TruffleString paramName = asTruffleString(ctx.IDENTIFIER(i).getSymbol(), false);
            int idx = scope.create(paramName);

            b.beginStoreLocal(idx);
            b.emitLoadArgument(i - 1);
            b.endStoreLocal();
        }

        b.beginInstrumentation(StandardTags.RootBodyTag.class);

        visit(ctx.body);

        b.endInstrumentation();

        b.beginReturn();
        b.emitConstObject(SLNull.SINGLETON);
        b.endReturn();

        scope = scope.parent;

        assert scope == null;

        b.endInstrumentation();
        b.endSource();

        OperationsNode node = b.build();

        if (DO_LOG_NODE_CREATION) {
            try {
                System.out.println("----------------------------------------------");
                System.out.printf(" Node: %s%n", name);
                System.out.println(node.dump());
                System.out.println("----------------------------------------------");
            } catch (Exception ignored) {
            }
        }

        SLOperationsRootNode rootNode = new SLOperationsRootNode(language, node, name);

        functions.put(name, rootNode.getCallTarget());

        return null;
    }

    @Override
    public Void visitBlock(BlockContext ctx) {
        scope = new LexicalScope(scope);

        b.beginBlock();
        super.visitBlock(ctx);
        b.endBlock();

        scope = scope.parent;

        return null;
    }

    @Override
    public Void visitBreak_statement(Break_statementContext ctx) {
        if (breakLabel == null) {
            SemErr(ctx.b, "break used outside of loop");
        }

        b.beginInstrumentation(StandardTags.StatementTag.class);
        b.emitBranch(breakLabel);
        b.endInstrumentation();

        return null;
    }

    @Override
    public Void visitContinue_statement(Continue_statementContext ctx) {
        if (continueLabel == null) {
            SemErr(ctx.c, "continue used outside of loop");
        }

        b.beginInstrumentation(StandardTags.StatementTag.class);
        b.emitBranch(continueLabel);
        b.endInstrumentation();

        return null;
    }

    @Override
    public Void visitDebugger_statement(Debugger_statementContext ctx) {
        b.beginInstrumentation(DebuggerTags.AlwaysHalt.class);
        b.endInstrumentation();

        return null;
    }

    @Override
    public Void visitWhile_statement(While_statementContext ctx) {
        OperationLabel oldBreak = breakLabel;
        OperationLabel oldContinue = continueLabel;

        b.beginInstrumentation(StandardTags.StatementTag.class);

        breakLabel = b.createLabel();
        continueLabel = b.createLabel();

        b.emitLabel(continueLabel);
        b.beginWhile();

        b.beginSLToBooleanOperation();
        visit(ctx.condition);
        b.endSLToBooleanOperation();

        visit(ctx.body);
        b.endWhile();
        b.emitLabel(breakLabel);

        b.endInstrumentation();

        breakLabel = oldBreak;
        continueLabel = oldContinue;

        return null;
    }

    @Override
    public Void visitIf_statement(If_statementContext ctx) {
        b.beginInstrumentation(StandardTags.StatementTag.class);

        if (ctx.alt == null) {
            b.beginIfThen();

            b.beginSLToBooleanOperation();
            visit(ctx.condition);
            b.endSLToBooleanOperation();

            visit(ctx.then);
            b.endIfThen();
        } else {
            b.beginIfThenElse();

            b.beginSLToBooleanOperation();
            visit(ctx.condition);
            b.endSLToBooleanOperation();

            visit(ctx.then);

            visit(ctx.alt);
            b.endIfThenElse();
        }

        b.endInstrumentation();
        return null;
    }

    @Override
    public Void visitReturn_statement(Return_statementContext ctx) {
        b.beginInstrumentation(StandardTags.StatementTag.class);
        b.beginReturn();

        if (ctx.expression() == null) {
            b.emitConstObject(SLNull.SINGLETON);
        } else {
            visit(ctx.expression());
        }

        b.endReturn();
        b.endInstrumentation();

        return null;
    }

    /**
     * <pre>
     * a || b
     * </pre>
     *
     * <pre>
     * {
     *  l0 = a;
     *  l0 ? l0 : b;
     * }
     * </pre>
     */
    private void logicalOrBegin(int localIdx) {
        b.beginBlock();
        b.beginStoreLocal(localIdx);
    }

    private void logicalOrMiddle(int localIdx) {
        b.endStoreLocal();
        b.beginConditional();
        b.beginSLToBooleanOperation();
        b.emitLoadLocal(localIdx);
        b.endSLToBooleanOperation();
        b.emitLoadLocal(localIdx);
    }

    private void logicalOrEnd(@SuppressWarnings("unused") int localIdx) {
        b.endConditional();
        b.endBlock();
    }

    @Override
    public Void visitExpression(ExpressionContext ctx) {
        int numTerms = ctx.logic_term().size();

        if (numTerms == 1)
            return visit(ctx.logic_term(0));

        b.beginInstrumentation(StandardTags.ExpressionTag.class);

        int[] locals = new int[numTerms - 1];
        for (int i = 0; i < numTerms - 1; i++) {
            locals[i] = scope.create();
            logicalOrBegin(locals[i]);
        }

        for (int i = 0; i < numTerms; i++) {
            visit(ctx.logic_term(i));

            if (i != 0) {
                logicalOrEnd(locals[i - 1]);
            }

            if (i != numTerms - 1) {
                logicalOrMiddle(locals[i]);
            }
        }
        b.endInstrumentation();

        return null;
    }

    /**
     * <pre>
     * a && b
     * </pre>
     *
     * <pre>
     * {
     *  l0 = a;
     *  l0 ? b : l0;
     * }
     * </pre>
     */
    private void logicalAndBegin(int localIdx) {
        b.beginBlock();
        b.beginStoreLocal(localIdx);
    }

    private void logicalAndMiddle(int localIdx) {
        b.endStoreLocal();
        b.beginConditional();
        b.beginSLToBooleanOperation();
        b.emitLoadLocal(localIdx);
        b.endSLToBooleanOperation();
    }

    private void logicalAndEnd(int localIdx) {
        b.emitLoadLocal(localIdx);
        b.endConditional();
        b.endBlock();
    }

    @Override
    public Void visitLogic_term(Logic_termContext ctx) {
        int numTerms = ctx.logic_factor().size();

        if (numTerms == 1) {
            return visit(ctx.logic_factor(0));
        }

        b.beginInstrumentation(StandardTags.ExpressionTag.class);
        b.beginSLUnboxOperation();

        int[] locals = new int[numTerms - 1];
        for (int i = 0; i < numTerms - 1; i++) {
            locals[i] = scope.create();
            logicalAndBegin(locals[i]);
        }

        for (int i = 0; i < numTerms; i++) {
            visit(ctx.logic_factor(i));

            if (i != 0) {
                logicalAndEnd(locals[i - 1]);
            }

            if (i != numTerms - 1) {
                logicalAndMiddle(locals[i]);
            }
        }

        b.endSLUnboxOperation();
        b.endInstrumentation();

        return null;
    }

    @Override
    public Void visitLogic_factor(Logic_factorContext ctx) {
        if (ctx.arithmetic().size() == 1) {
            return visit(ctx.arithmetic(0));
        }

        b.beginInstrumentation(StandardTags.ExpressionTag.class);
        b.beginSLUnboxOperation();

        switch (ctx.OP_COMPARE().getText()) {
            case "<":
                b.beginSLLessThanOperation();
                visit(ctx.arithmetic(0));
                visit(ctx.arithmetic(1));
                b.endSLLessThanOperation();
                break;
            case "<=":
                b.beginSLLessOrEqualOperation();
                visit(ctx.arithmetic(0));
                visit(ctx.arithmetic(1));
                b.endSLLessOrEqualOperation();
                break;
            case ">":
                b.beginSLLogicalNotOperation();
                b.beginSLLessOrEqualOperation();
                visit(ctx.arithmetic(0));
                visit(ctx.arithmetic(1));
                b.endSLLessOrEqualOperation();
                b.endSLLogicalNotOperation();
                break;
            case ">=":
                b.beginSLLogicalNotOperation();
                b.beginSLLessThanOperation();
                visit(ctx.arithmetic(0));
                visit(ctx.arithmetic(1));
                b.endSLLessThanOperation();
                b.endSLLogicalNotOperation();
                break;
            case "==":
                b.beginSLEqualOperation();
                visit(ctx.arithmetic(0));
                visit(ctx.arithmetic(1));
                b.endSLEqualOperation();
                break;
            case "!=":
                b.beginSLLogicalNotOperation();
                b.beginSLEqualOperation();
                visit(ctx.arithmetic(0));
                visit(ctx.arithmetic(1));
                b.endSLEqualOperation();
                b.endSLLogicalNotOperation();
                break;
        }

        b.endSLUnboxOperation();
        b.endInstrumentation();

        return null;
    }

    @Override
    public Void visitArithmetic(ArithmeticContext ctx) {

        if (!ctx.OP_ADD().isEmpty()) {
            b.beginInstrumentation(StandardTags.ExpressionTag.class);
            b.beginSLUnboxOperation();
        }

        for (int i = ctx.OP_ADD().size() - 1; i >= 0; i--) {
            switch (ctx.OP_ADD(i).getText()) {
                case "+":
                    b.beginSLAddOperation();
                    break;
                case "-":
                    b.beginSLSubOperation();
                    break;
            }
        }

        visit(ctx.term(0));

        for (int i = 0; i < ctx.OP_ADD().size(); i++) {
            visit(ctx.term(i + 1));

            switch (ctx.OP_ADD(i).getText()) {
                case "+":
                    b.endSLAddOperation();
                    break;
                case "-":
                    b.endSLSubOperation();
                    break;
            }
        }

        if (!ctx.OP_ADD().isEmpty()) {
            b.endSLUnboxOperation();
            b.endInstrumentation();
        }

        return null;
    }

    @Override
    public Void visitTerm(TermContext ctx) {
        if (!ctx.OP_MUL().isEmpty()) {
            b.beginInstrumentation(StandardTags.ExpressionTag.class);
            b.beginSLUnboxOperation();
        }
        for (int i = ctx.OP_MUL().size() - 1; i >= 0; i--) {
            switch (ctx.OP_MUL(i).getText()) {
                case "*":
                    b.beginSLMulOperation();
                    break;
                case "/":
                    b.beginSLDivOperation();
                    break;
            }
        }

        b.beginSLUnboxOperation();
        visit(ctx.factor(0));
        b.endSLUnboxOperation();

        for (int i = 0; i < ctx.OP_MUL().size(); i++) {
            b.beginSLUnboxOperation();
            visit(ctx.factor(i + 1));
            b.endSLUnboxOperation();

            switch (ctx.OP_MUL(i).getText()) {
                case "*":
                    b.endSLMulOperation();
                    break;
                case "/":
                    b.endSLDivOperation();
                    break;
            }
        }

        if (!ctx.OP_MUL().isEmpty()) {
            b.endSLUnboxOperation();
            b.endInstrumentation();
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
            Integer localIdx = scope.get(asTruffleString(ident, false));
            if (localIdx != null) {
                b.emitLoadLocal(localIdx);
            } else {
                b.beginSLFunctionLiteralOperation();
                b.emitConstObject(asTruffleString(ident, false));
                b.endSLFunctionLiteralOperation();
            }
            return;
        }

        Member_expressionContext last = members.get(idx);

        if (last instanceof MemberCallContext) {
            MemberCallContext lastCtx = (MemberCallContext) last;
            b.beginInstrumentation(StandardTags.ExpressionTag.class);
            b.beginInstrumentation(StandardTags.CallTag.class);
            b.beginSLInvokeOperation();

            buildMemberExpressionRead(ident, members, idx - 1);

            for (ExpressionContext arg : lastCtx.expression()) {
                visit(arg);
            }

            b.endSLInvokeOperation();
            b.endInstrumentation();
            b.endInstrumentation();
        } else if (last instanceof MemberAssignContext) {
            MemberAssignContext lastCtx = (MemberAssignContext) last;

            buildMemberExpressionWriteBefore(ident, members, idx - 1, lastCtx.expression().start);
            visit(lastCtx.expression());
            buildMemberExpressionWriteAfter(ident, members, idx - 1);
        } else if (last instanceof MemberFieldContext) {
            MemberFieldContext lastCtx = (MemberFieldContext) last;

            b.beginInstrumentation(StandardTags.ExpressionTag.class);
            b.beginSLReadPropertyOperation();
            buildMemberExpressionRead(ident, members, idx - 1);
            b.emitConstObject(asTruffleString(lastCtx.IDENTIFIER().getSymbol(), false));
            b.endSLReadPropertyOperation();
            b.endInstrumentation();
        } else {
            MemberIndexContext lastCtx = (MemberIndexContext) last;

            b.beginInstrumentation(StandardTags.ExpressionTag.class);
            b.beginSLReadPropertyOperation();
            buildMemberExpressionRead(ident, members, idx - 1);
            visit(lastCtx.expression());
            b.endSLReadPropertyOperation();
            b.endInstrumentation();
        }
    }

    /**
     * <pre>
     * x = a;
     *
     * {
     *  x = a;
     *  x
     * }
     * </pre>
     */
    private void buildMemberExpressionWriteBefore(Token ident, List<Member_expressionContext> members, int idx, Token errorToken) {
        if (idx == -1) {
            int localIdx = scope.getOrCreate(asTruffleString(ident, false));
            b.beginBlock();
            b.beginStoreLocal(localIdx);
            return;
        }

        Member_expressionContext last = members.get(idx);

        if (last instanceof MemberCallContext) {
            SemErr(errorToken, "invalid assignment target");
        } else if (last instanceof MemberAssignContext) {
            SemErr(errorToken, "invalid assignment target");
        } else if (last instanceof MemberFieldContext) {
            MemberFieldContext lastCtx = (MemberFieldContext) last;

            b.beginInstrumentation(StandardTags.ExpressionTag.class);
            b.beginSLWritePropertyOperation();
            buildMemberExpressionRead(ident, members, idx - 1);
            b.emitConstObject(asTruffleString(lastCtx.IDENTIFIER().getSymbol(), false));
        } else {
            MemberIndexContext lastCtx = (MemberIndexContext) last;

            b.beginInstrumentation(StandardTags.ExpressionTag.class);
            b.beginSLWritePropertyOperation();
            buildMemberExpressionRead(ident, members, idx - 1);
            visit(lastCtx.expression());
        }
    }

    private void buildMemberExpressionWriteAfter(Token ident, @SuppressWarnings("unused") List<Member_expressionContext> members, int idx) {
        if (idx == -1) {
            int localIdx = scope.get(asTruffleString(ident, false));
            b.endStoreLocal();
            b.emitLoadLocal(localIdx);
            b.endBlock();
            return;
        }

        b.endSLWritePropertyOperation();
        b.endInstrumentation();
    }

    @Override
    public Void visitStringLiteral(StringLiteralContext ctx) {
        b.emitConstObject(asTruffleString(ctx.STRING_LITERAL().getSymbol(), true));
        return null;
    }

    @Override
    public Void visitNumericLiteral(NumericLiteralContext ctx) {
        Object value;
        try {
            value = Long.parseLong(ctx.NUMERIC_LITERAL().getText());
        } catch (NumberFormatException ex) {
            value = new SLBigNumber(new BigInteger(ctx.NUMERIC_LITERAL().getText()));
        }
        b.emitConstObject(value);
        return null;
    }

}
