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
package org.graalvm.compiler.truffle.pelang.expr;

import org.graalvm.compiler.truffle.pelang.PELangException;
import org.graalvm.compiler.truffle.pelang.PELangExpressionNode;
import org.graalvm.compiler.truffle.pelang.PELangFunction;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;

public final class PELangInvokeNode extends PELangExpressionNode {

    @Child private PELangExpressionNode expressionNode;
    @Children private final PELangExpressionNode[] argumentNodes;
    @Child private IndirectCallNode callNode;

    public PELangInvokeNode(PELangExpressionNode expressionNode, PELangExpressionNode[] argumentNodes) {
        this.expressionNode = expressionNode;
        this.argumentNodes = argumentNodes;
        callNode = Truffle.getRuntime().createIndirectCallNode();
    }

    public PELangExpressionNode getExpressionNode() {
        return expressionNode;
    }

    public PELangExpressionNode[] getArgumentNodes() {
        return argumentNodes;
    }

    @ExplodeLoop
    @Override
    public Object executeGeneric(VirtualFrame frame) {
        CompilerAsserts.compilationConstant(argumentNodes.length);
        PELangFunction function = expressionNode.evaluateFunction(frame);

        if (function.getHeader().getArgs().length == argumentNodes.length) {
            Object[] argumentValues = new Object[argumentNodes.length];

            for (int i = 0; i < argumentNodes.length; i++) {
                argumentValues[i] = argumentNodes[i].executeGeneric(frame);
            }
            return callNode.call(function.getCallTarget(), argumentValues);
        } else {
            throw new PELangException("length of function args does not match provided argument nodes", this);
        }
    }

}
