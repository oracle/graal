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

import java.util.Arrays;

import org.graalvm.compiler.truffle.pelang.PELangExpressionNode;
import org.graalvm.compiler.truffle.pelang.PELangRootNode;
import org.graalvm.compiler.truffle.pelang.PELangStatementNode;
import org.graalvm.compiler.truffle.pelang.bcf.PELangBasicBlockDispatchNode;
import org.graalvm.compiler.truffle.pelang.bcf.PELangBasicBlockNode;
import org.graalvm.compiler.truffle.pelang.bcf.PELangDoubleSuccessorNode;
import org.graalvm.compiler.truffle.pelang.bcf.PELangMultiSuccessorNode;
import org.graalvm.compiler.truffle.pelang.bcf.PELangSingleSuccessorNode;
import org.graalvm.compiler.truffle.pelang.expr.PELangAddNodeGen;
import org.graalvm.compiler.truffle.pelang.expr.PELangEqualsNodeGen;
import org.graalvm.compiler.truffle.pelang.expr.PELangGlobalReadNodeGen;
import org.graalvm.compiler.truffle.pelang.expr.PELangGlobalWriteNodeGen;
import org.graalvm.compiler.truffle.pelang.expr.PELangGreaterThanNodeGen;
import org.graalvm.compiler.truffle.pelang.expr.PELangLessThanNodeGen;
import org.graalvm.compiler.truffle.pelang.expr.PELangLiteralLongNode;
import org.graalvm.compiler.truffle.pelang.expr.PELangLiteralStringNode;
import org.graalvm.compiler.truffle.pelang.expr.PELangLocalReadNodeGen;
import org.graalvm.compiler.truffle.pelang.expr.PELangLocalWriteNodeGen;
import org.graalvm.compiler.truffle.pelang.expr.PELangNotNode;
import org.graalvm.compiler.truffle.pelang.ncf.PELangBlockNode;
import org.graalvm.compiler.truffle.pelang.ncf.PELangIfNode;
import org.graalvm.compiler.truffle.pelang.ncf.PELangReturnNode;
import org.graalvm.compiler.truffle.pelang.ncf.PELangSwitchNode;
import org.graalvm.compiler.truffle.pelang.ncf.PELangWhileNode;

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

    public PELangExpressionNode add(PELangExpressionNode leftNode, PELangExpressionNode rightNode) {
        return PELangAddNodeGen.create(leftNode, rightNode);
    }

    public PELangExpressionNode add(long left, long right) {
        return add(lit(left), lit(right));
    }

    public PELangExpressionNode add(String left, String right) {
        return add(lit(left), lit(right));
    }

    public PELangExpressionNode eq(PELangExpressionNode leftNode, PELangExpressionNode rightNode) {
        return PELangEqualsNodeGen.create(leftNode, rightNode);
    }

    public PELangExpressionNode not(PELangExpressionNode bodyNode) {
        return new PELangNotNode(bodyNode);
    }

    public PELangExpressionNode lt(PELangExpressionNode leftNode, PELangExpressionNode rightNode) {
        return PELangLessThanNodeGen.create(leftNode, rightNode);
    }

    public PELangExpressionNode gt(PELangExpressionNode leftNode, PELangExpressionNode rightNode) {
        return PELangGreaterThanNodeGen.create(leftNode, rightNode);
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
        return PELangLocalReadNodeGen.create(frameDescriptor.findOrAddFrameSlot(identifier));
    }

    public PELangExpressionNode writeLocal(PELangExpressionNode valueNode, String identifier) {
        return PELangLocalWriteNodeGen.create(valueNode, frameDescriptor.findOrAddFrameSlot(identifier));
    }

    public PELangExpressionNode writeLocal(long value, String identifier) {
        return writeLocal(lit(value), identifier);
    }

    public PELangExpressionNode writeLocal(String value, String identifier) {
        return writeLocal(lit(value), identifier);
    }

    public PELangExpressionNode incrementLocal(long value, String identifier) {
        return writeLocal(add(lit(value), readLocal(identifier)), identifier);
    }

    public PELangExpressionNode appendLocal(String value, String identifier) {
        return writeLocal(add(lit(value), readLocal(identifier)), identifier);
    }

    public PELangExpressionNode readGlobal(String identifier) {
        return PELangGlobalReadNodeGen.create(identifier);
    }

    public PELangExpressionNode writeGlobal(PELangExpressionNode valueNode, String identifier) {
        return PELangGlobalWriteNodeGen.create(valueNode, identifier);
    }

    public PELangExpressionNode writeGlobal(long value, String identifier) {
        return writeGlobal(lit(value), identifier);
    }

    public PELangExpressionNode writeGlobal(String value, String identifier) {
        return writeGlobal(lit(value), identifier);
    }

    public PELangExpressionNode incrementGlobal(long value, String identifier) {
        return writeGlobal(add(lit(value), readGlobal(identifier)), identifier);
    }

    public PELangExpressionNode appendGlobal(String value, String identifier) {
        return writeGlobal(add(lit(value), readGlobal(identifier)), identifier);
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
