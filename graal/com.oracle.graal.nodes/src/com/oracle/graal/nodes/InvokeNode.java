/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.util.*;

/**
 * The {@code InvokeNode} represents all kinds of method calls.
 */
@NodeInfo(nameTemplate = "Invoke#{p#targetMethod/s}")
public final class InvokeNode extends AbstractStateSplit implements StateSplit, Node.IterableNodeType, Invoke, LIRLowerable, MemoryCheckpoint  {

    @Input private final CallTargetNode callTarget;
    private final int bci;
    private boolean megamorphic;
    private boolean useForInlining;
    private final long leafGraphId;

    /**
     * Constructs a new Invoke instruction.
     *
     * @param bci the bytecode index of the original invoke (used for debug infos)
     * @param callTarget the target method being called
     */
    public InvokeNode(CallTargetNode callTarget, int bci, long leafGraphId) {
        super(callTarget.returnStamp());
        this.callTarget = callTarget;
        this.bci = bci;
        this.leafGraphId = leafGraphId;
        this.megamorphic = false;
        this.useForInlining = true;
    }

    @Override
    public CallTargetNode callTarget() {
        return callTarget;
    }

    @Override
    public MethodCallTargetNode methodCallTarget() {
        return (MethodCallTargetNode) callTarget;
    }

    @Override
    public boolean isMegamorphic() {
        return megamorphic;
    }

    @Override
    public void setMegamorphic(boolean value) {
        this.megamorphic = value;
    }

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
    public Map<Object, Object> getDebugProperties(Map<Object, Object> map) {
        Map<Object, Object> debugProperties = super.getDebugProperties(map);
        if (callTarget instanceof MethodCallTargetNode && methodCallTarget().targetMethod() != null) {
            debugProperties.put("targetMethod", methodCallTarget().targetMethod());
        }
        return debugProperties;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getRuntime().lower(this, tool);
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.emitInvoke(this);
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Long) {
            return super.toString(Verbosity.Short) + "(bci=" + bci() + ")";
        } else if (verbosity == Verbosity.Name) {
            return "Invoke#" + (callTarget == null ? "null" : callTarget().targetName());
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
    public FrameState stateDuring() {
        FrameState stateAfter = stateAfter();
        FrameState stateDuring = stateAfter.duplicateModified(bci(), stateAfter.rethrowException(), kind());
        stateDuring.setDuringCall(true);
        return stateDuring;
    }

    @Override
    public void intrinsify(Node node) {
        assert !(node instanceof ValueNode) || ((ValueNode) node).kind().isVoid() == kind().isVoid();
        CallTargetNode call = callTarget;
        FrameState stateAfter = stateAfter();
        if (node instanceof StateSplit) {
            StateSplit stateSplit = (StateSplit) node;
            stateSplit.setStateAfter(stateAfter);
        }
        if (node == null) {
            assert kind() == Kind.Void && usages().isEmpty();
            ((StructuredGraph) graph()).removeFixed(this);
        } else {
            if (node instanceof FixedWithNextNode) {
                ((StructuredGraph) graph()).replaceFixedWithFixed(this, (FixedWithNextNode) node);
            } else if (node instanceof DeoptimizeNode) {
                this.replaceAtPredecessor(node);
                this.replaceAtUsages(null);
                GraphUtil.killCFG(this);
                return;
            } else {
                ((StructuredGraph) graph()).replaceFixed(this, node);
            }
        }
        call.safeDelete();
        if (stateAfter.usages().isEmpty()) {
            stateAfter.safeDelete();
        }
    }
}
