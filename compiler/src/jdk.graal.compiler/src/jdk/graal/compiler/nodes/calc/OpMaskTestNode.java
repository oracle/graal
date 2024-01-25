/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.nodes.calc;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.BinaryOpLogicNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.meta.TriState;

/**
 * This node will perform a "test" operation on its arguments similar to {@link IntegerTestNode},
 * but using vector op-masks instead of integers. If {@code invertX} is set, the result of this
 * operation is true if {@code (!x & y) == 0}, else the result is true if {@code (x & y) == 0}.
 * <p/>
 * The feature of inverting the left operand stems from the fact that this instruction is modeled
 * after the KTEST instruction defined in AVX512. KTEST sets the ZF if {@code (x & y) == 0} and the
 * CF if {@code (!x & y) == 0}. The field {@code invertX} selects the flag to jump on.
 */
@NodeInfo(cycles = CYCLES_1, size = SIZE_1)
public class OpMaskTestNode extends BinaryOpLogicNode {
    public static final NodeClass<OpMaskTestNode> TYPE = NodeClass.create(OpMaskTestNode.class);

    private final boolean invertX;

    public OpMaskTestNode(ValueNode x, ValueNode y) {
        this(x, y, false);
    }

    public OpMaskTestNode(ValueNode x, ValueNode y, boolean invertX) {
        super(TYPE, x, y);
        this.invertX = invertX;
    }

    public boolean invertX() {
        return invertX;
    }

    @Override
    public Stamp getSucceedingStampForX(boolean negated, Stamp xStamp, Stamp yStamp) {
        return null;
    }

    @Override
    public Stamp getSucceedingStampForY(boolean negated, Stamp xStamp, Stamp yStamp) {
        return null;
    }

    @Override
    public TriState tryFold(Stamp xStamp, Stamp yStamp) {
        return TriState.UNKNOWN;
    }

}
