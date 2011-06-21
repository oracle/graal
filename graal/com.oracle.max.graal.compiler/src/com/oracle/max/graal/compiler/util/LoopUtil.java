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

import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.graph.*;

public class LoopUtil {

    public static class Loop {
        private final LoopBegin loopBegin;
        private final NodeBitMap nodes;
        private Loop parent;
        private final List<FixedNode> exits;
        public Loop(LoopBegin loopBegin, NodeBitMap nodes) {
            this.loopBegin = loopBegin;
            this.nodes = nodes;
            this.exits = new LinkedList<FixedNode>();
        }

        public LoopBegin loopBegin() {
            return loopBegin;
        }

        public NodeBitMap nodes() {
            return nodes;
        }

        public Loop parent() {
            return parent;
        }

        public List<FixedNode> exist() {
            return exits;
        }

        public void setParent(Loop parent) {
            this.parent = parent;
        }
    }

    public static List<Loop> computeLoops(Graph graph) {
        List<Loop> loops = new LinkedList<LoopUtil.Loop>();
        for (LoopBegin loopBegin : graph.getNodes(LoopBegin.class)) {
            NodeBitMap nodes = computeLoopNodes(loopBegin);
            Loop loop = new Loop(loopBegin, nodes);
            NodeFlood workCFG = graph.createNodeFlood();
            workCFG.add(loopBegin.loopEnd());
            for (Node n : workCFG) {
                if (n == loopBegin) {
                    continue;
                }
                if (IdentifyBlocksPhase.trueSuccessorCount(n) > 1) {
                    for (Node sux : n.cfgSuccessors()) {
                        if (!nodes.isMarked(sux) && sux instanceof FixedNode) {
                            loop.exits.add((FixedNode) sux);
                        }
                    }
                }
                for (Node pred : n.cfgPredecessors()) {
                    workCFG.add(pred);
                }
            }
            loops.add(loop);
        }
        for (Loop loop : loops) {
            for (Loop other : loops) {
                if (other != loop && other.nodes().isMarked(loop.loopBegin())) {
                    if (loop.parent() == null || loop.parent().nodes().isMarked(other.loopBegin())) {
                        loop.setParent(other);
                    }
                }
            }
        }
        return loops;
    }

    public static NodeBitMap computeLoopNodes(LoopBegin loopBegin) {
        LoopEnd loopEnd = loopBegin.loopEnd();
        NodeBitMap loopNodes = loopBegin.graph().createNodeBitMap();
        NodeFlood workCFG = loopBegin.graph().createNodeFlood();
        NodeFlood workData1 = loopBegin.graph().createNodeFlood();
        NodeFlood workData2 = loopBegin.graph().createNodeFlood();
        workCFG.add(loopEnd);
        for (Node n : workCFG) {
            workData1.add(n);
            workData2.add(n);
            loopNodes.mark(n);
            if (n == loopBegin) {
                continue;
            }
            if (n instanceof LoopBegin) {
                workCFG.add(((LoopBegin) n).loopEnd());
            }
            for (Node pred : n.cfgPredecessors()) {
                workCFG.add(pred);
            }
        }
        NodeBitMap inOrAfter = loopBegin.graph().createNodeBitMap();
        for (Node n : workData1) {
            inOrAfter.mark(n);
            for (Node usage : n.dataUsages()) {
                workData1.add(usage);
            }
        }
        NodeBitMap inOrBefore = loopBegin.graph().createNodeBitMap();
        for (Node n : workData2) {
            inOrBefore.mark(n);
            for (Node input : n.dataInputs()) {
                workData2.add(input);
            }
            if (n instanceof Merge) { //add phis & counters
                for (Node usage : n.dataUsages()) {
                    workData2.add(usage);
                }
            }
        }
        inOrBefore.setIntersect(inOrAfter);
        loopNodes.setUnion(inOrBefore);
        return loopNodes;
    }
}
