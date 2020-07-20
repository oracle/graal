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
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_8;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.debug.ControlFlowAnchored;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.stack.JavaFrameAnchor;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;

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
public final class CFunctionPrologueNode extends FixedWithNextNode implements Lowerable, SingleMemoryKill, ControlFlowAnchored {
    public static final NodeClass<CFunctionPrologueNode> TYPE = NodeClass.create(CFunctionPrologueNode.class);

    private final int newThreadStatus;
    /**
     * The marker object prevents value numbering of the node. This means that the marker must be a
     * unique object per node, even after node cloning (e.g., because of method inlining).
     * Therefore, {@link #afterClone} properly re-initializes the field to a new marker instance.
     *
     * The marker is also used for LIR frame state verification, to ensure we have a proper matching
     * of prologue and epilogue and no unexpected machine code while the thread is in Native state.
     */
    private CFunctionPrologueMarker marker;

    public CFunctionPrologueNode(int newThreadStatus) {
        super(TYPE, StampFactory.forVoid());
        this.newThreadStatus = newThreadStatus;
        marker = new CFunctionPrologueMarker(newThreadStatus);
    }

    @Override
    protected void afterClone(Node other) {
        super.afterClone(other);
        marker = new CFunctionPrologueMarker(newThreadStatus);
    }

    public CFunctionPrologueMarker getMarker() {
        return marker;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }

    public int getNewThreadStatus() {
        return newThreadStatus;
    }

    @NodeIntrinsic
    public static native void cFunctionPrologue(@ConstantNodeParameter int newThreadStatus);
}
