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
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public final class PELangInvokeNode extends PELangExpressionNode {

    @Child private PELangExpressionNode functionNode;
    @Children private final PELangExpressionNode[] argumentNodes;
    @Child private DirectCallNode callNode;

    public PELangInvokeNode(PELangExpressionNode functionNode, PELangExpressionNode[] argumentNodes) {
        this.functionNode = functionNode;
        this.argumentNodes = argumentNodes;
    }

    public PELangExpressionNode getFunctionNode() {
        return functionNode;
    }

    public PELangExpressionNode[] getArgumentNodes() {
        return argumentNodes;
    }

    public DirectCallNode getCallNode() {
        return callNode;
    }

    @ExplodeLoop
    @Override
    public Object executeGeneric(VirtualFrame frame) {
        CompilerAsserts.compilationConstant(argumentNodes.length);
        PELangFunction function = functionNode.evaluateFunction(frame);

        if (function.getHeader().getArgs().length == argumentNodes.length) {
            Object[] argumentValues = new Object[argumentNodes.length];

            for (int i = 0; i < argumentNodes.length; i++) {
                argumentValues[i] = argumentNodes[i].executeGeneric(frame);
            }
            if (callNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNode = insert(Truffle.getRuntime().createDirectCallNode(function.getCallTarget()));
            }
            return callNode.call(argumentValues);
        } else {
            throw new PELangException("length of function args does not match provided argument nodes", this);
        }
    }

}
