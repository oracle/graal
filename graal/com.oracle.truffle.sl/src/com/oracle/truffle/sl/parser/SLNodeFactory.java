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

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.impl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.sl.nodes.*;
import com.oracle.truffle.sl.nodes.call.*;
import com.oracle.truffle.sl.nodes.controlflow.*;
import com.oracle.truffle.sl.nodes.expression.*;
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

    public SLNodeFactory(SLContext context, Source source) {
        this.context = context;
        this.source = source;
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
        SLReadArgumentNode readArg = assignSource(nameToken, new SLReadArgumentNode(parameterCount));
        methodNodes.add(createAssignment(nameToken, readArg));
        parameterCount++;
    }

    public void finishFunction(SLStatementNode bodyNode) {
        methodNodes.add(bodyNode);
        SLStatementNode methodBlock = finishBlock(methodNodes);
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

    public SLStatementNode finishBlock(List<SLStatementNode> bodyNodes) {
        lexicalScope = lexicalScope.outer;

        List<SLStatementNode> flattenedNodes = new ArrayList<>(bodyNodes.size());
        flattenBlocks(bodyNodes, flattenedNodes);
        if (flattenedNodes.size() == 1) {
            /* A block containing one other node is unnecessary, we can just that other node. */
            return flattenedNodes.get(0);
        } else {
            return new SLBlockNode(flattenedNodes.toArray(new SLStatementNode[flattenedNodes.size()]));
        }
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

    public SLStatementNode createBreak(Token t) {
        return assignSource(t, new SLBreakNode());
    }

    public SLStatementNode createContinue(Token t) {
        return assignSource(t, new SLContinueNode());
    }

    public SLStatementNode createWhile(Token t, SLExpressionNode conditionNode, SLStatementNode bodyNode) {
        return assignSource(t, new SLWhileNode(conditionNode, bodyNode));
    }

    public SLStatementNode createIf(Token t, SLExpressionNode conditionNode, SLStatementNode thenPartNode, SLStatementNode elsePartNode) {
        return assignSource(t, new SLIfNode(conditionNode, thenPartNode, elsePartNode));
    }

    public SLStatementNode createReturn(Token t, SLExpressionNode valueNode) {
        return assignSource(t, new SLReturnNode(valueNode));
    }

    public SLExpressionNode createBinary(Token opToken, SLExpressionNode leftNode, SLExpressionNode rightNode) {
        switch (opToken.val) {
            case "+":
                return assignSource(opToken, SLAddNodeFactory.create(leftNode, rightNode));
            case "*":
                return assignSource(opToken, SLMulNodeFactory.create(leftNode, rightNode));
            case "/":
                return assignSource(opToken, SLDivNodeFactory.create(leftNode, rightNode));
            case "-":
                return assignSource(opToken, SLSubNodeFactory.create(leftNode, rightNode));
            case "<":
                return assignSource(opToken, SLLessThanNodeFactory.create(leftNode, rightNode));
            case "<=":
                return assignSource(opToken, SLLessOrEqualNodeFactory.create(leftNode, rightNode));
            case ">":
                return assignSource(opToken, SLLogicalNotNodeFactory.create(assignSource(opToken, SLLessOrEqualNodeFactory.create(leftNode, rightNode))));
            case ">=":
                return assignSource(opToken, SLLogicalNotNodeFactory.create(assignSource(opToken, SLLessThanNodeFactory.create(leftNode, rightNode))));
            case "==":
                return assignSource(opToken, SLEqualNodeFactory.create(leftNode, rightNode));
            case "!=":
                return assignSource(opToken, SLLogicalNotNodeFactory.create(assignSource(opToken, SLEqualNodeFactory.create(leftNode, rightNode))));
            case "&&":
                return assignSource(opToken, SLLogicalAndNodeFactory.create(leftNode, rightNode));
            case "||":
                return assignSource(opToken, SLLogicalOrNodeFactory.create(leftNode, rightNode));
            default:
                throw new RuntimeException("unexpected operation: " + opToken.val);
        }
    }

    public SLExpressionNode createCall(Token nameToken, List<SLExpressionNode> parameterNodes) {
        SLExpressionNode functionNode = createRead(nameToken);
        return assignSource(nameToken, SLCallNode.create(functionNode, parameterNodes.toArray(new SLExpressionNode[parameterNodes.size()])));
    }

    public SLExpressionNode createAssignment(Token nameToken, SLExpressionNode valueNode) {
        FrameSlot frameSlot = frameDescriptor.findOrAddFrameSlot(nameToken.val);
        lexicalScope.locals.put(nameToken.val, frameSlot);
        return assignSource(nameToken, SLWriteLocalVariableNodeFactory.create(valueNode, frameSlot));
    }

    public SLExpressionNode createRead(Token nameToken) {
        FrameSlot frameSlot = lexicalScope.locals.get(nameToken.val);
        if (frameSlot != null) {
            /* Read of a local variable. */
            return assignSource(nameToken, SLReadLocalVariableNodeFactory.create(frameSlot));
        } else {
            /* Read of a global name. In our language, the only global names are functions. */
            return assignSource(nameToken, new SLFunctionLiteralNode(context.getFunctionRegistry().lookup(nameToken.val)));
        }
    }

    public SLExpressionNode createStringLiteral(Token literalToken) {
        /* Remove the trailing and ending " */
        String literal = literalToken.val;
        assert literal.length() >= 2 && literal.startsWith("\"") && literal.endsWith("\"");
        literal = literal.substring(1, literal.length() - 1);

        return assignSource(literalToken, new SLStringLiteralNode(literal));
    }

    public SLExpressionNode createNumericLiteral(Token literalToken) {
        try {
            /* Try if the literal is small enough to fit into a long value. */
            return assignSource(literalToken, new SLLongLiteralNode(Long.parseLong(literalToken.val)));
        } catch (NumberFormatException ex) {
            /* Overflow of long value, so fall back to BigInteger. */
            return assignSource(literalToken, new SLBigIntegerLiteralNode(new BigInteger(literalToken.val)));
        }
    }

    private <T extends Node> T assignSource(Token t, T node) {
        assert functionName != null;
        assert t != null;

        int startLine = t.line;
        int startColumn = t.col;
        int charLength = t.val.length();
        SourceSection sourceSection = new DefaultSourceSection(source, functionName, startLine, startColumn, 0, charLength);

        node.assignSourceSection(sourceSection);
        return node;
    }
}
