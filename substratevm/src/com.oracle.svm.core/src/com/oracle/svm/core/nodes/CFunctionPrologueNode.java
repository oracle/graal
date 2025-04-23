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

import static jdk.graal.compiler.nodeinfo.InputType.Memory;
import static jdk.graal.compiler.nodeinfo.InputType.State;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_8;

import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.stack.JavaFrameAnchor;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractStateSplit;
import jdk.graal.compiler.nodes.DeoptimizingNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.debug.ControlFlowAnchored;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.Lowerable;

/**
 * Represents the prologue that must be executed before a call to a C function, i.e., a function
 * that requires the setup of a {@link JavaFrameAnchor} and a {@link StatusSupport thread state
 * transition}.
 *
 * It must always be paired with a {@link CFunctionEpilogueNode}. In between the prologue and
 * epilogue, there must be exactly one {@link InvokeNode} (note that it must not be an invoke that
 * requires an exception handler, i.e., it must not be an {@link InvokeWithExceptionNode}.
 *
 * Part of the prologue/epilogue are emitted by the lowering of these nodes using snippets, see
 * class CFunctionSnippets. Other parts are emitted in the backend when the call instruction is
 * emitted.
 */
@NodeInfo(cycles = CYCLES_8, size = SIZE_8, allowedUsageTypes = {Memory})
public final class CFunctionPrologueNode extends AbstractStateSplit implements Lowerable, SingleMemoryKill, ControlFlowAnchored, DeoptimizingNode.DeoptBefore, DeoptimizingNode.DeoptAfter {
    public static final NodeClass<CFunctionPrologueNode> TYPE = NodeClass.create(CFunctionPrologueNode.class);

    private final int newThreadStatus;
    /**
     * The marker is used for LIR frame state verification, to ensure we have a proper matching of
     * prologue and epilogue and no unexpected machine code while the thread is in Native state.
     */
    private CFunctionPrologueMarker marker;

    public CFunctionPrologueNode(int newThreadStatus) {
        super(TYPE, StampFactory.forVoid());
        this.newThreadStatus = newThreadStatus;
    }

    @Override
    protected void afterClone(Node other) {
        super.afterClone(other);
        /*
         * Note that this method is invoked by the regular method inlining, but not by the
         * PEGraphDecoder. So the method inlining before analysis, as well as the trivial method
         * inlining before compilation, do not invoke this method. So it is only suitable for
         * assertion checking.
         */
        assert marker == null : "Marker must be unique";
    }

    public CFunctionPrologueMarker getMarker() {
        if (marker == null) {
            marker = new CFunctionPrologueMarker(newThreadStatus);
        }
        return marker;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }

    public int getNewThreadStatus() {
        return newThreadStatus;
    }

    @OptionalInput(State) FrameState stateBefore;

    @Override
    public FrameState stateBefore() {
        return stateBefore;
    }

    @Override
    public void setStateBefore(FrameState x) {
        updateUsages(this.stateBefore, x);
        this.stateBefore = x;
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

    @NodeIntrinsic
    public static native void cFunctionPrologue(@ConstantNodeParameter int newThreadStatus);
}
