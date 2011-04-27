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

import com.sun.c1x.*;
import com.sun.c1x.debug.*;
import com.sun.c1x.ir.*;

/**
 * This class finds and splits "critical" edges in the control flow graph.
 * An edge between two blocks {@code A} and {@code B} is "critical" if {@code A}
 * has more than one successor and {@code B} has more than one predecessor. Such
 * edges are split by adding a block between the two blocks.
 *
 * @author Thomas Wuerthinger
 */
public class CriticalEdgeFinder implements BlockClosure {

    private final IR ir;

    /**
     * The graph edges represented as a map from source to target nodes.
     * Using a linked hash map makes compilation tracing more deterministic and thus eases debugging.
     */
    private Map<BlockBegin, Set<BlockBegin>> edges = C1XOptions.DetailedAsserts ?
                    new LinkedHashMap<BlockBegin, Set<BlockBegin>>() :
                    new HashMap<BlockBegin, Set<BlockBegin>>();

    public CriticalEdgeFinder(IR ir) {
        this.ir = ir;
    }

    public void apply(BlockBegin block) {
        if (block.numberOfSux() >= 2) {
            for (BlockBegin succ : block.end().successors()) {
                if (succ.numberOfPreds() >= 2) {
                    // TODO: (tw) probably we don't have to make it a critical edge if succ only contains the _same_ predecessor multiple times.
                    recordCriticalEdge(block, succ);
                }
            }
        }
    }

    private void recordCriticalEdge(BlockBegin block, BlockBegin succ) {
        if (!edges.containsKey(block)) {
            edges.put(block, new HashSet<BlockBegin>());
        }

        edges.get(block).add(succ);
    }

    public void splitCriticalEdges() {
        for (BlockBegin from : edges.keySet()) {
            for (BlockBegin to : edges.get(from)) {
                BlockBegin split = ir.splitEdge(from, to);
                if (C1XOptions.PrintHIR) {
                    TTY.println("Split edge between block %d and block %d, creating new block %d", from.blockID, to.blockID, split.blockID);
                }
            }
        }
    }
}
