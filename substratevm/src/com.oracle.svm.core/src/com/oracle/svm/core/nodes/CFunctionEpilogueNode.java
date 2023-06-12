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

import java.util.Set;

import org.graalvm.compiler.api.replacements.Fold;
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
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.graal.snippets.CFunctionSnippets;

/**
 * See comments in {@link CFunctionPrologueNode} for details.
 */
@NodeInfo(cycles = CYCLES_UNKNOWN, cyclesRationale = "Capturing the call state requires calls whose cost is unknown.", size = SIZE_UNKNOWN, sizeRationale = "Capturing the call state requires calls whose cost is unknown.", allowedUsageTypes = {
                Memory})
public final class CFunctionEpilogueNode extends AbstractStateSplit implements Lowerable, SingleMemoryKill, ControlFlowAnchored, DeoptBefore {
    public enum CapturableState {
        ERRNO,
        GET_LAST_ERROR,
        WSA_GET_LAST_ERROR;

        private final int mask;

        CapturableState() {
            this.mask = 1 << this.ordinal();
        }

        /**
         * We want this class to disappear from compiled code, only leaving the mask, as to avoid
         * leaking compilation objects. See
         * {@link com.oracle.svm.core.graal.snippets.CFunctionSnippets#callCaptureCallState}
         */
        @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        @Fold
        public int mask() {
            return this.mask;
        }

        public static int mask(CapturableState state) {
            return state.mask;
        }

        public static int mask(Set<CapturableState> states) {
            int mask = 0;
            for (CapturableState state : states) {
                mask |= state.mask;
            }
            return mask;
        }
    }

    public static final NodeClass<CFunctionEpilogueNode> TYPE = NodeClass.create(CFunctionEpilogueNode.class);

    private final int oldThreadStatus;
    private final Set<CapturableState> statesToCapture;
    /**
     * Call state capture is currently implemented to always be performed by
     * {@link CFunctionSnippets#captureCallState(int, CIntPointer)}, but we could instead add a call
     * parameter as parameter/member to this class to have different capture logics, if the need for
     * multiple ones ever arises.
     */
    @OptionalInput ValueNode captureBuffer;
    /**
     * See comment in {@link CFunctionPrologueNode}.
     */
    private CFunctionEpilogueMarker marker;

    public CFunctionEpilogueNode(int oldThreadStatus, Set<CapturableState> statesToCapture, ValueNode captureBuffer) {
        super(TYPE, StampFactory.forVoid());
        this.oldThreadStatus = oldThreadStatus;
        this.statesToCapture = statesToCapture;
        this.captureBuffer = captureBuffer;
        assert (statesToCapture.size() == 0) == (captureBuffer == null);
    }

    public CFunctionEpilogueNode(int oldThreadStatus) {
        this(oldThreadStatus, Set.of(), null);
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

    public Set<CapturableState> getStatesToCapture() {
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
}
