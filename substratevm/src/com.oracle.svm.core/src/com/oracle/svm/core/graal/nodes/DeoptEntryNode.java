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
package com.oracle.svm.core.graal.nodes;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.gen.NodeLIRBuilder;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.LabelRef;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.DeoptimizingNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.UnreachableBeginNode;
import jdk.graal.compiler.nodes.WithExceptionNode;
import jdk.graal.compiler.nodes.debug.ControlFlowAnchored;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.graal.lir.DeoptEntryOp;

import jdk.vm.ci.code.BytecodeFrame;

/**
 * A landing-pad for deoptimization. This node is generated in deoptimization target methods for all
 * deoptimization entry points.
 */
@NodeInfo(allowedUsageTypes = InputType.Anchor, cycles = NodeCycles.CYCLES_0, size = NodeSize.SIZE_0)
public final class DeoptEntryNode extends WithExceptionNode implements DeoptEntrySupport, DeoptimizingNode.DeoptAfter, SingleMemoryKill, ControlFlowAnchored {
    public static final NodeClass<DeoptEntryNode> TYPE = NodeClass.create(DeoptEntryNode.class);

    @OptionalInput(InputType.State) protected FrameState stateAfter;

    private final int proxifiedInvokeBci;

    protected DeoptEntryNode(int proxifiedInvokeBci) {
        super(TYPE, StampFactory.forVoid());
        this.proxifiedInvokeBci = proxifiedInvokeBci;
    }

    public static DeoptEntryNode create(int proxifiedInvokeBci) {
        assert proxifiedInvokeBci != BytecodeFrame.UNKNOWN_BCI;
        return new DeoptEntryNode(proxifiedInvokeBci);
    }

    public static DeoptEntryNode create() {
        return new DeoptEntryNode(BytecodeFrame.UNKNOWN_BCI);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LabelRef exceptionRef;
        AbstractBeginNode exceptionNode = exceptionEdge();
        if (exceptionNode instanceof UnreachableBeginNode) {
            exceptionRef = null;
        } else {
            /* Only register exception handler if it is meaningful. */
            NodeLIRBuilder nodeLIRBuilder = (NodeLIRBuilder) gen;
            exceptionRef = nodeLIRBuilder.getLIRBlock(exceptionNode);
            exceptionRef.getTargetBlock().setIndirectBranchTarget();
        }
        gen.getLIRGeneratorTool().append(new DeoptEntryOp(((NodeLIRBuilder) gen).stateForWithExceptionEdge(this, stateAfter(), exceptionRef)));

        /* Link to next() instruction. */
        gen.getLIRGeneratorTool().emitJump(((NodeLIRBuilder) gen).getLIRBlock(next()));
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }

    @Override
    public FrameState stateAfter() {
        return stateAfter;
    }

    @Override
    public void setStateAfter(FrameState x) {
        assert x == null || x.isAlive() : "frame state must be in a graph";
        updateUsages(stateAfter, x);
        stateAfter = x;
    }

    @Override
    public boolean hasSideEffect() {
        return true;
    }

    @Override
    public int getProxifiedInvokeBci() {
        return proxifiedInvokeBci;
    }
}
