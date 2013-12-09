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
package com.oracle.truffle.sl;

import java.math.*;
import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.sl.nodes.ArithmeticNodeFactory.AddNodeFactory;
import com.oracle.truffle.sl.nodes.ArithmeticNodeFactory.DivNodeFactory;
import com.oracle.truffle.sl.nodes.ArithmeticNodeFactory.MulNodeFactory;
import com.oracle.truffle.sl.nodes.ArithmeticNodeFactory.SubNodeFactory;
import com.oracle.truffle.sl.nodes.*;
import com.oracle.truffle.sl.parser.*;
import com.oracle.truffle.sl.runtime.*;

public class SLNodeFactory {

    private final SLContext context;

    private Parser parser;
    private FrameDescriptor frameDescriptor;
    private TypedNode returnValue;

    private Source source;
    private String currentFunctionName;

    public SLNodeFactory(SLContext context) {
        this.context = context;
    }

    public void setSource(Source source) {
        this.source = source;
    }

    public void setParser(Parser parser) {
        this.parser = parser;
    }

    public void startFunction() {
        frameDescriptor = new FrameDescriptor();
    }

    public void createFunction(StatementNode body, String name, String[] parameterNames) {
        context.getFunctionRegistry().register(name, FunctionRootNode.createFunction(body, frameDescriptor, name, returnValue, parameterNames));
        this.currentFunctionName = name;
        this.returnValue = null;
    }

    private <T extends Node> T assignSource(T node) {
        node.assignSourceSection(ParserUtils.createSourceSection(source, currentFunctionName, parser));
        return node;
    }

    public TypedNode createLocal(String name) {
        return assignSource(new ReadUninitializedNode(context, frameDescriptor.findOrAddFrameSlot(name, FrameSlotKind.Int)));
    }

    public TypedNode createStringLiteral(String value) {
        return assignSource(new StringLiteralNode(value));
    }

    public TypedNode createAssignment(TypedNode read, TypedNode assignment) {
        FrameSlot slot = ((ReadUninitializedNode) read).getSlot();
        return assignSource(WriteLocalNodeFactory.create(slot, assignment));
    }

    public StatementNode createWhile(ConditionNode condition, StatementNode body) {
        return assignSource(new WhileNode(condition, body));
    }

    public StatementNode createBlock(List<StatementNode> statements) {
        return assignSource(new BlockNode(statements.toArray(new StatementNode[statements.size()])));
    }

    public TypedNode createCall(TypedNode function, TypedNode[] parameters) {
        return assignSource(CallNode.create(function, parameters));
    }

    public TypedNode createBinary(String operation, TypedNode left, TypedNode right) {
        TypedNode binary;
        switch (operation) {
            case "+":
                binary = AddNodeFactory.create(left, right);
                break;
            case "*":
                binary = MulNodeFactory.create(left, right);
                break;
            case "/":
                binary = DivNodeFactory.create(left, right);
                break;
            case "-":
                binary = SubNodeFactory.create(left, right);
                break;
            case "<":
                binary = LessThanNodeFactory.create(left, right);
                break;
            case "&&":
                binary = LogicalAndNodeFactory.create(left, right);
                break;
            default:
                throw new RuntimeException("unexpected operation: " + operation);
        }
        return assignSource(binary);
    }

    public TypedNode createNumericLiteral(String value) {
        try {
            return assignSource(new IntegerLiteralNode(Integer.parseInt(value)));
        } catch (NumberFormatException ex) {
            return assignSource(new BigIntegerLiteralNode(new BigInteger(value)));
        }
    }

    public StatementNode createReturn(TypedNode value) {
        FrameSlot slot = frameDescriptor.findOrAddFrameSlot("<retval>", FrameSlotKind.Int);
        if (returnValue == null) {
            returnValue = ReadLocalNodeFactory.create(slot);
        }
        StatementNode write = WriteLocalNodeFactory.create(slot, value);
        return assignSource(new ReturnNode(write));
    }

    public TypedNode createTernary(TypedNode condition, TypedNode thenPart, TypedNode elsePart) {
        return assignSource(TernaryNodeFactory.create(condition, thenPart, elsePart));
    }

    public StatementNode createIf(ConditionNode condition, StatementNode then, StatementNode elseNode) {
        return assignSource(IfNodeFactory.create(then, elseNode, condition));
    }

}
