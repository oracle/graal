/*
 * Copyright (c) 2009, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import static org.graalvm.compiler.nodeinfo.InputType.Association;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeFlood;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.loop.InductionVariable;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.util.GraphUtil;

/**
 * {@code PhiNode}s represent the merging of edges at a control flow merges (
 * {@link AbstractMergeNode} or {@link LoopBeginNode}). For a {@link AbstractMergeNode}, the order
 * of the values corresponds to the order of the ends. For {@link LoopBeginNode}s, the first value
 * corresponds to the loop's predecessor, while the rest of the values correspond to the
 * {@link LoopEndNode}s.
 */
@NodeInfo(cycles = CYCLES_0, size = SIZE_1)
public abstract class PhiNode extends FloatingNode implements Canonicalizable {

    public static final NodeClass<PhiNode> TYPE = NodeClass.create(PhiNode.class);
    @Input(Association) protected AbstractMergeNode merge;

    protected PhiNode(NodeClass<? extends PhiNode> c, Stamp stamp, AbstractMergeNode merge) {
        super(c, stamp);
        this.merge = merge;
    }

    public abstract NodeInputList<ValueNode> values();

    public AbstractMergeNode merge() {
        return merge;
    }

    public void setMerge(AbstractMergeNode x) {
        updateUsages(merge, x);
        merge = x;
    }

    @Override
    public boolean verify() {
        assertTrue(merge() != null, "missing merge");
        assertTrue(merge().phiPredecessorCount() == valueCount(), "mismatch between merge predecessor count and phi value count: %d != %d", merge().phiPredecessorCount(), valueCount());
        return super.verify();
    }

    /**
     * Get the instruction that produces the value associated with the i'th predecessor of the
     * merge.
     *
     * @param i the index of the predecessor
     * @return the instruction that produced the value in the i'th predecessor
     */
    public ValueNode valueAt(int i) {
        return values().get(i);
    }

    /**
     * Sets the value at the given index and makes sure that the values list is large enough.
     *
     * @param i the index at which to set the value
     * @param x the new phi input value for the given location
     */
    public void initializeValueAt(int i, ValueNode x) {
        while (values().size() <= i) {
            values().add(null);
        }
        values().set(i, x);
    }

    public void setValueAt(int i, ValueNode x) {
        values().set(i, x);
    }

    public void setValueAt(AbstractEndNode end, ValueNode x) {
        setValueAt(merge().phiPredecessorIndex(end), x);
    }

    public ValueNode valueAt(AbstractEndNode pred) {
        return valueAt(merge().phiPredecessorIndex(pred));
    }

    /**
     * Get the number of inputs to this phi (i.e. the number of predecessors to the merge).
     *
     * @return the number of inputs in this phi
     */
    public int valueCount() {
        return values().size();
    }

    public void clearValues() {
        values().clear();
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name) {
            StringBuilder str = new StringBuilder();
            for (int i = 0; i < valueCount(); ++i) {
                if (i != 0) {
                    str.append(' ');
                }
                str.append(valueAt(i) == null ? "-" : valueAt(i).toString(Verbosity.Id));
            }
            String description = valueDescription();
            if (description.length() > 0) {
                str.append(", ").append(description);
            }
            return super.toString(Verbosity.Name) + "(" + str + ")";
        } else {
            return super.toString(verbosity);
        }
    }

    /**
     * String describing the kind of value this Phi merges. Used by {@link #toString(Verbosity)} and
     * dumping.
     */
    protected String valueDescription() {
        return "";
    }

    public void addInput(ValueNode x) {
        assert !(x instanceof ValuePhiNode) || ((ValuePhiNode) x).merge() instanceof LoopBeginNode || ((ValuePhiNode) x).merge() != this.merge();
        assert !(this instanceof ValuePhiNode) || x.stamp(NodeView.DEFAULT).isCompatible(stamp(NodeView.DEFAULT));
        values().add(x);
    }

    public void removeInput(int index) {
        values().remove(index);
    }

    public NodeIterable<ValueNode> backValues() {
        return values().subList(merge().forwardEndCount());
    }

    /**
     * If all inputs are the same value, that value is returned, otherwise {@code this}. Note that
     * {@code null} is a valid return value, since {@link GuardPhiNode}s can have {@code null}
     * inputs.
     */
    public ValueNode singleValueOrThis() {
        ValueNode singleValue = valueAt(0);
        int count = valueCount();
        for (int i = 1; i < count; ++i) {
            ValueNode value = valueAt(i);
            if (value != this) {
                if (value != singleValue) {
                    return this;
                }
            }
        }
        return singleValue;
    }

    /**
     * If all inputs (but the first one) are the same value, that value is returned, otherwise
     * {@code this}. Note that {@code null} is a valid return value, since {@link GuardPhiNode}s can
     * have {@code null} inputs.
     */
    public ValueNode singleBackValueOrThis() {
        int valueCount = valueCount();
        assert merge() instanceof LoopBeginNode && valueCount >= 2;
        // Skip first value, assume second value as single value.
        ValueNode singleValue = valueAt(1);
        for (int i = 2; i < valueCount; ++i) {
            ValueNode value = valueAt(i);
            if (value != singleValue) {
                return this;
            }
        }
        return singleValue;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        if (isLoopPhi()) {
            int valueCount = valueCount();
            assert valueCount >= 2;
            int i;
            for (i = 1; i < valueCount; ++i) {
                ValueNode value = valueAt(i);
                if (value != this) {
                    break;
                }
            }

            // All back edges are self-references => return forward edge input value.
            if (i == valueCount) {
                return firstValue();
            }

            boolean onlySelfUsage = true;
            for (Node n : this.usages()) {
                if (n != this) {
                    onlySelfUsage = false;
                    break;
                }
            }
            if (onlySelfUsage) {
                return null;
            }
            if (this.graph() != null) {
                if (isDeadLoopPhiCycle(this)) {
                    return null;
                }
            }
        }

        return singleValueOrThis();
    }

    /**
     * Determine if the given {@link PhiNode} is the root of a dead (no real usages outside the
     * cycle) phi cycle. Loop phis ({@link PhiNode#isLoopPhi()}) often form cyclic chains of
     * floating nodes due to heavy usage of {@link InductionVariable} in loop code.
     *
     * Dead phi cycles are often the result of inlining, conditional elimination and partial escape
     * analysis.
     *
     * Cycle detection is a best-effort operation: we do not want to spend a lot of compile time
     * cleaning up extremely complex or long dead cycles (they will be handled by the scheduling
     * phase).
     *
     * Consider the following intended complex example
     *
     * <pre>
     * int phi = 0;
     * while (true) {
     *     if (sth) {
     *         code();
     *         phi = 12; // constant
     *     } else if (sthElse) {
     *         moreCode();
     *         phi = phi - 125; // sub node usage
     *     } else {
     *         evenMoreCode();
     *         phi = phi + 127; // add node usage
     *     }
     * }
     * </pre>
     *
     * The cycle detection will start by processing the node associated with {@code phi} and process
     * its usages. Note that the phi itself has multiple usages even though it is still dead. The
     * detection will follow all usages of marked nodes which means at the end the nodes visited
     * will be {@code phi, constant(12), sub, constant(125), add, constant(127)}. Since no node
     * visited in between has other usages except one (so no outside usages) and no fixed node
     * usages we can conclude we found a dead phi cycle that can be removed.
     */
    private static boolean isDeadLoopPhiCycle(PhiNode thisPhi) {
        GraalError.guarantee(thisPhi.isLoopPhi(), "Must only process loop phis");
        NodeFlood nf = thisPhi.graph().createNodeFlood();
        nf.add(thisPhi);
        int steps = 0;
        for (Node flooded : nf) {
            if (flooded != thisPhi && !flooded.hasExactlyOneUsage()) {
                /*
                 * Node is used outside the cycle (presumably). While following usages would also
                 * find dead floating nodes hanging of a phi cycle no other place is guaranteed to
                 * kill them in the correct order (like GraphUtil#killWithUnusedFloatingInputs).
                 */
                return false;
            }
            // constants are always marked as visited but we never visit their usages, they are leaf
            // nodes and can appear everywhere
            if (flooded instanceof ConstantNode) {
                continue;
            }
            if (steps++ >= DEAD_PHI_CYCLE_STEPS) {
                // too much effort, abort
                return false;
            }
            for (Node usage : flooded.usages()) {
                if (!GraphUtil.isFloatingNode(usage)) {
                    // Fixed node usage: can never have a dead cycle
                    return false;
                }
                if (usage instanceof VirtualState || usage instanceof ProxyNode) {
                    // usages that still require the (potential) dead cycle to be alive
                    return false;
                }
                if (!nf.isMarked(usage)) {
                    nf.add(usage);
                }
            }
        }
        // we managed to find a dead cycle
        thisPhi.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, thisPhi.graph(), "Found dead phi cycle %s", nf.getVisited());
        return true;
    }

    /**
     * Number of loop iterations to perform in dead phi cycle detection before giving up for compile
     * time reasons (the scheduler will consider the rest).
     */
    private static final int DEAD_PHI_CYCLE_STEPS = 16;

    public ValueNode firstValue() {
        return valueAt(0);
    }

    public boolean isLoopPhi() {
        return merge() instanceof LoopBeginNode;
    }

    /**
     * @return {@code true} if this node's only usages are the node itself (only possible for
     *         loops).
     */
    public boolean isDegenerated() {
        for (Node use : usages()) {
            if (use != this) {
                return false;
            }
        }
        assert isLoopPhi();
        return true;
    }

    public abstract ProxyNode createProxyFor(LoopExitNode lex);

    /**
     * Create a phi of the same kind on the given merge.
     *
     * @param newMerge the merge to use for the newly created phi
     */
    public abstract PhiNode duplicateOn(AbstractMergeNode newMerge);
}
