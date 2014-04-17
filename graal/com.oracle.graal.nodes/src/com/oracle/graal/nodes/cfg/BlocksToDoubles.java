/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.cfg;

import java.util.*;

import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.util.*;

public class BlocksToDoubles {

    private final IdentityHashMap<AbstractBlock<?>, Double> nodeProbabilities;

    public BlocksToDoubles(int numberOfNodes) {
        this.nodeProbabilities = new IdentityHashMap<>(numberOfNodes);
    }

    public void put(AbstractBlock<?> n, double value) {
        assert value >= 0.0 : value;
        nodeProbabilities.put(n, value);
    }

    public boolean contains(AbstractBlock<?> n) {
        return nodeProbabilities.containsKey(n);
    }

    public double get(AbstractBlock<?> n) {
        Double value = nodeProbabilities.get(n);
        assert value != null;
        return value;
    }

    public static BlocksToDoubles createFromNodeProbability(NodesToDoubles nodeProbabilities, ControlFlowGraph cfg) {
        BlocksToDoubles blockProbabilities = new BlocksToDoubles(cfg.getBlocks().length);
        for (Block block : cfg.getBlocks()) {
            blockProbabilities.put(block, nodeProbabilities.get(block.getBeginNode()));
        }
        assert verify(nodeProbabilities, cfg, blockProbabilities) : "Probabilities differ for nodes in the same block.";
        return blockProbabilities;
    }

    private static boolean verify(NodesToDoubles nodeProbabilities, ControlFlowGraph cfg, BlocksToDoubles blockProbabilities) {
        for (Block b : cfg.getBlocks()) {
            double p = blockProbabilities.get(b);
            for (FixedNode n : b.getNodes()) {
                if (nodeProbabilities.get(n) != p) {
                    return false;
                }
            }
        }
        return true;
    }
}
