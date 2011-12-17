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
package com.oracle.max.graal.compiler.util;

import java.util.*;

import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.graph.Node.Verbosity;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.PhiNode.PhiType;

public class LoopUtil {

    public static class Loop {
        private final LoopBeginNode loopBegin;
        private NodeBitMap cfgNodes;
        private Loop parent;
        private NodeBitMap exits;
        private NodeBitMap inOrBefore;
        private NodeBitMap inOrAfter;
        private NodeBitMap nodes;
        public Loop(LoopBeginNode loopBegin, NodeBitMap nodes, NodeBitMap exits) {
            this.loopBegin = loopBegin;
            this.cfgNodes = nodes;
            this.exits = exits;
        }

        public LoopBeginNode loopBegin() {
            return loopBegin;
        }

        public NodeBitMap cfgNodes() {
            return cfgNodes;
        }

        public NodeBitMap nodes() {
            if (nodes == null) {
                nodes = loopBegin().graph().createNodeBitMap();
                nodes.setUnion(inOrAfter());
                nodes.setIntersect(inOrBefore());
            }
            return nodes;
        }

        public Loop parent() {
            return parent;
        }

        public NodeBitMap exits() {
            return exits;
        }

        public void setParent(Loop parent) {
            this.parent = parent;
        }

        public boolean isChild(Loop loop) {
            return loop.parent != null && (loop.parent == this || loop.parent.isChild(this));
        }

        public NodeBitMap inOrAfter() {
            if (inOrAfter == null) {
                inOrAfter = LoopUtil.inOrAfter(this);
            }
            return inOrAfter;
        }

        public NodeBitMap inOrBefore() {
            if (inOrBefore == null) {
                inOrBefore = LoopUtil.inOrBefore(this, inOrAfter());
            }
            return inOrBefore;
        }

        public void invalidateCached() {
            inOrAfter = null;
            inOrBefore = null;
            nodes = null;
        }

        @Override
        public String toString() {
            return "Loop #" + loopBegin().toString(Verbosity.Id);
        }
    }

    public static List<Loop> computeLoops(StructuredGraph graph) {
        List<Loop> loops = new LinkedList<LoopUtil.Loop>();
        for (LoopBeginNode loopBegin : graph.getNodes(LoopBeginNode.class)) {
            NodeBitMap cfgNodes = markUpCFG(loopBegin, loopBegin.loopEnd()); // computeLoopNodes(loopBegin);
            cfgNodes.mark(loopBegin);
            NodeBitMap exits = computeLoopExits(loopBegin, cfgNodes);
            loops.add(new Loop(loopBegin, cfgNodes, exits));
        }
        for (Loop loop : loops) {
            for (Loop other : loops) {
                if (other != loop && other.cfgNodes().isMarked(loop.loopBegin())) {
                    if (loop.parent() == null || loop.parent().cfgNodes().isMarked(other.loopBegin())) {
                        loop.setParent(other);
                    }
                }
            }
        }
        return loops;
    }

    public static NodeBitMap computeLoopExits(LoopBeginNode loopBegin, NodeBitMap cfgNodes) {
        Graph graph = loopBegin.graph();
        NodeBitMap exits = graph.createNodeBitMap();
        for (Node n : cfgNodes) {
            if (IdentifyBlocksPhase.trueSuccessorCount(n) > 1) {
                for (Node sux : n.cfgSuccessors()) {
                    if (sux != null && !cfgNodes.isMarked(sux) && sux instanceof FixedNode) {
                        exits.mark(sux);
                    }
                }
            }
        }
        return exits;
    }

    public static NodeBitMap markUpCFG(LoopBeginNode loopBegin) {
        return markUpCFG(loopBegin, loopBegin.loopEnd());
    }

    public static NodeBitMap markUpCFG(LoopBeginNode loopBegin, FixedNode from) {
        NodeFlood workCFG = loopBegin.graph().createNodeFlood();
        workCFG.add(from);
        NodeBitMap loopNodes = loopBegin.graph().createNodeBitMap();
        for (Node n : workCFG) {
            if (n == loopBegin) {
                continue;
            }
            loopNodes.mark(n);
            if (n instanceof LoopBeginNode) {
                workCFG.add(((LoopBeginNode) n).loopEnd());
            }
            for (Node pred : n.cfgPredecessors()) {
                workCFG.add(pred);
            }
        }
        return loopNodes;
    }

    private static NodeBitMap inOrAfter(Loop loop) {
        return inOrAfter(loop, loop.cfgNodes());
    }

    private static NodeBitMap inOrAfter(Loop loop, NodeBitMap cfgNodes) {
        return inOrAfter(loop, cfgNodes, true);
    }

    private static NodeBitMap inOrAfter(Loop loop, NodeBitMap cfgNodes, boolean full) {
        Graph graph = loop.loopBegin().graph();
        NodeBitMap inOrAfter = graph.createNodeBitMap();
        NodeFlood work = graph.createNodeFlood();
        work.addAll(cfgNodes);
        for (Node n : work) {
            markWithState(n, inOrAfter);
            if (full) {
                for (Node sux : n.successors()) {
                    if (sux != null) {
                        work.add(sux);
                    }
                }
            }
            for (Node usage : n.usages()) {
                if (usage instanceof PhiNode) { // filter out data graph cycles
                    PhiNode phi = (PhiNode) usage;
                    MergeNode merge = phi.merge();
                    if (merge instanceof LoopBeginNode) {
                        LoopBeginNode phiLoop = (LoopBeginNode) merge;
                        if (phi.valueAt(phiLoop.loopEnd()) == n) {
                            continue;
                        }
                    }
                }
                work.add(usage);
            }
        }
        return inOrAfter;
    }

    private static NodeBitMap inOrBefore(Loop loop) {
        return inOrBefore(loop, inOrAfter(loop));
    }

    private static NodeBitMap inOrBefore(Loop loop, NodeBitMap inOrAfter) {
        return inOrBefore(loop, inOrAfter, loop.cfgNodes());
    }

    private static NodeBitMap inOrBefore(Loop loop, NodeBitMap inOrAfter, NodeBitMap cfgNodes) {
        return inOrBefore(loop, inOrAfter, cfgNodes, true);
    }

    private static NodeBitMap inOrBefore(Loop loop, NodeBitMap inOrAfter, NodeBitMap cfgNodes, boolean full) {
        Graph graph = loop.loopBegin().graph();
        NodeBitMap inOrBefore = graph.createNodeBitMap();
        NodeFlood work = graph.createNodeFlood();
        work.addAll(cfgNodes);
        for (Node n : work) {
            inOrBefore.mark(n);
            if (full) {
                if (n.predecessor() != null) {
                    work.add(n.predecessor());
                }
            }
            if (n instanceof PhiNode) { // filter out data graph cycles
                PhiNode phi = (PhiNode) n;
                if (phi.type() == PhiType.Value) {
                    int backIndex = -1;
                    MergeNode merge = phi.merge();
                    if (merge instanceof LoopBeginNode && cfgNodes.isNotNewNotMarked(((LoopBeginNode) merge).loopEnd())) {
                        LoopBeginNode phiLoop = (LoopBeginNode) merge;
                        backIndex = phiLoop.phiPredecessorIndex(phiLoop.loopEnd());
                    }
                    for (int i = 0; i < phi.valueCount(); i++) {
                        if (i != backIndex) {
                            work.add(phi.valueAt(i));
                        }
                    }
                }
            } else {
                for (Node in : n.inputs()) {
                    if (in != null) {
                        work.add(in);
                    }
                }
                if (full) {
                    for (Node sux : n.cfgSuccessors()) { // go down into branches that are not 'inOfAfter'
                        if (sux != null && !inOrAfter.isMarked(sux)) {
                            work.add(sux);
                        }
                    }
                    if (n instanceof LoopBeginNode && n != loop.loopBegin()) {
                        Loop p = loop.parent;
                        boolean isParent = false;
                        while (p != null) {
                            if (p.loopBegin() == n) {
                                isParent = true;
                                break;
                            }
                            p = p.parent;
                        }
                        if (!isParent) {
                            work.add(((LoopBeginNode) n).loopEnd());
                        }
                    }
                }
                if (cfgNodes.isNotNewMarked(n)) { //add all values from the exits framestates
                    for (Node sux : n.cfgSuccessors()) {
                        if (loop.exits().isNotNewMarked(sux) && sux instanceof StateSplit) {
                            FrameState stateAfter = ((StateSplit) sux).stateAfter();
                            while (stateAfter != null) {
                                for (Node in : stateAfter.inputs()) {
                                    if (!(in instanceof FrameState)) {
                                        work.add(in);
                                    }
                                }
                                stateAfter = stateAfter.outerFrameState();
                            }
                        }
                    }
                }
                if (n instanceof MergeNode) { //add phis & counters
                    for (Node usage : n.usages()) {
                        if (!(usage instanceof LoopEndNode)) {
                            work.add(usage);
                        }
                    }
                }
            }
        }
        return inOrBefore;
    }

    private static void markWithState(Node n, NodeBitMap map) {
        map.mark(n);
        if (n instanceof StateSplit) {
            FrameState stateAfter = ((StateSplit) n).stateAfter();
            while (stateAfter != null) {
                map.mark(stateAfter);
                stateAfter = stateAfter.outerFrameState();
            }
        }
    }

    private static void clearWithState(Node n, NodeBitMap map) {
        map.clear(n);
        if (n instanceof StateSplit) {
            FrameState stateAfter = ((StateSplit) n).stateAfter();
            while (stateAfter != null) {
                map.clear(stateAfter);
                stateAfter = stateAfter.outerFrameState();
            }
        }
    }
}
