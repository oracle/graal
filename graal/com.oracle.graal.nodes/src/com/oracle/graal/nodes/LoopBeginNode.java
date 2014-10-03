/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.graph.iterators.NodePredicates.*;

import java.util.*;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.util.*;

@NodeInfo
public class LoopBeginNode extends MergeNode implements IterableNodeType, LIRLowerable {

    protected double loopFrequency;
    protected int nextEndIndex;
    protected int unswitches;
    @OptionalInput(InputType.Guard) GuardingNode overflowGuard;

    public static LoopBeginNode create() {
        return USE_GENERATED_NODES ? new LoopBeginNodeGen() : new LoopBeginNode();
    }

    protected LoopBeginNode() {
        loopFrequency = 1;
    }

    public double loopFrequency() {
        return loopFrequency;
    }

    public void setLoopFrequency(double loopFrequency) {
        assert loopFrequency >= 0;
        this.loopFrequency = loopFrequency;
    }

    /**
     * Returns the <b>unordered</b> set of {@link LoopEndNode} that correspond to back-edges for
     * this loop. The order of the back-edges is unspecified, if you need to get an ordering
     * compatible for {@link PhiNode} creation, use {@link #orderedLoopEnds()}.
     *
     * @return the set of {@code LoopEndNode} that correspond to back-edges for this loop
     */
    public NodeIterable<LoopEndNode> loopEnds() {
        return usages().filter(LoopEndNode.class);
    }

    public NodeIterable<LoopExitNode> loopExits() {
        return usages().filter(LoopExitNode.class);
    }

    @Override
    public NodeIterable<Node> anchored() {
        return super.anchored().filter(isNotA(LoopEndNode.class).nor(LoopExitNode.class));
    }

    /**
     * Returns the set of {@link LoopEndNode} that correspond to back-edges for this loop, ordered
     * in increasing {@link #phiPredecessorIndex}. This method is suited to create new loop
     * {@link PhiNode}.
     *
     * @return the set of {@code LoopEndNode} that correspond to back-edges for this loop
     */
    public List<LoopEndNode> orderedLoopEnds() {
        List<LoopEndNode> snapshot = loopEnds().snapshot();
        Collections.sort(snapshot, new Comparator<LoopEndNode>() {

            @Override
            public int compare(LoopEndNode o1, LoopEndNode o2) {
                return o1.endIndex() - o2.endIndex();
            }
        });
        return snapshot;
    }

    public AbstractEndNode forwardEnd() {
        assert forwardEndCount() == 1;
        return forwardEndAt(0);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        // Nothing to emit, since this is node is used for structural purposes only.
    }

    @Override
    protected void deleteEnd(AbstractEndNode end) {
        if (end instanceof LoopEndNode) {
            LoopEndNode loopEnd = (LoopEndNode) end;
            loopEnd.setLoopBegin(null);
            int idx = loopEnd.endIndex();
            for (LoopEndNode le : loopEnds()) {
                int leIdx = le.endIndex();
                assert leIdx != idx;
                if (leIdx > idx) {
                    le.setEndIndex(leIdx - 1);
                }
            }
            nextEndIndex--;
        } else {
            super.deleteEnd(end);
        }
    }

    @Override
    public int phiPredecessorCount() {
        return forwardEndCount() + loopEnds().count();
    }

    @Override
    public int phiPredecessorIndex(AbstractEndNode pred) {
        if (pred instanceof LoopEndNode) {
            LoopEndNode loopEnd = (LoopEndNode) pred;
            if (loopEnd.loopBegin() == this) {
                assert loopEnd.endIndex() < loopEnds().count() : "Invalid endIndex : " + loopEnd;
                return loopEnd.endIndex() + forwardEndCount();
            }
        } else {
            return super.forwardEndIndex(pred);
        }
        throw ValueNodeUtil.shouldNotReachHere("unknown pred : " + pred);
    }

    @Override
    public AbstractEndNode phiPredecessorAt(int index) {
        if (index < forwardEndCount()) {
            return forwardEndAt(index);
        }
        for (LoopEndNode end : loopEnds()) {
            int idx = index - forwardEndCount();
            assert idx >= 0;
            if (end.endIndex() == idx) {
                return end;
            }
        }
        throw ValueNodeUtil.shouldNotReachHere();
    }

    @Override
    public boolean verify() {
        assertTrue(loopEnds().isNotEmpty(), "missing loopEnd");
        return super.verify();
    }

    public int nextEndIndex() {
        return nextEndIndex++;
    }

    public int unswitches() {
        return unswitches;
    }

    public void incUnswitches() {
        unswitches++;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        removeDeadPhis();
        canonicalizePhis(tool);
    }

    public boolean isLoopExit(BeginNode begin) {
        return begin instanceof LoopExitNode && ((LoopExitNode) begin).loopBegin() == this;
    }

    public void removeExits() {
        for (LoopExitNode loopexit : loopExits().snapshot()) {
            loopexit.removeProxies();
            FrameState loopStateAfter = loopexit.stateAfter();
            graph().replaceFixedWithFixed(loopexit, graph().add(BeginNode.create()));
            if (loopStateAfter != null && loopStateAfter.isAlive() && loopStateAfter.usages().isEmpty()) {
                GraphUtil.killWithUnusedFloatingInputs(loopStateAfter);
            }
        }
    }

    public GuardingNode getOverflowGuard() {
        return overflowGuard;
    }

    public void setOverflowGuard(GuardingNode overflowGuard) {
        updateUsagesInterface(this.overflowGuard, overflowGuard);
        this.overflowGuard = overflowGuard;
    }

    /**
     * Removes dead {@linkplain PhiNode phi nodes} hanging from this node.
     *
     * This method uses the heuristic that any node which not a phi node of this LoopBeginNode is
     * alive. This allows the removal of dead phi loops.
     */
    public void removeDeadPhis() {
        if (phis().isNotEmpty()) {
            Set<PhiNode> alive = new HashSet<>();
            for (PhiNode phi : phis()) {
                NodePredicate isAlive = u -> !isPhiAtMerge(u) || alive.contains(u);
                if (phi.usages().filter(isAlive).isNotEmpty()) {
                    alive.add(phi);
                    for (PhiNode keptAlive : phi.values().filter(PhiNode.class).filter(isAlive.negate())) {
                        alive.add(keptAlive);
                    }
                }
            }
            for (PhiNode phi : phis().filter(((NodePredicate) alive::contains).negate()).snapshot()) {
                phi.replaceAtUsages(null);
                phi.safeDelete();
            }
        }
    }

    private static final int NO_INCREMENT = Integer.MIN_VALUE;

    /**
     * Returns an array with one entry for each input of the phi, which is either
     * {@link #NO_INCREMENT} or the increment, i.e., the value by which the phi is incremented in
     * the corresponding branch.
     */
    private static int[] getSelfIncrements(PhiNode phi) {
        int[] selfIncrement = new int[phi.valueCount()];
        for (int i = 0; i < phi.valueCount(); i++) {
            ValueNode input = phi.valueAt(i);
            long increment = NO_INCREMENT;
            if (input != null && input instanceof AddNode && input.stamp() instanceof IntegerStamp) {
                AddNode add = (AddNode) input;
                if (add.getX() == phi && add.getY().isConstant()) {
                    increment = add.getY().asConstant().asLong();
                } else if (add.getY() == phi && add.getX().isConstant()) {
                    increment = add.getX().asConstant().asLong();
                }
            } else if (input == phi) {
                increment = 0;
            }
            if (increment < Integer.MIN_VALUE || increment > Integer.MAX_VALUE || increment == NO_INCREMENT) {
                increment = NO_INCREMENT;
            }
            selfIncrement[i] = (int) increment;
        }
        return selfIncrement;
    }

    /**
     * Coalesces loop phis that represent the same value (which is not handled by normal Global
     * Value Numbering).
     */
    public void canonicalizePhis(SimplifierTool tool) {
        int phiCount = phis().count();
        if (phiCount > 1) {
            int phiInputCount = phiPredecessorCount();
            int phiIndex = 0;
            int[][] selfIncrement = new int[phiCount][];
            PhiNode[] phis = this.phis().snapshot().toArray(new PhiNode[phiCount]);

            for (phiIndex = 0; phiIndex < phiCount; phiIndex++) {
                PhiNode phi = phis[phiIndex];
                if (phi != null) {
                    nextPhi: for (int otherPhiIndex = phiIndex + 1; otherPhiIndex < phiCount; otherPhiIndex++) {
                        PhiNode otherPhi = phis[otherPhiIndex];
                        if (otherPhi == null || phi.getNodeClass() != otherPhi.getNodeClass() || !phi.valueEquals(otherPhi)) {
                            continue nextPhi;
                        }
                        if (selfIncrement[phiIndex] == null) {
                            selfIncrement[phiIndex] = getSelfIncrements(phi);
                        }
                        if (selfIncrement[otherPhiIndex] == null) {
                            selfIncrement[otherPhiIndex] = getSelfIncrements(otherPhi);
                        }
                        int[] phiIncrement = selfIncrement[phiIndex];
                        int[] otherPhiIncrement = selfIncrement[otherPhiIndex];
                        for (int inputIndex = 0; inputIndex < phiInputCount; inputIndex++) {
                            if (phiIncrement[inputIndex] == NO_INCREMENT) {
                                if (phi.valueAt(inputIndex) != otherPhi.valueAt(inputIndex)) {
                                    continue nextPhi;
                                }
                            }
                            if (phiIncrement[inputIndex] != otherPhiIncrement[inputIndex]) {
                                continue nextPhi;
                            }
                        }
                        if (tool != null) {
                            tool.addToWorkList(otherPhi.usages());
                        }
                        otherPhi.replaceAtUsages(phi);
                        GraphUtil.killWithUnusedFloatingInputs(otherPhi);
                        phis[otherPhiIndex] = null;
                    }
                }
            }
        }
    }
}
