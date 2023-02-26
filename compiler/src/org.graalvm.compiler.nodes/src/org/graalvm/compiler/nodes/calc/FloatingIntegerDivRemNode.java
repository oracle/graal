/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.nodes.calc;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_32;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.GraphState.StageFlag;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.GuardNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.GuardedNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.extended.MultiGuardNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringProvider;

/**
 * {@link FloatingNode} version of {@link IntegerDivRemNode} if it is known that this node cannot
 * trap nor overflow (if these concepts exist with respect to the target architecture's division
 * operation). We distinguish between two reasons to trap: a division by zero or an overflow of
 * MIN_VALUE/-1. The division by 0 is typically checked with a zeroGuard. The knowledge about the
 * dividend containing MIN_VALUE is either guaranteed by its stamp or externally injected by a
 * guard.
 */
@NodeInfo(cycles = CYCLES_32, size = SIZE_1)
public abstract class FloatingIntegerDivRemNode<OP> extends BinaryArithmeticNode<OP> implements IterableNodeType, GuardedNode, Lowerable {

    @SuppressWarnings("rawtypes") public static final NodeClass<FloatingIntegerDivRemNode> TYPE = NodeClass.create(FloatingIntegerDivRemNode.class);

    @OptionalInput(InputType.Guard) protected GuardingNode floatingGuard;

    /**
     * Construct a new FloatingIntegerDivRemNode.
     *
     * @param c the concrete subclass for this node
     * @param op the division operation of this node
     * @param x the dividend
     * @param y the divisor
     * @param floatingGuard the guard (potentially a {@link MultiGuardNode}) that represents all
     *            necessary pre-conditions to allow a floating of the previously fixed
     *            {@link IntegerDivRemNode}
     */
    protected FloatingIntegerDivRemNode(NodeClass<? extends FloatingIntegerDivRemNode<OP>> c, BinaryOp<OP> op, ValueNode x, ValueNode y, GuardingNode floatingGuard) {
        super(c, op, x, y);
        this.floatingGuard = floatingGuard;
    }

    protected FloatingIntegerDivRemNode(NodeClass<? extends FloatingIntegerDivRemNode<OP>> c, BinaryOp<OP> op, ValueNode x, ValueNode y, GuardingNode floatingGuard,
                    boolean divisionOverflowIsJVMSCompliant) {
        super(c, op, x, y);
        this.floatingGuard = floatingGuard;
        this.divisionOverflowIsJVMSCompliant = divisionOverflowIsJVMSCompliant;
    }

    /**
     * See {@link LoweringProvider#divisionOverflowIsJVMSCompliant()}.
     */
    private boolean divisionOverflowIsJVMSCompliant;

    @Override
    public GuardingNode getGuard() {
        return floatingGuard;
    }

    public void setDivisionOverflowIsJVMSCompliant() {
        this.divisionOverflowIsJVMSCompliant = true;
    }

    public boolean divisionOverflowIsJVMSCompliant() {
        return divisionOverflowIsJVMSCompliant;
    }

    @Override
    public void setGuard(GuardingNode guard) {
        updateUsagesInterface(this.floatingGuard, guard);
        this.floatingGuard = guard;
    }

    /**
     * Determine if the division operation can potentially be a division by zero, i.e., the divisor
     * stamp can contain the value {@code 0}.
     */
    protected boolean canDivideByZero() {
        IntegerStamp yStamp = (IntegerStamp) y.stamp(NodeView.DEFAULT);
        return yStamp.contains(0);
    }

    private boolean overflowVisibleSideEffect() {
        return !SignedDivNode.divisionIsJVMSCompliant(x, y, divisionOverflowIsJVMSCompliant);
    }

    @Override
    public boolean verify() {
        /*
         * Special case unconditionally deopting rem operations: Other optimziations can lead to
         * graphs where the rem operation will unconditionally deopt.
         */
        boolean guardWillAlwaysDeopt = false;
        if (getGuard() != null) {
            GuardingNode guard = getGuard();
            if (guard instanceof GuardNode && ((GuardNode) guard).willDeoptUnconditionally()) {
                guardWillAlwaysDeopt = true;
            } else if (guard instanceof FixedGuardNode && ((FixedGuardNode) guard).willDeoptUnconditionally()) {
                guardWillAlwaysDeopt = true;
            } else if (guard instanceof AbstractBeginNode) {
                AbstractBeginNode abn = (AbstractBeginNode) guard;
                if (abn.predecessor() instanceof IfNode) {
                    IfNode ifGuard = (IfNode) abn.predecessor();
                    if (ifGuard.successorWillBeEliminated(abn)) {
                        guardWillAlwaysDeopt = true;
                    }
                }
            }
            // else unknown, we cannot prove this guard is unconditionally true or false
        }
        boolean cannotDeopt = (!canDivideByZero() && !overflowVisibleSideEffect());
        boolean isAfterStage = graph().isAfterStage(StageFlag.FIXED_READS);
        GraalError.guarantee(guardWillAlwaysDeopt || cannotDeopt || isAfterStage, "Floating irem must never create an exception or trap");
        return super.verify();
    }
}
