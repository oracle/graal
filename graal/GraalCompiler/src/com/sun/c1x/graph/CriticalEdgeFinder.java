/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.graph;

import java.util.*;

import com.oracle.graal.graph.*;
import com.sun.c1x.*;
import com.sun.c1x.debug.*;
import com.sun.c1x.ir.*;
import com.sun.c1x.lir.*;

/**
 * This class finds and splits "critical" edges in the control flow graph.
 * An edge between two blocks {@code A} and {@code B} is "critical" if {@code A}
 * has more than one successor and {@code B} has more than one predecessor. Such
 * edges are split by adding a block between the two blocks.
 */
public class CriticalEdgeFinder {

    private final List<LIRBlock> lirBlocks;
    private final Graph graph;

    /**
     * The graph edges represented as a map from source to target nodes.
     * Using a linked hash map makes compilation tracing more deterministic and thus eases debugging.
     */
    private Map<LIRBlock, Set<LIRBlock>> edges = C1XOptions.DetailedAsserts ?
                    new LinkedHashMap<LIRBlock, Set<LIRBlock>>() :
                    new HashMap<LIRBlock, Set<LIRBlock>>();

    public CriticalEdgeFinder(List<LIRBlock> lirBlocks, Graph graph) {
        this.lirBlocks = lirBlocks;
        this.graph = graph;
        for (LIRBlock block : lirBlocks) {
            apply(block);
        }

    }

    private void apply(LIRBlock block) {
        if (block.numberOfSux() >= 2) {
            for (LIRBlock succ : block.blockSuccessors()) {
                if (succ.numberOfPreds() >= 2) {
                    // TODO: (tw) probably we don't have to make it a critical edge if succ only contains the _same_ predecessor multiple times.
                    recordCriticalEdge(block, succ);
                }
            }
        }
    }

    private void recordCriticalEdge(LIRBlock block, LIRBlock succ) {
        if (!edges.containsKey(block)) {
            edges.put(block, new HashSet<LIRBlock>());
        }

        edges.get(block).add(succ);
    }

    public void splitCriticalEdges() {
        for (Map.Entry<LIRBlock, Set<LIRBlock>> entry : edges.entrySet()) {
            LIRBlock from = entry.getKey();
            for (LIRBlock to : entry.getValue()) {
                LIRBlock split = splitEdge(from, to);
                if (C1XOptions.PrintHIR) {
                    TTY.println("Split edge between block %d and block %d, creating new block %d", from.blockID(), to.blockID(), split.blockID());
                }
            }
        }
    }


    /**
     * Creates and inserts a new block between this block and the specified successor,
     * altering the successor and predecessor lists of involved blocks appropriately.
     * @param source the source of the edge
     * @param target the successor before which to insert a block
     * @return the new block inserted
     */
    public LIRBlock splitEdge(LIRBlock source, LIRBlock target) {

        // create new successor and mark it for special block order treatment
        LIRBlock newSucc = new LIRBlock(lirBlocks.size());
        lirBlocks.add(newSucc);

        // This goto is not a safepoint.
        Goto e = new Goto(target.getInstructions().get(0), graph);
        newSucc.getInstructions().add(e);

        // link predecessor to new block
        ((BlockEnd) source.getInstructions().get(source.getInstructions().size() - 1)).successors().replace(target.getInstructions().get(0), newSucc.getInstructions().get(0));

        source.substituteSuccessor(target, newSucc);
        target.substitutePredecessor(source, newSucc);
        newSucc.blockPredecessors().add(source);
        newSucc.blockSuccessors().add(target);

        return newSucc;
    }
}
