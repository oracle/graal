/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.baseline;

import java.util.*;

import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.java.*;
import com.oracle.graal.java.BciBlockMapping.BciBlock;

public class BaselineControlFlowGraph implements AbstractControlFlowGraph<BciBlock> {

    private BciBlock[] blocks;
    private Collection<Loop<BciBlock>> loops;
    private BitSet visited;

    public BaselineControlFlowGraph(BciBlockMapping blockMap) {
        blocks = blockMap.blocks.toArray(new BciBlock[0]);
        loops = new ArrayList<>();
        computeLoopInformation();
    }

    public BciBlock[] getBlocks() {
        return blocks;
    }

    public Collection<Loop<BciBlock>> getLoops() {
        return loops;
    }

    public BciBlock getStartBlock() {
        if (blocks.length > 0) {
            return blocks[0];
        }
        return null;
    }

    private void computeLoopInformation() {
        visited = new BitSet(blocks.length);
        Deque<BaselineLoop> stack = new ArrayDeque<>();
        for (int i = blocks.length - 1; i >= 0; i--) {
            BciBlock block = blocks[i];
            calcLoop(block, stack);
        }
    }

    private void calcLoop(BciBlock block, Deque<BaselineLoop> stack) {
        if (visited.get(block.getId())) {
            return;
        }
        visited.set(block.getId());
        if (block.isLoopEnd()) {
            BciBlock loopHeader = getLoopHeader(block);
            BaselineLoop l = new BaselineLoop(stack.peek(), loops.size(), loopHeader);
            loops.add(l);
            stack.push(l);
        }
        block.loop = stack.peek();
        if (block.isLoopHeader()) {
            assert block.loop.header.equals(block);
            stack.pop();
        }
        for (BciBlock pred : block.getPredecessors()) {
            calcLoop(pred, stack);
        }
    }

    private static BciBlock getLoopHeader(BciBlock block) {
        assert block.isLoopEnd();
        for (BciBlock sux : block.getSuccessors()) {
            if (sux.isLoopHeader() && sux.getId() <= block.getId() && block.loops == sux.loops) {
                return sux;
            }
        }
        throw GraalInternalError.shouldNotReachHere("No loop header found for " + block);
    }

}
