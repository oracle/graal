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
package org.graalvm.compiler.truffle.pelang;

import org.graalvm.compiler.truffle.pelang.bcf.PELangBasicBlockDispatchNode;
import org.graalvm.compiler.truffle.pelang.bcf.PELangBasicBlockNode;
import org.graalvm.compiler.truffle.pelang.bcf.PELangDoubleSuccessorNode;
import org.graalvm.compiler.truffle.pelang.bcf.PELangSingleSuccessorNode;
import org.graalvm.compiler.truffle.pelang.expr.PELangAddNodeGen;
import org.graalvm.compiler.truffle.pelang.expr.PELangEqualsNodeGen;
import org.graalvm.compiler.truffle.pelang.expr.PELangGreaterThanNodeGen;
import org.graalvm.compiler.truffle.pelang.expr.PELangLessThanNodeGen;
import org.graalvm.compiler.truffle.pelang.expr.PELangLiteralLongNode;
import org.graalvm.compiler.truffle.pelang.expr.PELangLiteralStringNode;
import org.graalvm.compiler.truffle.pelang.expr.PELangLocalReadNodeGen;
import org.graalvm.compiler.truffle.pelang.expr.PELangLocalWriteNodeGen;
import org.graalvm.compiler.truffle.pelang.expr.PELangNotNode;
import org.graalvm.compiler.truffle.pelang.ncf.PELangBlockNode;
import org.graalvm.compiler.truffle.pelang.ncf.PELangIfNode;
import org.graalvm.compiler.truffle.pelang.ncf.PELangWhileNode;

import com.oracle.truffle.api.frame.FrameDescriptor;

public class PELangBuilder {

    private final FrameDescriptor frameDescriptor = new FrameDescriptor();

    public PELangRootNode root(PELangStatementNode bodyNode) {
        return new PELangRootNode(bodyNode, frameDescriptor);
    }

    public PELangExpressionNode literal(long value) {
        return new PELangLiteralLongNode(value);
    }

    public PELangExpressionNode literal(String value) {
        return new PELangLiteralStringNode(value);
    }

    public PELangExpressionNode add(PELangExpressionNode leftNode, PELangExpressionNode rightNode) {
        return PELangAddNodeGen.create(leftNode, rightNode);
    }

    public PELangExpressionNode add(long left, long right) {
        return add(literal(left), literal(right));
    }

    public PELangExpressionNode add(String left, String right) {
        return add(literal(left), literal(right));
    }

    public PELangExpressionNode equals(PELangExpressionNode leftNode, PELangExpressionNode rightNode) {
        return PELangEqualsNodeGen.create(leftNode, rightNode);
    }

    public PELangExpressionNode not(PELangExpressionNode bodyNode) {
        return new PELangNotNode(bodyNode);
    }

    public PELangExpressionNode lessThan(PELangExpressionNode leftNode, PELangExpressionNode rightNode) {
        return PELangLessThanNodeGen.create(leftNode, rightNode);
    }

    public PELangExpressionNode greaterThan(PELangExpressionNode leftNode, PELangExpressionNode rightNode) {
        return PELangGreaterThanNodeGen.create(leftNode, rightNode);
    }

    public PELangStatementNode block(PELangStatementNode... bodyNodes) {
        return new PELangBlockNode(bodyNodes);
    }

    public PELangStatementNode branch(PELangExpressionNode conditionNode, PELangStatementNode thenNode,
                    PELangStatementNode elseNode) {
        return new PELangIfNode(conditionNode, thenNode, elseNode);
    }

    public PELangStatementNode branch(PELangExpressionNode conditionNode, PELangStatementNode thenNode) {
        return branch(conditionNode, thenNode, block());
    }

    public PELangStatementNode loop(PELangExpressionNode conditionNode, PELangStatementNode bodyNode) {
        return new PELangWhileNode(conditionNode, bodyNode);
    }

    public PELangExpressionNode read(String identifier) {
        return PELangLocalReadNodeGen.create(frameDescriptor.findOrAddFrameSlot(identifier));
    }

    public PELangExpressionNode write(PELangExpressionNode valueNode, String identifier) {
        return PELangLocalWriteNodeGen.create(valueNode, frameDescriptor.findOrAddFrameSlot(identifier));
    }

    public PELangExpressionNode write(long value, String identifier) {
        return write(literal(value), identifier);
    }

    public PELangExpressionNode write(String value, String identifier) {
        return write(literal(value), identifier);
    }

    public PELangExpressionNode increment(long value, String identifier) {
        return write(add(literal(value), read(identifier)), identifier);
    }

    public PELangExpressionNode append(String value, String identifier) {
        return write(add(literal(value), read(identifier)), identifier);
    }

    public PELangStatementNode ret(PELangExpressionNode bodyNode) {
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

}
