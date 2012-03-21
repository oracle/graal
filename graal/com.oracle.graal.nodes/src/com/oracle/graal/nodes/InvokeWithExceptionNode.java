/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes;

import java.util.*;

import com.oracle.max.cri.ci.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.util.*;

public class InvokeWithExceptionNode extends ControlSplitNode implements Node.IterableNodeType, Invoke, MemoryCheckpoint, LIRLowerable {
    public static final int NORMAL_EDGE = 0;
    public static final int EXCEPTION_EDGE = 1;

    @Input private final MethodCallTargetNode callTarget;
    @Input private FrameState stateAfter;
    @Data private final int bci;
    // megamorph should only be true when the compiler is sure that the call site is megamorph, and false when in doubt
    @Data private boolean megamorph;
    private boolean useForInlining;
    private final long leafGraphId;

    /**
     * @param kind
     * @param blockSuccessors
     * @param branchProbability
     */
    public InvokeWithExceptionNode(MethodCallTargetNode callTarget, BeginNode exceptionEdge, int bci, long leafGraphId) {
        super(callTarget.returnStamp(), new BeginNode[]{null, exceptionEdge}, new double[]{1.0, 0.0});
        this.bci = bci;
        this.callTarget = callTarget;
        this.leafGraphId = leafGraphId;
        this.megamorph = true;
        this.useForInlining = true;
    }

    public BeginNode exceptionEdge() {
        return blockSuccessor(EXCEPTION_EDGE);
    }

    public void setExceptionEdge(BeginNode x) {
        setBlockSuccessor(EXCEPTION_EDGE, x);
    }

    public BeginNode next() {
        return blockSuccessor(NORMAL_EDGE);
    }

    public void setNext(BeginNode x) {
        setBlockSuccessor(NORMAL_EDGE, x);
    }

    public MethodCallTargetNode callTarget() {
        return callTarget;
    }

    @Override
    public boolean megamorph() {
        return megamorph;
    }

    @Override
    public void setMegamorph(boolean megamorph) {
        this.megamorph = megamorph;
    }

    @Override
    public boolean useForInlining() {
        return useForInlining;
    }

    @Override
    public void setUseForInlining(boolean value) {
        this.useForInlining = value;
    }

    @Override
    public long leafGraphId() {
        return leafGraphId;
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Long) {
            return super.toString(Verbosity.Short) + "(bci=" + bci() + ")";
        } else if (verbosity == Verbosity.Name) {
            return "Invoke!#" + callTarget.targetMethod().name();
        } else {
            return super.toString(verbosity);
        }
    }

    public int bci() {
        return bci;
    }

    @Override
    public FixedNode node() {
        return this;
    }

    @Override
    public void setNext(FixedNode x) {
        if (x != null) {
            this.setNext(BeginNode.begin(x));
        } else {
            this.setNext(null);
        }
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.emitInvoke(this);
    }

    public FrameState stateAfter() {
        return stateAfter;
    }

    public void setStateAfter(FrameState stateAfter) {
        updateUsages(this.stateAfter, stateAfter);
        this.stateAfter = stateAfter;
    }

    public FrameState stateDuring() {
        FrameState tempStateAfter = stateAfter();
        FrameState stateDuring = tempStateAfter.duplicateModified(bci(), tempStateAfter.rethrowException(), this.callTarget.targetMethod().signature().returnKind(false));
        stateDuring.setDuringCall(true);
        return stateDuring;
    }

    @Override
    public Map<Object, Object> getDebugProperties() {
        Map<Object, Object> debugProperties = super.getDebugProperties();
        debugProperties.put("memoryCheckpoint", "true");
        if (callTarget != null && callTarget.targetMethod() != null) {
            debugProperties.put("targetMethod", CiUtil.format("%h.%n(%p)", callTarget.targetMethod()));
        }
        return debugProperties;
    }

    public void killExceptionEdge() {
        BeginNode exceptionEdge = exceptionEdge();
        setExceptionEdge(null);
        GraphUtil.killCFG(exceptionEdge);
    }

    @Override
    public boolean needsStateAfter() {
        return true;
    }

    @Override
    public void intrinsify(Node node) {
        MethodCallTargetNode call = callTarget;
        FrameState state = stateAfter();
        killExceptionEdge();
        if (node instanceof StateSplit) {
            StateSplit stateSplit = (StateSplit) node;
            stateSplit.setStateAfter(state);
        }
        if (node == null) {
            assert kind() == CiKind.Void && usages().isEmpty();
            ((StructuredGraph) graph()).removeSplit(this, NORMAL_EDGE);
        } else {
            ((StructuredGraph) graph()).replaceSplit(this, node, NORMAL_EDGE);
        }
        call.safeDelete();
        if (state.usages().isEmpty()) {
            state.safeDelete();
        }
    }
}
