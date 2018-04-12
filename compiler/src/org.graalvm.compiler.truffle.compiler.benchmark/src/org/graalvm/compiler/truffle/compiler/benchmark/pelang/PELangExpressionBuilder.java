/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.benchmark.pelang;

import com.oracle.truffle.api.frame.FrameDescriptor;

public class PELangExpressionBuilder {

    private final FrameDescriptor frameDescriptor = new FrameDescriptor();

    public PELangRootNode root(PELangExpressionNode bodyNode) {
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
        return PELangAddNodeGen.create(literal(left), literal(right));
    }

    public PELangExpressionNode add(String left, String right) {
        return PELangAddNodeGen.create(literal(left), literal(right));
    }

    public PELangExpressionNode equals(long value, String identifier) {
        return add(literal(-value), read(identifier));
    }

    public PELangExpressionNode block(PELangExpressionNode... bodyNodes) {
        return new PELangExpressionBlockNode(bodyNodes);
    }

    public PELangExpressionNode branch(PELangExpressionNode conditionNode, PELangExpressionNode thenNode,
                    PELangExpressionNode elseNode) {
        return new PELangIfNode(conditionNode, thenNode, elseNode);
    }

    public PELangExpressionNode branch(PELangExpressionNode conditionNode, PELangExpressionNode thenNode) {
        return branch(conditionNode, thenNode, block());
    }

    public PELangExpressionNode loop(PELangExpressionNode conditionNode, PELangExpressionNode bodyNode) {
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

    public PELangExpressionNode increment(String value, String identifier) {
        return write(add(literal(value), read(identifier)), identifier);
    }

}
