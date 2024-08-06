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
package jdk.graal.compiler.nodes;

import static jdk.graal.compiler.nodeinfo.InputType.Association;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import java.util.List;

import org.graalvm.collections.EconomicSet;

import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.graph.IterableNodeType;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.graph.iterators.NodePredicate;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.memory.MemoryPhiNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.nodes.spi.Simplifiable;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.nodes.util.GraphUtil;

/**
 * Denotes the merging of multiple control-flow paths.
 */
@NodeInfo(allowedUsageTypes = Association, cycles = CYCLES_0, size = SIZE_0)
@SuppressWarnings("this-escape")
public abstract class AbstractMergeNode extends BeginStateSplitNode implements IterableNodeType, Simplifiable, LIRLowerable {
    public static final NodeClass<AbstractMergeNode> TYPE = NodeClass.create(AbstractMergeNode.class);

    protected AbstractMergeNode(NodeClass<? extends AbstractMergeNode> c) {
        super(c);
    }

    @Input(Association) protected NodeInputList<EndNode> ends = new NodeInputList<>(this);

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.visitMerge(this);
    }

    public int forwardEndIndex(EndNode end) {
        return ends.indexOf(end);
    }

    public void addForwardEnd(EndNode end) {
        ends.add(end);
    }

    public final int forwardEndCount() {
        return ends.size();
    }

    public final EndNode forwardEndAt(int index) {
        return ends.get(index);
    }

    @Override
    public NodeIterable<EndNode> cfgPredecessors() {
        return ends;
    }

    /**
     * Determines if a given node is a phi whose {@linkplain PhiNode#merge() merge} is this node.
     *
     * @param value the instruction to test
     * @return {@code true} if {@code value} is a phi and its merge is {@code this}
     */
    public boolean isPhiAtMerge(Node value) {
        return value instanceof PhiNode && ((PhiNode) value).merge() == this;
    }

    /**
     * Removes the given end from the merge, along with the entries corresponding to this end in the
     * phis connected to the merge.
     *
     * @param pred the end to remove
     */
    public void removeEnd(AbstractEndNode pred) {
        int predIndex = phiPredecessorIndex(pred);
        assert predIndex != -1;
        deleteEnd(pred);
        for (PhiNode phi : phis().snapshot()) {
            if (phi.isDeleted()) {
                continue;
            }
            ValueNode removedValue = phi.valueAt(predIndex);
            phi.removeInput(predIndex);
            if (removedValue != null) {
                GraphUtil.tryKillUnused(removedValue);
            }
        }
    }

    protected void deleteEnd(AbstractEndNode end) {
        ends.remove(end);
    }

    public NodeInputList<EndNode> forwardEnds() {
        return ends;
    }

    public int phiPredecessorCount() {
        return forwardEndCount();
    }

    public int phiPredecessorIndex(AbstractEndNode pred) {
        return forwardEndIndex((EndNode) pred);
    }

    public AbstractEndNode phiPredecessorAt(int index) {
        return forwardEndAt(index);
    }

    /**
     * Returns the phi nodes {@linkplain #isPhiAtMerge anchored at this merge}. The returned node
     * iterable does not contain duplicates. The iteration order is not specified.
     */
    public NodeIterable<PhiNode> phis() {
        return this.usages().filter(PhiNode.class).filter(new NodePredicate() {
            /*
             * A guard phi can use a merge both as its merge point and as a guard input. Such a phi
             * occurs in the usages twice. Eliminate duplicate guard phis using this set.
             */
            EconomicSet<GuardPhiNode> seenGuardPhis = null;

            @Override
            public boolean apply(Node n) {
                if (isPhiAtMerge(n)) {
                    if (n instanceof GuardPhiNode guardPhi) {
                        if (seenGuardPhis == null) {
                            seenGuardPhis = EconomicSet.create();
                        }
                        return seenGuardPhis.add(guardPhi);
                    }
                    return true;
                }
                return false;
            }
        });
    }

    public NodeIterable<ValuePhiNode> valuePhis() {
        return this.usages().filter(ValuePhiNode.class);
    }

    public NodeIterable<MemoryPhiNode> memoryPhis() {
        return this.usages().filter(MemoryPhiNode.class);
    }

    @Override
    public NodeIterable<Node> anchored() {
        return super.anchored().filter(n -> !isPhiAtMerge(n));
    }

    /**
     * This simplify method can deal with a null value for tool, so that it can be used outside of
     * canonicalization.
     */
    @Override
    @SuppressWarnings("try")
    public void simplify(SimplifierTool tool) {
        FixedNode currentNext = next();
        if (currentNext instanceof AbstractEndNode) {
            AbstractEndNode origLoopEnd = (AbstractEndNode) currentNext;
            AbstractMergeNode merge = origLoopEnd.merge();
            if (merge instanceof LoopBeginNode && !(origLoopEnd instanceof LoopEndNode)) {
                return;
            }
            // in order to move anchored values to the other merge we would need to check if the
            // anchors are used by phis of the other merge
            if (this.anchored().isNotEmpty()) {
                return;
            }
            if (merge.stateAfter() == null && this.stateAfter() != null) {
                // We hold a state, but the succeeding merge does not => do not combine.
                return;
            }
            for (PhiNode phi : phis()) {
                for (Node usage : phi.usages()) {
                    if (!(usage instanceof VirtualState) && !merge.isPhiAtMerge(usage)) {
                        return;
                    }
                }
            }
            getDebug().log("Split %s into ends for %s.", this, merge);
            int numEnds = this.forwardEndCount();
            for (int i = 0; i < numEnds - 1; i++) {
                AbstractEndNode end = forwardEndAt(numEnds - 1 - i);
                if (tool != null) {
                    tool.addToWorkList(end);
                }
                AbstractEndNode newEnd;
                try (DebugCloseable position = end.withNodeSourcePosition()) {
                    if (merge instanceof LoopBeginNode) {
                        newEnd = graph().add(new LoopEndNode((LoopBeginNode) merge));
                    } else {
                        EndNode tmpEnd = graph().add(new EndNode());
                        merge.addForwardEnd(tmpEnd);
                        newEnd = tmpEnd;
                    }
                }
                for (PhiNode phi : merge.phis()) {
                    ValueNode v = phi.valueAt(origLoopEnd);
                    ValueNode newInput;
                    if (isPhiAtMerge(v)) {
                        PhiNode endPhi = (PhiNode) v;
                        newInput = endPhi.valueAt(end);
                    } else {
                        newInput = v;
                    }
                    phi.addInput(newInput);
                }
                this.removeEnd(end);
                end.replaceAtPredecessor(newEnd);
                end.safeDelete();
                if (tool != null) {
                    tool.addToWorkList(newEnd.predecessor());
                }
            }
            graph().reduceTrivialMerge(this);
        }
    }

    @SuppressWarnings("try")
    public static boolean duplicateReturnThroughMerge(MergeNode merge) {
        assert merge.graph() != null;
        FixedNode next = merge.next();
        if (next instanceof ReturnNode) {
            ReturnNode returnNode = (ReturnNode) next;
            if (merge.anchored().isNotEmpty() || returnNode.getMemoryMap() != null) {
                return false;
            }
            List<PhiNode> phis = merge.phis().snapshot();
            for (PhiNode phi : phis) {
                for (Node usage : phi.usages()) {
                    if (usage != returnNode && !(usage instanceof FrameState)) {
                        return false;
                    }
                }
            }
            ValuePhiNode returnValuePhi = returnNode.result() == null || !merge.isPhiAtMerge(returnNode.result()) ? null : (ValuePhiNode) returnNode.result();
            List<EndNode> endNodes = merge.forwardEnds().snapshot();
            for (EndNode end : endNodes) {
                try (DebugCloseable position = returnNode.withNodeSourcePosition()) {
                    ReturnNode newReturn = merge.graph().add(new ReturnNode(returnValuePhi == null ? returnNode.result() : returnValuePhi.valueAt(end)));
                    end.replaceAtPredecessor(newReturn);
                }
            }
            GraphUtil.killCFG(merge);
            for (EndNode end : endNodes) {
                end.safeDelete();
            }
            for (PhiNode phi : phis) {
                if (phi.isAlive() && phi.hasNoUsages()) {
                    GraphUtil.killWithUnusedFloatingInputs(phi);
                }
            }
            return true;
        }
        return false;
    }

    protected boolean verifyState() {
        return this.stateAfter != null;
    }

    @Override
    public boolean verifyNode() {
        assert !this.graph().getGraphState().getFrameStateVerification().implies(GraphState.FrameStateVerificationFeature.MERGES) || verifyState() : "Merge must have a state until FSA " + this;
        return super.verifyNode();
    }
}
