/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser.util;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.TerminatingInstruction;

public final class LLVMControlFlowGraph {
    private CFGBlock[] blocks;
    private List<CFGLoop> cfgLoops;
    private int nextLoop = 0;
    private boolean reducible = true;

    public final class CFGBlock {
        private final InstructionBlock instructionBlock;
        public int id;
        public List<CFGBlock> sucs = new ArrayList<>();
        public List<CFGBlock> preds = new ArrayList<>();
        public boolean visited = false;
        public boolean active = false;
        public boolean isLoopHeader = false;
        public BitSet loops;
        public int loopId;

        public CFGBlock(InstructionBlock block) {
            this.instructionBlock = block;
            this.id = block.getBlockIndex();
            loops = new BitSet(64);
        }

        @Override
        public String toString() {
            return instructionBlock.toString();
        }
    }

    public final class CFGLoop {
        private CFGBlock loopHeader;
        private List<CFGBlock> body;
        private List<CFGLoop> innerLoops;
        private Set<CFGBlock> successors;
        private final int id;

        public CFGLoop(int id) {
            this.id = id;
            body = new ArrayList<>();
            innerLoops = new ArrayList<>();
        }

        public List<CFGBlock> getBody() {
            return body;
        }

        public List<CFGLoop> getInnerLoops() {
            return innerLoops;
        }

        public Set<CFGBlock> getSuccessors() {
            if (successors == null) {
                calculateSuccessors();
            }
            return this.successors;
        }

        /**
         * Calculates the successors of this loop with successors of inner loops potentially
         * forwarded to the outer loop.
         */
        private void calculateSuccessors() {
            successors = new HashSet<>();
            for (CFGBlock s : loopHeader.sucs) {
                if (!isInLoop(s)) {
                    successors.add(s);
                }
            }
            for (CFGBlock b : body) {
                // for each inner loop, add all successors which are not in the outer loop
                // to the successors of the outer loop
                if (b.isLoopHeader) {
                    for (CFGLoop l : innerLoops) {
                        if (l.getHeader().equals(b)) {
                            for (CFGBlock ib : l.getSuccessors()) {
                                if (!isInLoop(ib)) {
                                    successors.add(ib);
                                }
                            }
                        }
                    }
                } else {
                    for (CFGBlock s : b.sucs) {
                        if (!isInLoop(s)) {
                            successors.add(s);
                        }
                    }
                }
            }
        }

        public int[] getSuccessorIDs() {
            if (successors == null) {
                calculateSuccessors();
            }
            int[] sucIDs = new int[successors.size()];
            int i = 0;
            for (CFGBlock b : successors) {
                sucIDs[i++] = b.id;
            }
            return sucIDs;
        }

        public boolean isInLoop(CFGBlock block) {
            return block == loopHeader || body.contains(block);
        }

        public CFGBlock getHeader() {
            return loopHeader;
        }

        @Override
        public String toString() {
            return "Loop: " + this.id + " - Header: " + this.loopHeader.toString();
        }
    }

    public LLVMControlFlowGraph(InstructionBlock[] blocks) {
        this.blocks = new CFGBlock[blocks.length];
        for (int i = 0; i < blocks.length; i++) {
            this.blocks[i] = new CFGBlock(blocks[i]);
        }
        cfgLoops = new ArrayList<>();
    }

    private void resolveEdges() {
        for (CFGBlock block : blocks) {
            TerminatingInstruction term = block.instructionBlock.getTerminatingInstruction();
            // set successors and predecessors
            for (int i = 0; i < term.getSuccessorCount(); i++) {
                int sucId = term.getSuccessor(i).getBlockIndex();
                block.sucs.add(this.blocks[sucId]);
                this.blocks[sucId].preds.add(block);
            }
        }
    }

    public List<CFGLoop> getCFGLoops() {
        return cfgLoops;
    }

    public boolean isReducible() {
        return reducible;
    }

    public void build() {
        reducible = true;
        // set successors and predecessors
        resolveEdges();
        if (!(openLoops(blocks[0]).isEmpty())) {
            reducible = false;
            return;
        }
        try {
            sortLoops();
        } catch (ControlFlowBailoutException e) {
            reducible = false;
            return;
        }
        for (CFGLoop l : getCFGLoops()) {
            l.calculateSuccessors();
        }
    }

    private boolean sortLoops() throws ControlFlowBailoutException {
        List<CFGLoop> sorted = new ArrayList<>();
        List<CFGLoop> active = new ArrayList<>();
        for (CFGLoop l : getCFGLoops()) {
            sortLoop(sorted, active, l);
        }
        cfgLoops = sorted;
        return true;
    }

    private void sortLoop(List<CFGLoop> sorted, List<CFGLoop> active, CFGLoop loop) throws ControlFlowBailoutException {
        if (sorted.contains(loop)) {
            return;
        }
        active.add(loop);
        for (CFGBlock b : loop.body) {
            if (b.isLoopHeader) {
                CFGLoop inner = cfgLoops.get(b.loopId);
                if (active.contains(inner)) {
                    // catches case that there is a stack overflow because two loop nodes are being
                    // called iteratively
                    // from one another, without one being left beforehand
                    throw new ControlFlowBailoutException("Irreducible nestedness!");
                }
                sortLoop(sorted, active, inner);
                loop.innerLoops.add(inner);
            }
        }
        loop.body.sort(new Comparator<CFGBlock>() {
            @Override
            public int compare(CFGBlock o1, CFGBlock o2) {
                return o2.id - o1.id;
            }
        });
        sorted.add(loop);
        active.remove(loop);
    }

    private BitSet openLoops(CFGBlock block) {
        if (block.visited) {
            if (block.active) {
                // Reached block via backward branch.
                makeLoopHeader(block);
                // Return cached loop information for this block.
                return block.loops;
            } else if (block.isLoopHeader) {
                BitSet outerLoops = new BitSet();
                outerLoops.or(block.loops);
                outerLoops.clear(block.loopId);
                return outerLoops;
            } else {
                return block.loops;
            }
        }
        block.visited = true;
        block.active = true;
        BitSet loops = new BitSet();
        for (CFGBlock successor : block.sucs) {
            // Recursively process successors.
            loops.or(openLoops(successor));
            if (successor.active) {
                // Reached block via backward branch.
                loops.set(successor.loopId);
            }
        }
        block.loops = loops;
        if (block.isLoopHeader) {
            loops.clear(block.loopId);
        }
        block.active = false;
        // add blocks to all loops they are contained in
        for (int i = 0; i < nextLoop; i++) {
            if (loops.get(i)) {
                this.cfgLoops.get(i).body.add(block);
            }
        }
        return loops;
    }

    private void makeLoopHeader(CFGBlock block) {
        if (!block.isLoopHeader) {
            block.isLoopHeader = true;
            assert block.loops.isEmpty();
            block.loops.set(nextLoop);
            cfgLoops.add(new CFGLoop(nextLoop));
            cfgLoops.get(nextLoop).loopHeader = block;
            block.loopId = nextLoop++;
        }
        assert block.loops.cardinality() == 1;
    }

    private class ControlFlowBailoutException extends Exception {

        private static final long serialVersionUID = 1L;

        ControlFlowBailoutException(String string) {
            super(string);
        }
    }
}
