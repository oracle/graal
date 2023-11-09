/*
 * Copyright (c) 2009, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes;

import static jdk.graal.compiler.nodeinfo.InputType.Extension;
import static jdk.graal.compiler.nodeinfo.InputType.Memory;
import static jdk.graal.compiler.nodeinfo.InputType.State;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_64;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_64;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_8;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;
import static jdk.graal.compiler.nodes.Invoke.CYCLES_UNKNOWN_RATIONALE;
import static jdk.graal.compiler.nodes.Invoke.SIZE_UNKNOWN_RATIONALE;

import java.util.Map;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodeinfo.Verbosity;
import jdk.graal.compiler.nodes.memory.AbstractMemoryCheckpoint;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.nodes.spi.UncheckedInterfaceProvider;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.code.BytecodeFrame;

/**
 * The {@code InvokeNode} represents all kinds of method calls.
 */
// @formatter:off
@NodeInfo(nameTemplate = "Invoke#{p#targetMethod/s}",
          allowedUsageTypes = {Memory},
          cycles = CYCLES_UNKNOWN, cyclesRationale = CYCLES_UNKNOWN_RATIONALE,
          size   = SIZE_UNKNOWN,   sizeRationale   = SIZE_UNKNOWN_RATIONALE)
// @formatter:on
public final class InvokeNode extends AbstractMemoryCheckpoint implements Invoke, LIRLowerable, SingleMemoryKill, UncheckedInterfaceProvider {
    public static final NodeClass<InvokeNode> TYPE = NodeClass.create(InvokeNode.class);

    @OptionalInput ValueNode classInit;
    @Input(Extension) CallTargetNode callTarget;
    @OptionalInput(State) FrameState stateDuring;
    protected int bci;
    protected boolean polymorphic;
    protected InlineControl inlineControl;
    protected final LocationIdentity identity;

    public InvokeNode(CallTargetNode callTarget, int bci) {
        this(callTarget, bci, callTarget.returnStamp().getTrustedStamp());
    }

    public InvokeNode(CallTargetNode callTarget, int bci, LocationIdentity identity) {
        this(callTarget, bci, callTarget.returnStamp().getTrustedStamp(), identity);
    }

    public InvokeNode(CallTargetNode callTarget, int bci, Stamp stamp) {
        this(callTarget, bci, stamp, LocationIdentity.any());
    }

    public InvokeNode(CallTargetNode callTarget, int bci, Stamp stamp, LocationIdentity identity) {
        super(TYPE, stamp);
        this.callTarget = callTarget;
        this.bci = bci;
        this.polymorphic = false;
        this.inlineControl = InlineControl.Normal;
        this.identity = identity;
    }

    @Override
    protected void afterClone(Node other) {
        updateInliningLogAfterClone(other);
    }

    @Override
    public CallTargetNode callTarget() {
        return callTarget;
    }

    void setCallTarget(CallTargetNode callTarget) {
        updateUsages(this.callTarget, callTarget);
        this.callTarget = callTarget;
    }

    @Override
    public boolean isPolymorphic() {
        return polymorphic;
    }

    @Override
    public void setPolymorphic(boolean value) {
        this.polymorphic = value;
    }

    @Override
    public void setInlineControl(InlineControl control) {
        this.inlineControl = control;
    }

    @Override
    public InlineControl getInlineControl() {
        return inlineControl;
    }

    @Override
    public Map<Object, Object> getDebugProperties(Map<Object, Object> map) {
        Map<Object, Object> debugProperties = super.getDebugProperties(map);
        if (callTarget != null) {
            debugProperties.put("targetMethod", callTarget.targetName());
        }
        return debugProperties;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return identity;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
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

    @Override
    public int bci() {
        return bci;
    }

    @Override
    public void setBci(int newBci) {
        assert BytecodeFrame.isPlaceholderBci(bci) && !BytecodeFrame.isPlaceholderBci(newBci) : "can only replace placeholder with better bci";
        bci = newBci;
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

    @Override
    public FrameState stateDuring() {
        return stateDuring;
    }

    @Override
    public void setStateDuring(FrameState stateDuring) {
        updateUsages(this.stateDuring, stateDuring);
        this.stateDuring = stateDuring;
    }

    @Override
    public Stamp uncheckedStamp() {
        return this.callTarget.returnStamp().getUncheckedStamp();
    }

    @Override
    public void setClassInit(ValueNode classInit) {
        this.classInit = classInit;
        updateUsages(null, classInit);
    }

    @Override
    public ValueNode classInit() {
        return classInit;
    }

    @Override
    public NodeCycles estimatedNodeCycles() {
        return estimatedNodeCycles(callTarget);
    }

    @Override
    protected NodeSize dynamicNodeSizeEstimate() {
        return estimatedNodeSize(callTarget);
    }

    static NodeCycles estimatedNodeCycles(CallTargetNode callTarget) {
        if (callTarget == null) {
            return CYCLES_UNKNOWN;
        }
        switch (callTarget.invokeKind()) {
            case Interface:
                return CYCLES_64;
            case Special:
            case Static:
                return CYCLES_2;
            case Virtual:
                return CYCLES_8;
            default:
                assert false : "Should not reach here";
                return CYCLES_UNKNOWN;
        }
    }

    static NodeSize estimatedNodeSize(CallTargetNode callTarget) {
        if (callTarget == null) {
            return SIZE_UNKNOWN;
        }
        switch (callTarget.invokeKind()) {
            case Interface:
                return SIZE_64;
            case Special:
            case Static:
                return SIZE_2;
            case Virtual:
                return SIZE_8;
            default:
                assert false : "Should not reach here";
                return SIZE_UNKNOWN;
        }
    }

}
