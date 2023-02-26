/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.nodes.arithmetic;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_2;

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.NegateNode;
import org.graalvm.compiler.nodes.extended.GuardedNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Node representing an exact integer negate that will throw an {@link ArithmeticException} in case
 * the negation would overflow the 32 bit range.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_2)
public final class IntegerNegExactNode extends NegateNode implements GuardedNode, IntegerExactArithmeticNode, IterableNodeType {

    public static final NodeClass<IntegerNegExactNode> TYPE = NodeClass.create(IntegerNegExactNode.class);

    @Input(InputType.Guard) protected GuardingNode guard;

    public IntegerNegExactNode(ValueNode value, GuardingNode guard) {
        super(TYPE, value);
        setStamp(value.stamp(NodeView.DEFAULT).unrestricted());
        this.guard = guard;
    }

    @Override
    public boolean inferStamp() {
        /*
         * Note: it is not allowed to use the foldStamp method of the regular negate node as we do
         * not know the result stamp of this node if we do not know whether we may deopt. If we know
         * we can never overflow we will replace this node with its non overflow checking
         * counterpart anyway.
         */
        return false;
    }

    @Override
    public Stamp foldStamp(Stamp newStamp) {
        IntegerStamp integerStamp = (IntegerStamp) newStamp;
        // if an overflow is possible the node will throw so do not expose bound information to
        // avoid optimizations believing (because of a precise range) that the node can be folded
        // etc
        if (IntegerStamp.negateCanOverflow(integerStamp)) {
            return integerStamp.unrestricted();
        }

        return super.foldStamp(newStamp);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (forValue.isConstant()) {
            JavaConstant cst = forValue.asJavaConstant();
            try {
                if (cst.getJavaKind() == JavaKind.Int) {
                    return ConstantNode.forInt(Math.negateExact(cst.asInt()));
                } else {
                    assert cst.getJavaKind() == JavaKind.Long;
                    return ConstantNode.forLong(Math.negateExact(cst.asLong()));
                }
            } catch (ArithmeticException ex) {
                // The operation will result in an overflow exception, so do not canonicalize.
            }
            return this;
        }
        if (!IntegerStamp.negateCanOverflow((IntegerStamp) forValue.stamp(NodeView.DEFAULT))) {
            return new NegateNode(forValue).canonical(tool);
        }
        return this;
    }

    @Override
    public GuardingNode getGuard() {
        return guard;
    }

    @Override
    public void setGuard(GuardingNode guard) {
        updateUsagesInterface(this.guard, guard);
        this.guard = guard;
    }
}
