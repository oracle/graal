/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.java;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_8;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.DeoptimizingNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.WithExceptionNode;
import jdk.graal.compiler.nodes.extended.MembarNode;
import jdk.graal.compiler.nodes.extended.MembarNode.FenceKind;
import jdk.graal.compiler.nodes.spi.Lowerable;

/**
 * This node has an exception edge to an {@link ExceptionObjectNode}. It is used to represent
 * allocation operations that are known or expected to throw {@link OutOfMemoryError}.
 */
@NodeInfo(/* allowedUsageTypes = {InputType.Memory}, */ cycles = CYCLES_8, cyclesRationale = "tlab alloc + header init", size = SIZE_8)
public abstract class AllocateWithExceptionNode extends WithExceptionNode implements Lowerable, StateSplit, DeoptimizingNode.DeoptBefore {

    public static final NodeClass<AllocateWithExceptionNode> TYPE = NodeClass.create(AllocateWithExceptionNode.class);

    protected AllocateWithExceptionNode(NodeClass<? extends AllocateWithExceptionNode> c, Stamp stamp) {
        this(c, stamp, true);
    }

    protected AllocateWithExceptionNode(NodeClass<? extends AllocateWithExceptionNode> c, Stamp stamp, boolean mustHaveStateAfter) {
        super(c, stamp);
        this.mustHaveStateAfter = mustHaveStateAfter;
    }

    @OptionalInput(InputType.State) protected FrameState stateBefore;
    @OptionalInput(InputType.State) protected FrameState stateAfter;

    /**
     * Controls whether this {@link StateSplit} must carry a valid post-allocation
     * {@linkplain #stateAfter() stateAfter}. Parser-created allocation-with-exception nodes have an
     * exact post-allocation frame state, but nodes restored from encoded plain allocations during SVM
     * graph decoding do not: the encoded graph usually preserved only the allocation pre-state, and a
     * valid parser-created post-state cannot be reconstructed reliably at the decode site.
     */
    private final boolean mustHaveStateAfter;

    @Override
    public FrameState stateBefore() {
        return stateBefore;
    }

    @Override
    public void setStateBefore(FrameState f) {
        updateUsages(stateBefore, f);
        stateBefore = f;
    }

    @Override
    public FrameState stateAfter() {
        return stateAfter;
    }

    @Override
    public void setStateAfter(FrameState x) {
        GraalError.guarantee(x == null || x.isAlive(), "frame state must be in a graph: %s", x);
        updateUsages(stateAfter, x);
        stateAfter = x;
    }

    @Override
    public boolean hasSideEffect() {
        return mustHaveStateAfter;
    }

    protected boolean mustHaveStateAfter() {
        return mustHaveStateAfter;
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

    @Override
    public boolean canUseAsStateDuring() {
        /*
         * Lowering can replace this allocation with a throwing foreign call. While that call is
         * executing, no allocation result is available yet, so the allocation pre-state is a valid
         * stateDuring for the lowered throwing call.
         */
        return true;
    }

    /**
     * Return {@code true} if the object's contents should be initialized to zero/null.
     * <p>
     * For simplicity, returns {@code true}.
     */
    public boolean fillContents() {
        return true;
    }

    /**
     * Controls whether this allocation emits a {@link MembarNode} with
     * {@link FenceKind#ALLOCATION_INIT} as part of the object initialization.
     * <p>
     * For simplicity, returns {@code true}.
     */
    public boolean emitMemoryBarrier() {
        return true;
    }
}
