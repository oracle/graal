/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.nodes;

import static org.graalvm.compiler.nodeinfo.InputType.Memory;
import static org.graalvm.compiler.nodeinfo.InputType.State;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_8;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.AbstractStateSplit;
import org.graalvm.compiler.nodes.DeoptimizingNode.DeoptBefore;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.debug.ControlFlowAnchored;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.word.LocationIdentity;

/**
 * See comments in {@link CFunctionPrologueNode} for details.
 */
@NodeInfo(cycles = CYCLES_UNKNOWN, cyclesRationale = "Capturing the call state requires calls whose cost is unknown.", size = SIZE_UNKNOWN, sizeRationale = "Capturing the call state requires calls whose cost is unknown.", allowedUsageTypes = {
                Memory})
public final class CFunctionEpilogueNode extends AbstractStateSplit implements Lowerable, SingleMemoryKill, ControlFlowAnchored, DeoptBefore {
    public static final NodeClass<CFunctionEpilogueNode> TYPE = NodeClass.create(CFunctionEpilogueNode.class);

    private final int oldThreadStatus;
    /*
     * This method is called with an integer (the capture mask) and a pointer to int (the capture
     * buffer).
     *
     * This method is called from inside the CFunction prologue before transitioning back into Java.
     * This means that no transition to/from should happen and the method must be uninterruptible.
     *
     * You need to register this method for foreign call and may need to declare it as root method.
     */
    private final ForeignCallDescriptor captureFunction;
    @OptionalInput ValueNode statesToCapture;
    @OptionalInput ValueNode captureBuffer;
    /**
     * See comment in {@link CFunctionPrologueNode}.
     */
    private CFunctionEpilogueMarker marker;

    public CFunctionEpilogueNode(int oldThreadStatus, ForeignCallDescriptor captureFunction, ValueNode statesToCapture, ValueNode captureBuffer) {
        super(TYPE, StampFactory.forVoid());
        this.oldThreadStatus = oldThreadStatus;
        if ((captureFunction != null) && ((statesToCapture == null) || (captureBuffer == null))) {
            throw new IllegalArgumentException("The states to capture and capture buffer must be specified since a capture function was provided.");
        }

        if ((captureFunction == null) && ((statesToCapture != null) || (captureBuffer != null))) {
            throw new IllegalArgumentException("The states to capture and capture buffer must not be specified since a capture function was not provided.");
        }
        this.captureFunction = captureFunction;
        this.statesToCapture = statesToCapture;
        this.captureBuffer = captureBuffer;
    }

    public CFunctionEpilogueNode(int oldThreadStatus) {
        this(oldThreadStatus, null, null, null);
    }

    @Override
    protected void afterClone(Node other) {
        super.afterClone(other);
        assert marker == null : "Marker must be unique";
    }

    public CFunctionEpilogueMarker getMarker() {
        if (marker == null) {
            marker = new CFunctionEpilogueMarker();
        }
        return marker;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }

    public int getOldThreadStatus() {
        return oldThreadStatus;
    }

    public ValueNode getStatesToCapture() {
        return statesToCapture;
    }

    public ValueNode getCaptureBuffer() {
        return captureBuffer;
    }

    @NodeIntrinsic
    public static native void cFunctionEpilogue(@ConstantNodeParameter int oldThreadStatus);

    @OptionalInput(State) protected FrameState stateBefore;

    @Override
    public boolean canDeoptimize() {
        return true;
    }

    @Override
    public void setStateBefore(FrameState state) {
        updateUsages(this.stateBefore, state);
        this.stateBefore = state;
    }

    @Override
    public FrameState stateBefore() {
        return stateBefore;
    }

    @Override
    public boolean canUseAsStateDuring() {
        return true;
    }

    @Override
    public NodeCycles estimatedNodeCycles() {
        return captureBuffer == null ? CYCLES_8 : super.estimatedNodeCycles();
    }

    @Override
    protected NodeSize dynamicNodeSizeEstimate() {
        return captureBuffer == null ? SIZE_8 : super.dynamicNodeSizeEstimate();
    }

    public ForeignCallDescriptor getCaptureFunction() {
        return captureFunction;
    }
}
