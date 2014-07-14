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
package com.oracle.truffle.sl.nodes.expression;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.sl.nodes.*;

/**
 * This class declares specializations similar to the extensively documented {@link SLAddNode}. It
 * uses one additional feature of the Truffle DSL: {@link ShortCircuit}.
 * <p>
 * Logical operations in SL use short circuit evaluation: if the evaluation of the left operand
 * already decides the result of the operation, the right operand must not be executed. This is
 * expressed in the Truffle DSL via a method annotated with {@link ShortCircuit}, which returns
 * whether a child needs to be executed based on the result of already executed children.
 */
@NodeInfo(shortName = "&&")
@SuppressWarnings("unused")
public abstract class SLLogicalAndNode extends SLBinaryNode {

    public SLLogicalAndNode(SourceSection src) {
        super(src);
    }

    /**
     * This method is called after the left child was evaluated, but before the right child is
     * evaluated. The right child is only evaluated when the return value is {code true}.
     */
    @ShortCircuit("rightNode")
    protected boolean needsRightNode(boolean left) {
        return left;
    }

    /**
     * Similar to {@link #needsRightNode(boolean)}, but for generic cases where the type of the left
     * child is not known.
     */
    @ShortCircuit("rightNode")
    protected boolean needsRightNode(Object left) {
        return left instanceof Boolean && needsRightNode(((Boolean) left).booleanValue());
    }

    @Specialization
    protected boolean doBoolean(boolean left, boolean hasRight, boolean right) {
        return left && right;
    }
}
