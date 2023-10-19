/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.truffle.nodes.frame;

import static jdk.compiler.graal.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.compiler.graal.nodeinfo.NodeSize.SIZE_0;

import jdk.compiler.graal.core.common.type.Stamp;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodes.ConstantNode;
import jdk.compiler.graal.nodes.FixedGuardNode;
import jdk.compiler.graal.nodes.FixedWithNextNode;
import jdk.compiler.graal.nodes.LogicConstantNode;
import jdk.compiler.graal.nodes.LogicNode;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.debug.ControlFlowAnchored;
import jdk.compiler.graal.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import jdk.compiler.graal.nodes.spi.VirtualizerTool;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.SpeculationLog.Speculation;

@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public abstract class VirtualFrameAccessorNode extends FixedWithNextNode implements ControlFlowAnchored, VirtualFrameAccessVerificationNode {
    public static final NodeClass<VirtualFrameAccessorNode> TYPE = NodeClass.create(VirtualFrameAccessorNode.class);

    @Input protected NewFrameNode frame;

    protected final int frameSlotIndex;
    protected final int accessTag;
    protected final VirtualFrameAccessType type;
    protected final VirtualFrameAccessFlags accessFlags;

    protected VirtualFrameAccessorNode(NodeClass<? extends VirtualFrameAccessorNode> c, Stamp stamp, Receiver frame, int frameSlotIndex,
                    int accessTag, VirtualFrameAccessType type, VirtualFrameAccessFlags accessFlags) {
        this(c, stamp, (NewFrameNode) frame.get(), frameSlotIndex, accessTag, type, accessFlags);
    }

    protected VirtualFrameAccessorNode(NodeClass<? extends VirtualFrameAccessorNode> c, Stamp stamp, NewFrameNode frame, int frameSlotIndex,
                    int accessTag, VirtualFrameAccessType type, VirtualFrameAccessFlags accessFlags) {
        super(c, stamp);
        this.type = type;
        this.frame = frame;
        this.frameSlotIndex = frameSlotIndex;
        this.accessTag = accessTag;
        this.accessFlags = accessFlags;
    }

    protected final ValueNode getConstant(int n) {
        return frame.smallIntConstants.get(n);
    }

    protected final ValueNode getConstantWithStaticModifier(int n) {
        return frame.smallIntConstants.get(n | NewFrameNode.FrameSlotKindStaticTag);
    }

    @Override
    public final NewFrameNode getFrame() {
        return frame;
    }

    @Override
    public final int getFrameSlotIndex() {
        return frameSlotIndex;
    }

    @Override
    public final VirtualFrameAccessType getType() {
        return type;
    }

    public final int getAccessTag() {
        return accessTag;
    }

    protected final void insertDeoptimization(VirtualizerTool tool) {
        /*
         * Escape analysis does not allow insertion of a DeoptimizeNode. We work around this
         * restriction by inserting an always-failing guard, which will be canonicalized to a
         * DeoptimizeNode later on.
         */
        LogicNode condition = LogicConstantNode.contradiction();
        tool.addNode(condition);
        Speculation speculation = graph().getSpeculationLog().speculate(frame.getIntrinsifyAccessorsSpeculation());
        tool.addNode(new FixedGuardNode(condition, DeoptimizationReason.RuntimeConstraint, DeoptimizationAction.InvalidateReprofile, speculation, false));

        if (getStackKind() == JavaKind.Void) {
            tool.delete();
        } else {
            /*
             * Even though all usages will be eventually dead, we need to provide a valid
             * replacement value for now.
             */
            ConstantNode unusedValue = ConstantNode.forConstant(JavaConstant.defaultForKind(getStackKind()), tool.getMetaAccess());
            tool.addNode(unusedValue);
            tool.replaceWith(unusedValue);
        }
    }

    @Override
    public <State> void updateVerificationState(VirtualFrameVerificationStateUpdater<State> updater, State state) {
        assert !accessFlags.updatesFrame() : "This node modifies the frame and must override `updateVerificationState`.";
    }
}
