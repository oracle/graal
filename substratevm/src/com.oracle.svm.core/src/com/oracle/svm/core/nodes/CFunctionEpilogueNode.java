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
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_8;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.AbstractStateSplit;
import org.graalvm.compiler.nodes.DeoptimizingNode.DeoptBefore;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.debug.ControlFlowAnchored;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.word.LocationIdentity;

/**
 * See comments in {@link CFunctionPrologueNode} for details.
 */
@NodeInfo(cycles = CYCLES_8, size = SIZE_8, allowedUsageTypes = {Memory})
public final class CFunctionEpilogueNode extends AbstractStateSplit implements Lowerable, SingleMemoryKill, ControlFlowAnchored, DeoptBefore {
    public static final NodeClass<CFunctionEpilogueNode> TYPE = NodeClass.create(CFunctionEpilogueNode.class);

    private final int oldThreadStatus;
    /** See comment in {@link CFunctionPrologueNode}. */
    private CFunctionEpilogueMarker marker;

    public CFunctionEpilogueNode(int oldThreadStatus) {
        super(TYPE, StampFactory.forVoid());
        this.oldThreadStatus = oldThreadStatus;
        marker = new CFunctionEpilogueMarker();
    }

    @Override
    protected void afterClone(Node other) {
        super.afterClone(other);
        marker = new CFunctionEpilogueMarker();
    }

    public CFunctionEpilogueMarker getMarker() {
        return marker;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }

    public int getOldThreadStatus() {
        return oldThreadStatus;
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

}
