/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.pelang.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.compiler.truffle.pelang.PELangExpressionNode;
import org.graalvm.compiler.truffle.pelang.PELangFunction;
import org.graalvm.compiler.truffle.pelang.PELangRootNode;
import org.graalvm.compiler.truffle.pelang.PELangStatementNode;
import org.graalvm.compiler.truffle.pelang.bcf.PELangBasicBlockDispatchNode;
import org.graalvm.compiler.truffle.pelang.bcf.PELangBasicBlockNode;
import org.graalvm.compiler.truffle.pelang.bcf.PELangDoubleSuccessorNode;
import org.graalvm.compiler.truffle.pelang.bcf.PELangMultiSuccessorNode;
import org.graalvm.compiler.truffle.pelang.bcf.PELangSingleSuccessorNode;
import org.graalvm.compiler.truffle.pelang.expr.PELangAddNode;
import org.graalvm.compiler.truffle.pelang.expr.PELangEqualsNode;
import org.graalvm.compiler.truffle.pelang.expr.PELangGreaterThanNode;
import org.graalvm.compiler.truffle.pelang.expr.PELangInvokeNode;
import org.graalvm.compiler.truffle.pelang.expr.PELangLessThanNode;
import org.graalvm.compiler.truffle.pelang.expr.PELangLiteralFunctionNode;
import org.graalvm.compiler.truffle.pelang.expr.PELangLiteralLongNode;
import org.graalvm.compiler.truffle.pelang.expr.PELangLiteralStringNode;
import org.graalvm.compiler.truffle.pelang.expr.PELangNotNode;
import org.graalvm.compiler.truffle.pelang.expr.PELangReadArgumentNode;
import org.graalvm.compiler.truffle.pelang.ncf.PELangBlockNode;
import org.graalvm.compiler.truffle.pelang.ncf.PELangIfNode;
import org.graalvm.compiler.truffle.pelang.ncf.PELangReturnNode;
import org.graalvm.compiler.truffle.pelang.ncf.PELangSwitchNode;
import org.graalvm.compiler.truffle.pelang.ncf.PELangWhileNode;
import org.graalvm.compiler.truffle.pelang.var.PELangGlobalReadNode;
import org.graalvm.compiler.truffle.pelang.var.PELangGlobalWriteNode;
import org.graalvm.compiler.truffle.pelang.var.PELangLocalReadNode;
import org.graalvm.compiler.truffle.pelang.var.PELangLocalWriteNode;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;

public class PELangBuilder {

    private final FrameDescriptor frameDescriptor = new FrameDescriptor();

    public PELangRootNode root(PELangStatementNode bodyNode) {
        return new PELangRootNode(bodyNode, frameDescriptor);
    }

    public PELangExpressionNode lit(long value) {
        return new PELangLiteralLongNode(value);
    }

    public PELangExpressionNode lit(String value) {
        return new PELangLiteralStringNode(value);
    }

    public PELangExpressionNode lit(PELangFunction function) {
        return new PELangLiteralFunctionNode(function);
    }

    public PELangFunction fn(FunctionHeader header, PELangStatementNode bodyNode) {
        PELangBuilder builder = new PELangBuilder();
        List<PELangStatementNode> bodyNodes = new ArrayList<>();

        // read arguments and make them available as local variables
        for (int i = 0; i < header.getArgs().length; i++) {
            bodyNodes.add(builder.writeLocal(builder.readArgument(i), header.getArgs()[i]));
        }
        bodyNodes.add(bodyNode);

        PELangRootNode rootNode = builder.root(builder.block(bodyNodes.stream().toArray(PELangStatementNode[]::new)));
        return new PELangFunction(header, Truffle.getRuntime().createCallTarget(rootNode));
    }

    public FunctionHeader header(String... args) {
        return new FunctionHeader(args);
    }

    public PELangExpressionNode add(PELangExpressionNode leftNode, PELangExpressionNode rightNode) {
        return PELangAddNode.create(leftNode, rightNode);
    }

    public PELangExpressionNode add(long left, long right) {
        return add(lit(left), lit(right));
    }

    public PELangExpressionNode add(String left, String right) {
        return add(lit(left), lit(right));
    }

    public PELangExpressionNode eq(PELangExpressionNode leftNode, PELangExpressionNode rightNode) {
        return PELangEqualsNode.create(leftNode, rightNode);
    }

    public PELangExpressionNode not(PELangExpressionNode bodyNode) {
        return new PELangNotNode(bodyNode);
    }

    public PELangExpressionNode lt(PELangExpressionNode leftNode, PELangExpressionNode rightNode) {
        return PELangLessThanNode.create(leftNode, rightNode);
    }

    public PELangExpressionNode gt(PELangExpressionNode leftNode, PELangExpressionNode rightNode) {
        return PELangGreaterThanNode.create(leftNode, rightNode);
    }

    public PELangStatementNode block(PELangStatementNode... bodyNodes) {
        return new PELangBlockNode(bodyNodes);
    }

    public PELangStatementNode if_(PELangExpressionNode conditionNode, PELangStatementNode thenNode,
                    PELangStatementNode elseNode) {
        return new PELangIfNode(conditionNode, thenNode, elseNode);
    }

    public PELangStatementNode if_(PELangExpressionNode conditionNode, PELangStatementNode thenNode) {
        return if_(conditionNode, thenNode, block());
    }

    public PELangStatementNode while_(PELangExpressionNode conditionNode, PELangStatementNode bodyNode) {
        return new PELangWhileNode(conditionNode, bodyNode);
    }

    public PELangStatementNode switch_(PELangExpressionNode valueNode, Case... cases) {
        PELangExpressionNode[] caseValueNodes = Arrays.stream(cases).map(Case::getValueNode).toArray(PELangExpressionNode[]::new);
        PELangStatementNode[] caseBodyNodes = Arrays.stream(cases).map(Case::getBodyNode).toArray(PELangStatementNode[]::new);

        return new PELangSwitchNode(valueNode, caseValueNodes, caseBodyNodes);
    }

    public Case case_(PELangExpressionNode valueNode, PELangStatementNode bodyNode) {
        return new Case(valueNode, bodyNode);
    }

    public PELangExpressionNode readLocal(String identifier) {
        return PELangLocalReadNode.create(frameDescriptor.findOrAddFrameSlot(identifier));
    }

    public PELangExpressionNode readArgument(int index) {
        return new PELangReadArgumentNode(index);
    }

    public PELangExpressionNode writeLocal(PELangExpressionNode valueNode, String identifier) {
        return PELangLocalWriteNode.create(valueNode, frameDescriptor.findOrAddFrameSlot(identifier));
    }

    public PELangExpressionNode writeLocal(long value, String identifier) {
        return writeLocal(lit(value), identifier);
    }

    public PELangExpressionNode writeLocal(String value, String identifier) {
        return writeLocal(lit(value), identifier);
    }

    public PELangExpressionNode writeLocal(PELangFunction function, String identifier) {
        return writeLocal(lit(function), identifier);
    }

    public PELangExpressionNode incrementLocal(long value, String identifier) {
        return writeLocal(add(lit(value), readLocal(identifier)), identifier);
    }

    public PELangExpressionNode appendLocal(String value, String identifier) {
        return writeLocal(add(lit(value), readLocal(identifier)), identifier);
    }

    public PELangExpressionNode readGlobal(String identifier) {
        return PELangGlobalReadNode.create(identifier);
    }

    public PELangExpressionNode writeGlobal(PELangExpressionNode valueNode, String identifier) {
        return PELangGlobalWriteNode.create(valueNode, identifier);
    }

    public PELangExpressionNode writeGlobal(long value, String identifier) {
        return writeGlobal(lit(value), identifier);
    }

    public PELangExpressionNode writeGlobal(String value, String identifier) {
        return writeGlobal(lit(value), identifier);
    }

    public PELangExpressionNode writeGlobal(PELangFunction function, String identifier) {
        return writeGlobal(lit(function), identifier);
    }

    public PELangExpressionNode incrementGlobal(long value, String identifier) {
        return writeGlobal(add(lit(value), readGlobal(identifier)), identifier);
    }

    public PELangExpressionNode appendGlobal(String value, String identifier) {
        return writeGlobal(add(lit(value), readGlobal(identifier)), identifier);
    }

    public PELangExpressionNode invoke(PELangExpressionNode expressionNode, PELangExpressionNode... argumentNodes) {
        return new PELangInvokeNode(expressionNode, argumentNodes);
    }

    public PELangStatementNode return_(PELangExpressionNode bodyNode) {
        return new PELangReturnNode(bodyNode);
    }

    public PELangStatementNode dispatch(PELangBasicBlockNode... blockNodes) {
        return new PELangBasicBlockDispatchNode(blockNodes);
    }

    public PELangBasicBlockNode basicBlock(PELangStatementNode bodyNode, int successor) {
        return new PELangSingleSuccessorNode(bodyNode, successor);
    }

    public PELangBasicBlockNode basicBlock(PELangExpressionNode bodyNode, int trueSuccessor, int falseSuccessor) {
        return new PELangDoubleSuccessorNode(bodyNode, trueSuccessor, falseSuccessor);
    }

    public PELangBasicBlockNode basicBlock(PELangExpressionNode valueNode, PELangExpressionNode[] caseValueNodes, int[] caseBodySuccessors, int defaultSuccessor) {
        return new PELangMultiSuccessorNode(valueNode, caseValueNodes, caseBodySuccessors, defaultSuccessor);
    }

    public static final class FunctionHeader {

        private final String[] args;

        public FunctionHeader(String... args) {
            this.args = args;
        }

        public String[] getArgs() {
            return args;
        }

    }

    public static final class Case {

        private final PELangExpressionNode valueNode;
        private final PELangStatementNode bodyNode;

        public Case(PELangExpressionNode valueNode, PELangStatementNode bodyNode) {
            this.valueNode = valueNode;
            this.bodyNode = bodyNode;
        }

        public PELangExpressionNode getValueNode() {
            return valueNode;
        }

        public PELangStatementNode getBodyNode() {
            return bodyNode;
        }

    }

}
