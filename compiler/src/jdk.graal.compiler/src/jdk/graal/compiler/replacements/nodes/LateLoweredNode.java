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
package jdk.graal.compiler.replacements.nodes;

import static jdk.graal.compiler.nodeinfo.InputType.State;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.DeoptimizingNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.phases.common.TransplantGraphsPhase;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Represents a method or snippet call in a {@link StructuredGraph}. Supports various types of call
 * semantics, i.e., no verification is performed on the semantics of the callee graph.
 *
 * This node is the late "inlined" / transplanted counterpart of any {@link InvokeNode}. While
 * regular (java) invokes can be expanded and inlined early in the compilation pipeline
 * LateLoweredNode is the opposite. It is not touched, optimized or moved until the end of the
 * frontend. Before LIR generation, {@link TransplantGraphsPhase} expands this call to its callee
 * graph and transplants the callee graph at the position of the LateLoweredNode.
 *
 * Given the semantics behind the call is unknown, this node is a statesplit, memory kill and
 * deoptimizing node (so we preserve necessary framestates for rewiring any deoptimizing nodes in
 * the callee).
 *
 * For more context see {@link TransplantGraphsPhase}.
 */
//@formatter:off
@NodeInfo(cycles =NodeCycles. CYCLES_UNKNOWN,
          cyclesRationale = "If this node is not optimized away it will be lowered to a call, which we cannot estimate",
          size =NodeSize. SIZE_UNKNOWN,
          sizeRationale = "If this node is not optimized away it will be lowered to a call, which we cannot estimate", allowedUsageTypes = InputType.Memory)
//@formatter:on
public class LateLoweredNode extends AbstractBeginNode
                implements SingleMemoryKill, StateSplit, DeoptimizingNode, DeoptimizingNode.DeoptDuring, DeoptimizingNode.DeoptBefore, DeoptimizingNode.DeoptAfter {

    public static final NodeClass<LateLoweredNode> TYPE = NodeClass.create(LateLoweredNode.class);
    @Input protected NodeInputList<ValueNode> arguments;
    @OptionalInput(State) FrameState stateDuring;
    @OptionalInput(State) FrameState stateBefore;
    @OptionalInput(State) FrameState stateAfter;

    protected int bci;
    protected ResolvedJavaMethod targetMethod;
    protected Stamp returnStamp;

    /**
     * Action to be performed after inlining of a callee {@link BasicBlock} into the caller
     * {@link ControlFlowGraph}. Can be used to implement tasks relative to the inlined code in the
     * caller context.
     */
    private Consumer<BasicBlock<?>> afterInlineeBasicBlockAction;

    /**
     * Function that actually derives the snippet template to get the callee graph.
     */
    private Supplier<SnippetTemplate> templateProducer;

    @SuppressWarnings("this-escape")
    public LateLoweredNode(ResolvedJavaMethod targetMethod, Stamp returnStamp, ValueNode[] arguments, Supplier<SnippetTemplate> templateProducer) {
        super(TYPE, returnStamp);
        this.arguments = new NodeInputList<>(this, arguments);
        this.targetMethod = targetMethod;
        this.returnStamp = returnStamp;
        this.templateProducer = templateProducer;
    }

    @SuppressWarnings("this-escape")
    public LateLoweredNode(int bci, ResolvedJavaMethod targetMethod, Stamp returnStamp, ValueNode[] arguments, Supplier<SnippetTemplate> templateProducer) {
        super(TYPE, returnStamp);
        this.arguments = new NodeInputList<>(this, arguments);
        this.targetMethod = targetMethod;
        this.returnStamp = returnStamp;
        this.templateProducer = templateProducer;
        this.bci = bci;
    }

    public void setAfterInlineeBasicBlockAction(Consumer<BasicBlock<?>> afterInlineeBasicBlockAction) {
        this.afterInlineeBasicBlockAction = afterInlineeBasicBlockAction;
    }

    public Consumer<BasicBlock<?>> getAfterInlineeBasicBlockAction() {
        return afterInlineeBasicBlockAction;
    }

    public void setTemplateProducer(Supplier<SnippetTemplate> templateProducer) {
        this.templateProducer = templateProducer;
    }

    public Supplier<SnippetTemplate> getTemplateProducer() {
        return templateProducer;
    }

    @Override
    public final boolean hasSideEffect() {
        return true;
    }

    public ResolvedJavaMethod getTargetMethod() {
        return targetMethod;
    }

    public Stamp getReturnStamp() {
        return returnStamp;
    }

    @Override
    public boolean mustNotMoveAttachedGuards() {
        return true;
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

    public NodeInputList<ValueNode> getArguments() {
        return arguments;
    }

    public void setBci(int bci) {
        this.bci = bci;
    }

    @Override
    public FrameState stateDuring() {
        return stateDuring;
    }

    @Override
    public FrameState stateBefore() {
        return stateBefore;
    }

    @Override
    public FrameState stateAfter() {
        return stateAfter;
    }

    @Override
    public void setStateAfter(FrameState x) {
        updateUsages(this.stateAfter, x);
        this.stateAfter = x;
    }

    @Override
    public void setStateBefore(FrameState x) {
        updateUsages(this.stateBefore, x);
        this.stateBefore = x;
    }

    @Override
    public void setStateDuring(FrameState stateDuring) {
        updateUsages(this.stateDuring, stateDuring);
        this.stateDuring = stateDuring;
    }

    @Override
    public void computeStateDuring(FrameState stateAfter1) {
        FrameState newStateDuring = stateAfter1.duplicateModifiedDuringCall(bci, asNode().getStackKind());
        setStateDuring(newStateDuring);
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.ANY_LOCATION;
    }
}
