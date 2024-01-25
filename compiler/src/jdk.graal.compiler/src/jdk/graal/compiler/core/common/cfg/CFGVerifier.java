/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.common.cfg;

import java.util.ArrayDeque;
import java.util.Deque;

import jdk.graal.compiler.debug.Assertions;

public class CFGVerifier {

    public static <T extends BasicBlock<T>, C extends AbstractControlFlowGraph<T>> boolean verify(C cfg) {
        for (T block : cfg.getBlocks()) {
            assert block.getId() <= AbstractControlFlowGraph.LAST_VALID_BLOCK_INDEX : block.getId();
            assert cfg.getBlocks()[block.getId()] == block : Assertions.errorMessageContext("block", block, "cfgBlockId", cfg.getBlocks()[block.getId()]);

            for (int i = 0; i < block.getPredecessorCount(); i++) {
                T pred = block.getPredecessorAt(i);
                assert pred.containsSucc(block);
                assert pred.getId() < block.getId() || pred.isLoopEnd();
            }

            for (int i = 0; i < block.getSuccessorCount(); i++) {
                T sux = block.getSuccessorAt(i);
                assert sux.containsPred(block);
                assert sux.getId() > block.getId() || sux.isLoopHeader();
            }

            if (block.getDominator() != null) {
                assert block.getDominator().getId() < block.getId() : Assertions.errorMessage(block, block.getDominator());

                BasicBlock<?> domChild = block.getDominator().getFirstDominated();
                while (domChild != null) {
                    if (domChild == block) {
                        break;
                    }
                    domChild = domChild.getDominatedSibling();
                }
                assert domChild != null : "dominators must contain block";
            }

            T dominated = block.getFirstDominated();
            while (dominated != null) {
                assert dominated.getId() > block.getId() : Assertions.errorMessageContext("dominated", dominated, "block", block);
                assert dominated.getDominator() == block : Assertions.errorMessageContext("domianted", dominated, "dominated.dom", dominated.getDominator(), "block", block);
                dominated = dominated.getDominatedSibling();
            }

            T postDominatorBlock = block.getPostdominator();
            if (postDominatorBlock != null) {
                assert block.getSuccessorCount() > 0 : "block has post-dominator block, but no successors";

                BlockMap<Boolean> visitedBlocks = new BlockMap<>(cfg);
                visitedBlocks.put(block, true);

                Deque<T> stack = new ArrayDeque<>();
                for (int i = 0; i < block.getSuccessorCount(); i++) {
                    T sux = block.getSuccessorAt(i);
                    visitedBlocks.put(sux, true);
                    stack.push(sux);
                }

                while (stack.size() > 0) {
                    T tos = stack.pop();
                    assert tos.getId() <= postDominatorBlock.getId() : Assertions.errorMessageContext("tos", tos, "tos.getid", tos.getId(), "postDom", postDominatorBlock, "postDomId",
                                    postDominatorBlock.getId());
                    if (tos == postDominatorBlock) {
                        continue; // found a valid path
                    }
                    assert tos.getSuccessorCount() > 0 : "no path found";

                    for (int i = 0; i < tos.getSuccessorCount(); i++) {
                        T sux = tos.getSuccessorAt(i);
                        if (visitedBlocks.get(sux) == null) {
                            visitedBlocks.put(sux, true);
                            stack.push(sux);
                        }
                    }
                }
            }

            assert cfg.getLoops() == null || !block.isLoopHeader() || block.getLoop().getHeader() == block : Assertions.errorMessage(block, block.getLoop());
        }

        if (cfg.getLoops() != null) {
            for (Loop<T> loop : cfg.getLoops()) {
                assert loop.getHeader().isLoopHeader() : "LoopHeader block must be loop header " + Assertions.errorMessageContext("loop", loop, "loop.getheader", loop.getHeader());

                for (T block : loop.getBlocks()) {
                    assert block.getId() >= loop.getHeader().getId() : Assertions.errorMessageContext("block", block, "loop.getheader", loop.getHeader());

                    Loop<?> blockLoop = block.getLoop();
                    while (blockLoop != loop) {
                        assert blockLoop != null;
                        blockLoop = blockLoop.getParent();
                    }

                    if (!(block.isLoopHeader() && block.getLoop() == loop)) {
                        for (int i = 0; i < block.getPredecessorCount(); i++) {
                            T pred = block.getPredecessorAt(i);
                            if (!loop.getBlocks().contains(pred)) {
                                assert false : "Loop " + loop + " does not contain " + pred;
                                return false;
                            }
                        }
                    }
                }

                for (T block : loop.getLoopExits()) {
                    assert block.getId() >= loop.getHeader().getId() : Assertions.errorMessageContext("block", block, "loop", loop, "loop.gethead", loop.getHeader());

                    Loop<?> blockLoop = block.getLoop();
                    while (blockLoop != null) {
                        blockLoop = blockLoop.getParent();
                        assert blockLoop != loop : "Parent loop must be different than loop that is exitted " + Assertions.errorMessageContext("blockLoop", blockLoop, "loop", loop);
                    }
                }
            }
        }

        return true;
    }
}
