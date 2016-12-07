/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.nodes.frame;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import com.oracle.truffle.api.frame.FrameSlot;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public abstract class VirtualFrameAccessorNode extends FixedWithNextNode {
    public static final NodeClass<VirtualFrameAccessorNode> TYPE = NodeClass.create(VirtualFrameAccessorNode.class);

    @Input protected NewFrameNode frame;

    protected final FrameSlot frameSlot;
    protected final int accessTag;

    protected VirtualFrameAccessorNode(NodeClass<? extends VirtualFrameAccessorNode> c, Stamp stamp, NewFrameNode frame, FrameSlot frameSlot, int accessTag) {
        super(c, stamp);
        this.frame = frame;
        this.frameSlot = frameSlot;
        this.accessTag = accessTag;
    }

    protected int getFrameSlotIndex() {
        return frameSlot.getIndex();
    }

    protected ValueNode getConstant(int n) {
        return frame.smallIntConstants.get(n);
    }

    protected void insertDeoptimization(VirtualizerTool tool) {
        /*
         * Escape analysis does not allow insertion of a DeoptimizeNode. We work around this
         * restriction by inserting an always-failing guard, which will be canonicalized to a
         * DeoptimizeNode later on.
         */
        LogicNode condition = LogicConstantNode.contradiction();
        tool.addNode(condition);
        JavaConstant speculation = graph().getSpeculationLog().speculate(frame.getIntrinsifyAccessorsSpeculation());
        tool.addNode(new FixedGuardNode(condition, DeoptimizationReason.RuntimeConstraint, DeoptimizationAction.InvalidateReprofile, speculation, false));

        if (getStackKind() == JavaKind.Void) {
            tool.delete();
        } else {
            /*
             * Even though all usages will be eventually dead, we need to provide a valid
             * replacement value for now.
             */
            ConstantNode unusedValue = ConstantNode.forConstant(JavaConstant.defaultForKind(getStackKind()), tool.getMetaAccessProvider());
            tool.addNode(unusedValue);
            tool.replaceWith(unusedValue);
        }
    }
}
