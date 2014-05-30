/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.calc;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

public abstract class FloatArithmeticNode extends BinaryNode implements ArithmeticLIRLowerable {

    private final boolean isStrictFP;

    public FloatArithmeticNode(Stamp stamp, ValueNode x, ValueNode y, boolean isStrictFP) {
        super(stamp, x, y);
        assert stamp instanceof FloatStamp;
        this.isStrictFP = isStrictFP;
    }

    /**
     * Checks whether this instruction has strict fp semantics.
     * 
     * @return {@code true} if this instruction has strict fp semantics
     */
    public boolean isStrictFP() {
        return isStrictFP;
    }
}
