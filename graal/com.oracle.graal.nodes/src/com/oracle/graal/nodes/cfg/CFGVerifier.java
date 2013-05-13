/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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

public class CFGVerifier {

    public static boolean verify(ControlFlowGraph cfg) {
        for (Block block : cfg.getBlocks()) {
            assert block.getId() >= 0;
            assert cfg.getBlocks()[block.getId()] == block;

            for (Block pred : block.getPredecessors()) {
                assert pred.getSuccessors().contains(block);
                assert pred.getId() < block.getId() || pred.isLoopEnd();
            }

            for (Block sux : block.getSuccessors()) {
                assert sux.getPredecessors().contains(block);
                assert sux.getId() > block.getId() || sux.isLoopHeader();
            }

            if (block.getDominator() != null) {
                assert block.getDominator().getId() < block.getId();
                assert block.getDominator().getDominated().contains(block);
            }
            for (Block dominated : block.getDominated()) {
                assert dominated.getId() > block.getId();
                assert dominated.getDominator() == block;
            }

            Block postDominatorBlock = block.getPostdominator();
            if (postDominatorBlock != null) {
                assert block.getSuccessorCount() > 0 : "block has post-dominator block, but no successors";

                BlockMap<Boolean> visitedBlocks = new BlockMap<>(cfg);
                visitedBlocks.put(block, true);

                Deque<Block> stack = new ArrayDeque<>();
                for (Block sux : block.getSuccessors()) {
                    visitedBlocks.put(sux, true);
                    stack.push(sux);
                }

                while (stack.size() > 0) {
                    Block tos = stack.pop();
                    assert tos.getId() <= postDominatorBlock.getId();
                    if (tos == postDominatorBlock) {
                        continue; // found a valid path
                    }
                    assert tos.getSuccessorCount() > 0 : "no path found";

                    for (Block sux : tos.getSuccessors()) {
                        if (visitedBlocks.get(sux) == null) {
                            visitedBlocks.put(sux, true);
                            stack.push(sux);
                        }
                    }
                }
            }

            assert cfg.getLoops() == null || !block.isLoopHeader() || block.getLoop().header == block : block.beginNode;
        }

        if (cfg.getLoops() != null) {
            for (Loop loop : cfg.getLoops()) {
                assert loop.header.isLoopHeader();

                for (Block block : loop.blocks) {
                    assert block.getId() >= loop.header.getId();

                    Loop blockLoop = block.getLoop();
                    while (blockLoop != loop) {
                        assert blockLoop != null;
                        blockLoop = blockLoop.parent;
                    }

                    if (!(block.isLoopHeader() && block.getLoop() == loop)) {
                        for (Block pred : block.getPredecessors()) {
                            if (!loop.blocks.contains(pred)) {
                                assert false : "Loop " + loop + " does not contain " + pred;
                                return false;
                            }
                        }
                    }
                }

                for (Block block : loop.exits) {
                    assert block.getId() >= loop.header.getId();

                    Loop blockLoop = block.getLoop();
                    while (blockLoop != null) {
                        blockLoop = blockLoop.parent;
                        assert blockLoop != loop;
                    }
                }
            }
        }

        return true;
    }
}
