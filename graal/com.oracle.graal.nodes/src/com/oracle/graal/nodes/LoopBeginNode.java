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

import static com.oracle.graal.graph.iterators.NodePredicates.*;

import java.util.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.util.*;

public class LoopBeginNode extends MergeNode implements IterableNodeType, LIRLowerable {

    private double loopFrequency;
    private int nextEndIndex;
    private int unswitches;
    @Input(InputType.Guard) private GuardingNode overflowGuard;

    public LoopBeginNode() {
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
        // nothing yet
    }

    public boolean isLoopExit(AbstractBeginNode begin) {
        return begin instanceof LoopExitNode && ((LoopExitNode) begin).loopBegin() == this;
    }

    public void removeExits() {
        for (LoopExitNode loopexit : loopExits().snapshot()) {
            loopexit.removeProxies();
            FrameState stateAfter = loopexit.stateAfter();
            graph().replaceFixedWithFixed(loopexit, graph().add(new BeginNode()));
            if (stateAfter != null && stateAfter.isAlive() && stateAfter.usages().isEmpty()) {
                GraphUtil.killWithUnusedFloatingInputs(stateAfter);
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
}
