/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.sl.nodes.*;
import com.oracle.truffle.sl.nodes.call.*;
import com.oracle.truffle.sl.nodes.controlflow.*;
import com.oracle.truffle.sl.nodes.expression.*;
import com.oracle.truffle.sl.nodes.local.*;
import com.oracle.truffle.sl.runtime.*;

public class SLNodeFactory {

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
    private final Parser parser;

    /* State while parsing a function. */
    private String functionName;
    private FrameDescriptor frameDescriptor;
    private List<SLStatementNode> methodNodes;

    /* State while parsing a block. */
    private LexicalScope lexicalScope;

    public SLNodeFactory(SLContext context, Source source, Parser parser) {
        this.context = context;
        this.source = source;
        this.parser = parser;
    }

    public void startFunction(String name, List<String> parameters) {
        assert functionName == null;
        assert frameDescriptor == null;
        assert lexicalScope == null;

        functionName = name;
        frameDescriptor = new FrameDescriptor();
        startBlock();

        /*
         * Method parameters are assigned to local variables at the beginning of the method. This
         * ensures that accesses to parameters are specialized the same way as local variables are
         * specialized.
         */
        methodNodes = new ArrayList<>(parameters.size());
        for (int i = 0; i < parameters.size(); i++) {
            methodNodes.add(createAssignment(parameters.get(i), new SLReadArgumentNode(i)));
        }
    }

    public void finishFunction(SLStatementNode body) {
        methodNodes.add(body);
        SLStatementNode methodBlock = finishBlock(methodNodes);
        assert lexicalScope == null : "Wrong scoping of blocks in parser";

        context.getFunctionRegistry().register(functionName, SLRootNode.createFunction(functionName, frameDescriptor, methodBlock));

        functionName = null;
        frameDescriptor = null;
        lexicalScope = null;
    }

    public void startBlock() {
        lexicalScope = new LexicalScope(lexicalScope);
    }

    public SLStatementNode finishBlock(List<SLStatementNode> statements) {
        lexicalScope = lexicalScope.outer;

        List<SLStatementNode> flattened = new ArrayList<>(statements.size());
        flattenBlocks(statements, flattened);
        if (flattened.size() == 1) {
            return flattened.get(0);
        } else {
            return assignSource(new SLBlockNode(flattened.toArray(new SLStatementNode[flattened.size()])));
        }
    }

    private void flattenBlocks(Iterable<? extends Node> statements, List<SLStatementNode> flattened) {
        for (Node statement : statements) {
            if (statement instanceof SLBlockNode) {
                flattenBlocks(statement.getChildren(), flattened);
            } else {
                flattened.add((SLStatementNode) statement);
            }
        }
    }

    private <T extends Node> T assignSource(T node) {
        assert functionName != null;
        node.assignSourceSection(ParserUtils.createSourceSection(source, functionName, parser));
        return node;
    }

    public SLExpressionNode createAssignment(String name, SLExpressionNode value) {
        FrameSlot frameSlot = frameDescriptor.findOrAddFrameSlot(name);
        lexicalScope.locals.put(name, frameSlot);
        return assignSource(WriteLocalNodeFactory.create(frameSlot, value));
    }

    public SLExpressionNode createRead(String name) {
        FrameSlot frameSlot = lexicalScope.locals.get(name);
        if (frameSlot != null) {
            /* Read of a local variable. */
            return assignSource(ReadLocalNodeFactory.create(frameSlot));
        } else {
            /* Read of a global name. In our language, the only global names are functions. */
            return new SLFunctionLiteralNode(context.getFunctionRegistry().lookup(name));
        }
    }

    public SLExpressionNode createNumericLiteral(String value) {
        try {
            return assignSource(new SLLongLiteralNode(Long.parseLong(value)));
        } catch (NumberFormatException ex) {
            return assignSource(new SLBigIntegerLiteralNode(new BigInteger(value)));
        }
    }

    public SLExpressionNode createStringLiteral(String value) {
        return assignSource(new SLStringLiteralNode(value));
    }

    public SLStatementNode createWhile(SLExpressionNode condition, SLStatementNode body) {
        return assignSource(new SLWhileNode(condition, body));
    }

    public SLStatementNode createBreak() {
        return assignSource(new SLBreakNode());
    }

    public SLStatementNode createContinue() {
        return assignSource(new SLContinueNode());
    }

    public SLExpressionNode createCall(SLExpressionNode function, List<SLExpressionNode> parameters) {
        return assignSource(SLCallNode.create(function, parameters.toArray(new SLExpressionNode[parameters.size()])));
    }

    public SLExpressionNode createBinary(String operation, SLExpressionNode left, SLExpressionNode right) {
        SLExpressionNode binary;
        switch (operation) {
            case "+":
                binary = SLAddNodeFactory.create(left, right);
                break;
            case "*":
                binary = SLMulNodeFactory.create(left, right);
                break;
            case "/":
                binary = SLDivNodeFactory.create(left, right);
                break;
            case "-":
                binary = SLSubNodeFactory.create(left, right);
                break;
            case "<":
                binary = SLLessThanNodeFactory.create(left, right);
                break;
            case "<=":
                binary = SLLessOrEqualNodeFactory.create(left, right);
                break;
            case "==":
                binary = SLEqualNodeFactory.create(left, right);
                break;
            case "!=":
                binary = SLNotEqualNodeFactory.create(left, right);
                break;
            case "&&":
                binary = SLLogicalAndNodeFactory.create(left, right);
                break;
            case "||":
                binary = SLLogicalOrNodeFactory.create(left, right);
                break;
            default:
                throw new RuntimeException("unexpected operation: " + operation);
        }
        return assignSource(binary);
    }

    public SLStatementNode createReturn(SLExpressionNode value) {
        return assignSource(new SLReturnNode(value));
    }

    public SLStatementNode createIf(SLExpressionNode condition, SLStatementNode then, SLStatementNode elseNode) {
        return assignSource(new SLIfNode(condition, then, elseNode));
    }
}
