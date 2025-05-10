/*
 * Copyright (c) 2009, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.InputType.Memory;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_8;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.BeginStateSplitNode;
import jdk.graal.compiler.nodes.DeoptimizingNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.WithExceptionNode;
import jdk.graal.compiler.nodes.memory.MemoryAnchorNode;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * The entry to an exception handler with the exception coming from a call (as opposed to a local
 * throw instruction or implicit exception).
 */
@NodeInfo(allowedUsageTypes = Memory, cycles = CYCLES_8, size = SIZE_8)
public final class ExceptionObjectNode extends BeginStateSplitNode implements Lowerable, SingleMemoryKill, DeoptimizingNode.DeoptAfter {
    public static final NodeClass<ExceptionObjectNode> TYPE = NodeClass.create(ExceptionObjectNode.class);

    /**
     * Marker flag to express that this node can be unconditionally deleted in lower. We do it with
     * a marking scheme to not care about fixed node removal order in lowering.
     */
    private boolean unconditionallyMarkForDeletion;

    public ExceptionObjectNode(MetaAccessProvider metaAccess) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createTrustedWithoutAssumptions(metaAccess.lookupJavaType(Throwable.class))));
    }

    public ExceptionObjectNode(Stamp s) {
        super(TYPE, s);
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }

    /**
     * An exception handler is an entry point to a method from the runtime and so represents an
     * instruction that cannot be re-executed. It therefore needs a frame state.
     */
    @Override
    public boolean hasSideEffect() {
        return true;
    }

    @Override
    public void lower(LoweringTool tool) {
        if (isMarkerAndCanBeRemoved()) {
            graph().removeFixed(this);
            return;
        }
        if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.LOW_TIER) {
            /*
             * Now the lowering to BeginNode+LoadExceptionNode can be performed, since no more
             * deopts can float in between the begin node and the load exception node.
             */
            final StructuredGraph graph = graph();
            LoadExceptionObjectNode loadException = graph.add(new LoadExceptionObjectNode(stamp(NodeView.DEFAULT)));
            GraalError.guarantee(graph.getGuardsStage().areFrameStatesAtDeopts(), "Should be after FSA %s", this);
            GraalError.guarantee(stateAfter() != null, "StateAfter must not be null for %s", this);
            loadException.setStateAfter(stateAfter());
            AbstractBeginNode begin;
            if (graph.isSubstitution()) {
                begin = graph.add(new LoweredExceptionObjectBegin());
            } else {
                begin = graph.add(new BeginNode());
            }
            FixedWithNextNode insertAfter = begin;
            graph.addAfterFixed(this, begin);
            replaceAtUsages(loadException, InputType.Value);
            if (hasUsages()) {
                MemoryAnchorNode anchor = graph.add(new MemoryAnchorNode(LocationIdentity.any()));
                graph.addAfterFixed(begin, anchor);
                replaceAtUsages(anchor, InputType.Memory);
                insertAfter = anchor;
            }
            graph.addAfterFixed(insertAfter, loadException);
            graph.removeFixed(this);
            loadException.lower(tool);
        }
    }

    /**
     * Special marker begin node to signal that an exception handler ({@link ExceptionObjectNode})
     * was lowered as part of a snippet graph. We use this node to simplify graph stitching during
     * snippet template instantiation for complex exception snippets.
     */
    @NodeInfo(cycles = NodeCycles.CYCLES_0, size = NodeSize.SIZE_0)
    public static final class LoweredExceptionObjectBegin extends AbstractBeginNode {
        public static final NodeClass<LoweredExceptionObjectBegin> TYPE = NodeClass.create(LoweredExceptionObjectBegin.class);

        public LoweredExceptionObjectBegin() {
            super(TYPE);
        }
    }

    /**
     * See {@link ExceptionObjectNode#unconditionallyMarkForDeletion}.
     */
    public void markForDeletion() {
        unconditionallyMarkForDeletion = true;
    }

    /**
     * Tests whether this is a placeholder node that can be removed.
     *
     * @see jdk.graal.compiler.replacements.SnippetTemplate#replaceExceptionObjectNode
     */
    private boolean isMarkerAndCanBeRemoved() {
        if (unconditionallyMarkForDeletion) {
            if (this.hasNoUsages()) {
                return true;
            } else {
                throw GraalError.shouldNotReachHere("Cannot mark exception object with usages unconditionally for deletion " + this);
            }
        }
        if (predecessor() instanceof WithExceptionNode) {
            return false;
        }

        GraalError.guarantee(predecessor() instanceof ExceptionObjectNode || predecessor() instanceof MergeNode, "Unexpected predecessor of %s: %s", this, predecessor());
        GraalError.guarantee(getExceptionValueFromState(this) == getExceptionValueFromState((StateSplit) predecessor()), "predecessor of %s with unexpected state: %s", this, predecessor());
        GraalError.guarantee(hasNoUsages(), "Unexpected usages of %s", this);
        return true;
    }

    private static ValueNode getExceptionValueFromState(StateSplit exceptionObjectNode) {
        if (exceptionObjectNode.asNode().graph().getGraphState().getFrameStateVerification() == GraphState.FrameStateVerification.NONE) {
            return null;
        }
        GraalError.guarantee(exceptionObjectNode.stateAfter() != null, "an exception handler needs a frame state");
        GraalError.guarantee(exceptionObjectNode.stateAfter().stackSize() == 1 && exceptionObjectNode.stateAfter().stackAt(0).stamp(NodeView.DEFAULT).getStackKind() == JavaKind.Object,
                        "an exception handler's frame state must have only the exception on the stack");
        return exceptionObjectNode.stateAfter().stackAt(0);
    }

    @Override
    public boolean verifyNode() {
        assertTrue(graph().getGraphState().getFrameStateVerification() == GraphState.FrameStateVerification.NONE || stateAfter() != null, "an exception handler needs a frame state");
        assertTrue(graph().getGraphState().getFrameStateVerification() == GraphState.FrameStateVerification.NONE ||
                        stateAfter().stackSize() == 1 && stateAfter().stackAt(0).stamp(NodeView.DEFAULT).getStackKind() == JavaKind.Object,
                        "an exception handler's frame state must have only the exception on the stack");
        return super.verifyNode();
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }
}
