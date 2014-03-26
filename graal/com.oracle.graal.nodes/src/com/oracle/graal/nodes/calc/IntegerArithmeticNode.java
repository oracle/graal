/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

public abstract class IntegerArithmeticNode extends BinaryNode implements ArithmeticLIRLowerable, MemoryArithmeticLIRLowerable {

    public IntegerArithmeticNode(Stamp stamp, ValueNode x, ValueNode y) {
        super(stamp, x, y);
        assert stamp instanceof IntegerStamp;
    }

    public static IntegerAddNode add(StructuredGraph graph, ValueNode v1, ValueNode v2) {
        return graph.unique(new IntegerAddNode(StampTool.add(v1.stamp(), v2.stamp()), v1, v2));
    }

    public static IntegerMulNode mul(StructuredGraph graph, ValueNode v1, ValueNode v2) {
        assert v1.stamp().isCompatible(v2.stamp());
        return graph.unique(new IntegerMulNode(v1.stamp().unrestricted(), v1, v2));
    }

    public static IntegerSubNode sub(StructuredGraph graph, ValueNode v1, ValueNode v2) {
        return graph.unique(new IntegerSubNode(StampTool.sub(v1.stamp(), v2.stamp()), v1, v2));
    }
}
