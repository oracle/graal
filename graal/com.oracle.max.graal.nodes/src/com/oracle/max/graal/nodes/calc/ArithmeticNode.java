/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.nodes.calc;

import com.oracle.max.graal.nodes.*;
import com.sun.cri.ci.*;

/**
 * The {@code ArithmeticOp} class represents arithmetic operations such as addition, subtraction, etc.
 */
public abstract class ArithmeticNode extends BinaryNode {

    private final boolean isStrictFP;

    /**
     * Creates a new arithmetic operation.
     * @param kind the result kind of the operation
     * @param x the first input instruction
     * @param y the second input instruction
     * @param isStrictFP indicates this operation has strict rounding semantics
     */
    public ArithmeticNode(CiKind kind, ValueNode x, ValueNode y, boolean isStrictFP) {
        super(kind, x, y);
        this.isStrictFP = isStrictFP;
    }

    /**
     * Checks whether this instruction has strict fp semantics.
     * @return {@code true} if this instruction has strict fp semantics
     */
    public boolean isStrictFP() {
        return isStrictFP;
    }
}
