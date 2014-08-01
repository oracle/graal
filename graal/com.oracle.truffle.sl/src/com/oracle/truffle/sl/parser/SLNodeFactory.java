/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.sl.parser;

import java.math.*;
import java.util.*;

import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.sl.nodes.*;
import com.oracle.truffle.sl.nodes.call.*;
import com.oracle.truffle.sl.nodes.controlflow.*;
import com.oracle.truffle.sl.nodes.expression.*;
import com.oracle.truffle.sl.nodes.instrument.*;
import com.oracle.truffle.sl.nodes.local.*;
import com.oracle.truffle.sl.runtime.*;

/**
 * Helper class used by the SL {@link Parser} to create nodes. The code is factored out of the
 * automatically generated parser to keep the attributed grammar of SL small.
 */
public class SLNodeFactory {

    /**
     * Local variable names that are visible in the current block. Variables are not visible outside
     * of their defining block, to prevent the usage of undefined variables. Because of that, we can
     * decide during parsing if a name references a local variable or is a function name.
     */
    static class LexicalScope {
        protected final LexicalScope outer;
        protected final Map<String, FrameSlot> locals;

        public LexicalScope(LexicalScope outer) {
            this.outer = outer;
            this.locals = new HashMap<>();
            if (outer != null) {
                locals.putAll(outer.locals);
            }
        }
    }

    /* State while parsing a source unit. */
    private final SLContext context;
    private final Source source;

    /* State while parsing a function. */
    private String functionName;
    private int parameterCount;
    private FrameDescriptor frameDescriptor;
    private List<SLStatementNode> methodNodes;

    /* State while parsing a block. */
    private LexicalScope lexicalScope;

    private final SLNodeProber prober;

    public SLNodeFactory(SLContext context, Source source, SLNodeProber prober) {
        this.context = context;
        this.source = source;
        this.prober = prober;
    }

    public void startFunction(Token nameToken) {
        assert functionName == null;
        assert parameterCount == 0;
        assert frameDescriptor == null;
        assert lexicalScope == null;

        functionName = nameToken.val;
        frameDescriptor = new FrameDescriptor();
        methodNodes = new ArrayList<>();
        startBlock();
    }

    public void addFormalParameter(Token nameToken) {
        /*
         * Method parameters are assigned to local variables at the beginning of the method. This
         * ensures that accesses to parameters are specialized the same way as local variables are
         * specialized.
         */
        final SourceSection src = srcFromToken(nameToken);
        SLReadArgumentNode readArg = new SLReadArgumentNode(src, parameterCount);
        methodNodes.add(createAssignment(nameToken, readArg));
        parameterCount++;
    }

    public void finishFunction(SLStatementNode bodyNode) {
        methodNodes.add(bodyNode);
        // TODO (mlvdv) testing
        SLStatementNode methodBlock = finishBlock(methodNodes, -1, -1);
        assert lexicalScope == null : "Wrong scoping of blocks in parser";

        SLFunctionBodyNode functionBodyNode = new SLFunctionBodyNode(methodBlock);
        SLRootNode rootNode = new SLRootNode(frameDescriptor, functionBodyNode, functionName);

        context.getFunctionRegistry().register(functionName, rootNode);

        functionName = null;
        parameterCount = 0;
        frameDescriptor = null;
        lexicalScope = null;
    }

    public void startBlock() {
        lexicalScope = new LexicalScope(lexicalScope);
    }

    public SLStatementNode finishBlock(List<SLStatementNode> bodyNodes, int lBracePos, int length) {
        lexicalScope = lexicalScope.outer;

        List<SLStatementNode> flattenedNodes = new ArrayList<>(bodyNodes.size());
        flattenBlocks(bodyNodes, flattenedNodes);

        if (lBracePos >= 0) {
            final SourceSection src = source.createSection("block", lBracePos, length);
            return new SLBlockNode(src, flattenedNodes.toArray(new SLStatementNode[flattenedNodes.size()]));
        }
        if (flattenedNodes.size() == 0) {
            // TODO (mlvdv) for error reporting, should have the character position, even if the
            // block is empty.
            return new SLBlockNode(null, new SLStatementNode[0]);
        }
        if (flattenedNodes.size() == 1) {
            /*
             * A block containing one other node, not surrounded by braces is unnecessary, we can
             * just that other node.
             */
            return flattenedNodes.get(0);
        }
        /*
         * A "block" not surrounded by braces.
         */
        final int start = flattenedNodes.get(0).getSourceSection().getCharIndex();
        final int end = flattenedNodes.get(flattenedNodes.size() - 1).getSourceSection().getCharEndIndex();
        return new SLBlockNode(source.createSection("block", start, end - start), flattenedNodes.toArray(new SLStatementNode[flattenedNodes.size()]));
    }

    private void flattenBlocks(Iterable<? extends Node> bodyNodes, List<SLStatementNode> flattenedNodes) {
        for (Node n : bodyNodes) {
            if (n instanceof SLBlockNode) {
                flattenBlocks(n.getChildren(), flattenedNodes);
            } else {
                flattenedNodes.add((SLStatementNode) n);
            }
        }
    }

    public SLStatementNode createBreak(Token breakToken) {
        return new SLBreakNode(srcFromToken(breakToken));
    }

    public SLStatementNode createContinue(Token continueToken) {
        return new SLContinueNode(srcFromToken(continueToken));
    }

    public SLStatementNode createWhile(Token whileToken, SLExpressionNode conditionNode, SLStatementNode bodyNode) {
        final int start = whileToken.charPos;
        final int end = bodyNode.getSourceSection().getCharEndIndex();
        return new SLWhileNode(source.createSection(whileToken.val, start, end - start), conditionNode, bodyNode);
    }

    public SLStatementNode createIf(Token ifToken, SLExpressionNode conditionNode, SLStatementNode thenPartNode, SLStatementNode elsePartNode) {
        final int start = ifToken.charPos;
        final int end = elsePartNode == null ? thenPartNode.getSourceSection().getCharEndIndex() : elsePartNode.getSourceSection().getCharEndIndex();

        // if (prober != null) {
        // SLStatementNode wrappedThenNode = prober.probeAsStatement(thenPartNode);
        // // SLStatementNode wrappedElseNode = prober.probeAsStatement(elsePartNode);
        // return new SLIfNode(source.createSection(t.val, start, end - start), conditionNode,
        // wrappedThenNode, elsePartNode);
        // }

        return new SLIfNode(source.createSection(ifToken.val, start, end - start), conditionNode, thenPartNode, elsePartNode);
    }

    public SLStatementNode createReturn(Token t, SLExpressionNode valueNode) {
        final int start = t.charPos;
        final int length = valueNode == null ? t.val.length() : valueNode.getSourceSection().getCharEndIndex() - start;
        return new SLReturnNode(source.createSection(t.val, start, length), valueNode);
    }

    public SLExpressionNode createBinary(Token opToken, SLExpressionNode leftNode, SLExpressionNode rightNode) {
        int start = leftNode.getSourceSection().getCharIndex();
        int length = rightNode.getSourceSection().getCharEndIndex() - start;
        final SourceSection src = source.createSection(opToken.val, start, length);
        switch (opToken.val) {
            case "+":
                return SLAddNodeFactory.create(src, leftNode, rightNode);
            case "*":
                return SLMulNodeFactory.create(src, leftNode, rightNode);
            case "/":
                return SLDivNodeFactory.create(src, leftNode, rightNode);
            case "-":
                return SLSubNodeFactory.create(src, leftNode, rightNode);
            case "<":
                return SLLessThanNodeFactory.create(src, leftNode, rightNode);
            case "<=":
                return SLLessOrEqualNodeFactory.create(src, leftNode, rightNode);
            case ">":
                return SLLogicalNotNodeFactory.create(src, SLLessOrEqualNodeFactory.create(null, leftNode, rightNode));
            case ">=":
                return SLLogicalNotNodeFactory.create(src, SLLessThanNodeFactory.create(null, leftNode, rightNode));
            case "==":
                return SLEqualNodeFactory.create(src, leftNode, rightNode);
            case "!=":
                return SLLogicalNotNodeFactory.create(src, SLEqualNodeFactory.create(null, leftNode, rightNode));
            case "&&":
                return SLLogicalAndNodeFactory.create(src, leftNode, rightNode);
            case "||":
                return SLLogicalOrNodeFactory.create(src, leftNode, rightNode);
            default:
                throw new RuntimeException("unexpected operation: " + opToken.val);
        }
    }

    public SLExpressionNode createCall(Token nameToken, List<SLExpressionNode> parameterNodes, Token finalToken) {
        final int startPos = nameToken.charPos;
        final int endPos = finalToken.charPos + finalToken.val.length();
        final SourceSection src = source.createSection(nameToken.val, startPos, endPos - startPos);
        SLExpressionNode functionNode = createRead(nameToken);
        if (prober != null) {
            SLExpressionNode wrappedNode = prober.probeAsCall(functionNode, nameToken.val);
            return SLInvokeNode.create(src, wrappedNode, parameterNodes.toArray(new SLExpressionNode[parameterNodes.size()]));
        }
        return SLInvokeNode.create(src, functionNode, parameterNodes.toArray(new SLExpressionNode[parameterNodes.size()]));
    }

    public SLExpressionNode createAssignment(Token nameToken, SLExpressionNode valueNode) {
        FrameSlot frameSlot = frameDescriptor.findOrAddFrameSlot(nameToken.val);
        lexicalScope.locals.put(nameToken.val, frameSlot);
        final int start = nameToken.charPos;
        final int length = valueNode.getSourceSection().getCharEndIndex() - start;
        if (prober != null) {
            final SLExpressionNode wrappedNode = prober.probeAsLocalAssignment(valueNode, nameToken.val);
            return SLWriteLocalVariableNodeFactory.create(source.createSection("=", start, length), wrappedNode, frameSlot);
        }
        return SLWriteLocalVariableNodeFactory.create(source.createSection("=", start, length), valueNode, frameSlot);
    }

    public SLExpressionNode createRead(Token nameToken) {
        final FrameSlot frameSlot = lexicalScope.locals.get(nameToken.val);
        final SourceSection src = srcFromToken(nameToken);
        if (frameSlot != null) {
            /* Read of a local variable. */
            return SLReadLocalVariableNodeFactory.create(src, frameSlot);
        } else {
            /* Read of a global name. In our language, the only global names are functions. */
            return new SLFunctionLiteralNode(src, context.getFunctionRegistry().lookup(nameToken.val));
        }
    }

    public SLExpressionNode createStringLiteral(Token literalToken) {
        /* Remove the trailing and ending " */
        String literal = literalToken.val;
        assert literal.length() >= 2 && literal.startsWith("\"") && literal.endsWith("\"");
        final SourceSection src = srcFromToken(literalToken);
        literal = literal.substring(1, literal.length() - 1);

        return new SLStringLiteralNode(src, literal);
    }

    public SLExpressionNode createNumericLiteral(Token literalToken) {
        final SourceSection src = srcFromToken(literalToken);
        try {
            /* Try if the literal is small enough to fit into a long value. */
            return new SLLongLiteralNode(src, Long.parseLong(literalToken.val));
        } catch (NumberFormatException ex) {
            /* Overflow of long value, so fall back to BigInteger. */
            return new SLBigIntegerLiteralNode(src, new BigInteger(literalToken.val));
        }
    }

    public SLExpressionNode createParenExpression(SLExpressionNode expressionNode, int start, int length) {
        final SourceSection src = source.createSection("()", start, length);
        return new SLParenExpressionNode(src, expressionNode);
    }

    /**
     * Creates source description of a single token.
     */
    private SourceSection srcFromToken(Token token) {
        return source.createSection(token.val, token.charPos, token.val.length());
    }

}
