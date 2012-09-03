/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.snippets.nodes;

import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.type.*;

/**
 * Delimits a control flow path in a snippet that will be connected to a
 * {@link ControlSplitNode} successor upon snippet instantiation.
 * This node can only appear in snippets with a void return type.
 */
public class JumpNode extends FixedWithNextNode {

    /**
     * Index of {@link ControlSplitNode} successor to which this label will be connected.
     */
    private final int successorIndex;

    public JumpNode(int successorIndex) {
        super(StampFactory.forVoid());
        this.successorIndex = successorIndex;
    }

    public int successorIndex() {
        return successorIndex;
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name) {
            return super.toString(Verbosity.Name) + "{" + successorIndex() + "}";
        } else {
            return super.toString(verbosity);
        }
    }

    /**
     * There must be a return statement immediately following a call to this method.
     *
     * @param successorIndex e.g. {@link IfNode#TRUE_EDGE}
     */
    @NodeIntrinsic
    public static void jump(@ConstantNodeParameter int successorIndex) {
        throw new UnsupportedOperationException();
    }
}
