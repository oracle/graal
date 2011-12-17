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
package com.oracle.max.graal.util;

import java.util.*;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;


public final class ComputeImmediateDominator {
    private final MergeNode dominated;
    private final Queue<FixedNode> toExplore;
    private final Queue<FixedNode> speculativeExplore;
    private final NodeMap<DominatorInfo> infoMap;
    private final DominatorInfo fullInfo;
    private FixedNode dominator;
    private int nextBit = 1;

    public ComputeImmediateDominator(MergeNode dominated) {
        this.dominated = dominated;
        this.toExplore = new LinkedList<FixedNode>();
        this.speculativeExplore = new LinkedList<FixedNode>();
        this.infoMap = dominated.graph().createNodeMap();
        fullInfo = new DominatorInfo(dominated, true);

        this.processMerge(dominated, fullInfo);
        if (toExplore.size() == 1) {
            dominator = toExplore.remove();
        }
    }

    public FixedNode compute() {
        try {
            while (dominator == null && (!toExplore.isEmpty() || !speculativeExplore.isEmpty())) {
                while (!toExplore.isEmpty()) {
                    exploreUp(toExplore.remove());
                    if (dominator != null) {
                        return dominator;
                    }
                }
                exploreUp(speculativeExplore.remove());
            }
            return dominator;
        } catch (Throwable t) {
            throw new GraalInternalError(t).addContext("Could not find a dominator").addContext(dominated);
        }
    }

    private void exploreUp(FixedNode from) {
        FixedNode p = from;
        DominatorInfo info = infoMap.get(from);
        if (info.isExplored()) {
            return;
        }
        //TTY.println("exploreUp(" + from + ") with " + info);
        info.setExplored();
        while (p != null) {
            if (p instanceof MergeNode) {
                processMerge((MergeNode) p, info);
                p = null;
            } else if (p instanceof ControlSplitNode) {
                processControlSplit((ControlSplitNode) p, info);
                p = null;
            } else {
                p = (FixedNode) p.predecessor();
            }
        }
    }

    private void processControlSplit(ControlSplitNode cs, DominatorInfo info) {
        //TTY.println("processControlSplit(" + cs + ", " + info + ")");
        DominatorInfo csInfo = infoMap.get(cs);
        if (csInfo == null) {
            csInfo = new DominatorInfo(cs, false);
            infoMap.set(cs, csInfo);
        }
        csInfo.add(info);
        FixedNode next = (FixedNode) cs.predecessor();
        if (checkControlSplitInfo(csInfo)) {
            return;
        }
        if (csInfo.isExplored()) {
            //TTY.println("  Already explored, propagate update");
            propagateUpdate(csInfo);
        } else {
            if (csInfo.parentCount() == cs.blockSuccessorCount()) { // all paths leading to this CS have been explored
                //TTY.println("  All parents explored, Enqueue");
                toExplore.add(next);
                speculativeExplore.remove(next);
            } else {
                //TTY.println("  Not all parents explored : Enqueue speculative");
                speculativeExplore.add(next);
            }
        }
        infoMap.set(next, csInfo);
    }

    private boolean propagateUpdate(DominatorInfo di) {
        //TTY.println("   propagateUpdate(" + di + ")");
        for (DominatorInfo child : di.children()) {
            //TTY.println("      add to child " + child);
            if (child.add(di, false)) {
                if (child.equals(fullInfo)) {
                    //TTY.println("   Found DOM!");
                    dominator = child.node();
                    return true;
                }
                if (propagateUpdate(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkControlSplitInfo(DominatorInfo di) {
        //TTY.println("   checkControlSplitInfo(" + di + ")");
        if (di.equals(fullInfo)) {
            dominator = di.node();
            //TTY.println("   Found DOM!");
            return true;
        }
        return false;
    }

    private void processMerge(MergeNode merge, DominatorInfo info) {
        //TTY.println("processMerge(" + merge + ", " + info + ")");
        for (EndNode end : merge.cfgPredecessors()) {
            toExplore.add(end);
            infoMap.set(end, info.createChild(end));
            //TTY.println("  Enqueue end : " + end + " with " + infoMap.get(end));
        }
    }

    private class DominatorInfo {
        private final FixedNode node;
        private final BitSet bits;
        private final BitSet ownBits;
        private final Collection<DominatorInfo> children;
        private final Collection<DominatorInfo> parents;
        private boolean explored;

        public DominatorInfo(FixedNode node, boolean full) {
            this.node = node;
            this.bits = new BitSet();
            this.ownBits = new BitSet();
            this.children = new ArrayList<DominatorInfo>(2);
            this.parents = new ArrayList<DominatorInfo>(2);
            if (full) {
                addOwnBits(0);
            }
        }

        public boolean isExplored() {
            return explored;
        }

        public void setExplored() {
            explored = true;
        }

        public DominatorInfo createChild(FixedNode node) {
            DominatorInfo di = new DominatorInfo(node, false);
            di.bits.or(bits);
            di.ownBits.or(ownBits);
            if (!children.isEmpty() || di.ownBits.isEmpty()) {
                int newBit = nextBit++;
                di.bits.xor(ownBits);
                di.bits.set(newBit);
                di.ownBits.clear();
                di.ownBits.set(newBit);
                addOwnBits(newBit);
            }
            children.add(di);
            di.parents.add(this);
            return di;
        }

        private void addOwnBits(int newBit) {
            if (!bits.get(newBit)) {
                ownBits.set(newBit);
                bits.set(newBit);
                for (DominatorInfo parent : parents) {
                    parent.addOwnBits(newBit);
                }
            }
        }

        public boolean add(DominatorInfo i) {
            return add(i, true);
        }

        public boolean add(DominatorInfo i, boolean addParent) {
            boolean ret = true;
            if (addParent) {
                parents.add(i);
                i.children.add(this);
                bits.or(i.bits);
            } else {
                BitSet newBits = (BitSet) i.bits.clone();
                newBits.andNot(bits);
                newBits.andNot(i.ownBits);
                ret = !newBits.isEmpty();
                bits.or(newBits);
            }
            return ret;
        }

        public int parentCount() {
            return parents.size();
        }


        public FixedNode node() {
            return node;
        }

        public Collection<DominatorInfo> children() {
            return children;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof DominatorInfo)) {
                return false;
            }
            return ((DominatorInfo) obj).bits.equals(bits);
        }

        @Override
        public String toString() {
            return bits + " (o" + ownBits + ") " + node;
        }
    }
}
