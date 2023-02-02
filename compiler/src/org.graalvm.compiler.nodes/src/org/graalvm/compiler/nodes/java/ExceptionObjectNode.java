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
package org.graalvm.compiler.nodes.java;

import static org.graalvm.compiler.nodeinfo.InputType.Memory;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_8;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.BeginStateSplitNode;
import org.graalvm.compiler.nodes.DeoptimizingNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.WithExceptionNode;
import org.graalvm.compiler.nodes.memory.MemoryAnchorNode;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * The entry to an exception handler with the exception coming from a call (as opposed to a local
 * throw instruction or implicit exception).
 */
@NodeInfo(allowedUsageTypes = Memory, cycles = CYCLES_8, size = SIZE_8)
public final class ExceptionObjectNode extends BeginStateSplitNode implements Lowerable, SingleMemoryKill, DeoptimizingNode.DeoptAfter {
    public static final NodeClass<ExceptionObjectNode> TYPE = NodeClass.create(ExceptionObjectNode.class);

    public ExceptionObjectNode(MetaAccessProvider metaAccess) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createTrustedWithoutAssumptions(metaAccess.lookupJavaType(Throwable.class))));
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
            LoadExceptionObjectNode loadException = graph().add(new LoadExceptionObjectNode(stamp(NodeView.DEFAULT)));

            GraalError.guarantee(graph().getGuardsStage().areFrameStatesAtDeopts(), "Should be after FSA %s", this);
            GraalError.guarantee(stateAfter() != null, "StateAfter must not be null for %s", this);
            loadException.setStateAfter(stateAfter());
            BeginNode begin = graph().add(new BeginNode());
            FixedWithNextNode insertAfter = begin;
            graph().addAfterFixed(this, begin);
            replaceAtUsages(loadException, InputType.Value);
            if (hasUsages()) {
                MemoryAnchorNode anchor = graph().add(new MemoryAnchorNode(LocationIdentity.any()));
                graph().addAfterFixed(begin, anchor);
                replaceAtUsages(anchor, InputType.Memory);
                insertAfter = anchor;
            }
            graph().addAfterFixed(insertAfter, loadException);
            graph().removeFixed(this);
            loadException.lower(tool);
        }
    }

    /**
     * Tests whether this is a placeholder node that can be removed.
     *
     * See {@code org.graalvm.compiler.replacements.SnippetTemplate.replaceExceptionObjectNode}
     */
    private boolean isMarkerAndCanBeRemoved() {
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
    public boolean verify() {
        assertTrue(graph().getGraphState().getFrameStateVerification() == GraphState.FrameStateVerification.NONE || stateAfter() != null, "an exception handler needs a frame state");
        assertTrue(graph().getGraphState().getFrameStateVerification() == GraphState.FrameStateVerification.NONE ||
                        stateAfter().stackSize() == 1 && stateAfter().stackAt(0).stamp(NodeView.DEFAULT).getStackKind() == JavaKind.Object,
                        "an exception handler's frame state must have only the exception on the stack");
        return super.verify();
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }
}
