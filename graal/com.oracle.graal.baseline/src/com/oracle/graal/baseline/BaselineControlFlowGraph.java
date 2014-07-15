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

import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.java.*;
import com.oracle.graal.java.BciBlockMapping.BciBlock;

public class BaselineControlFlowGraph implements AbstractControlFlowGraph<BciBlock> {

    private List<BciBlock> blocks;
    private Collection<Loop<BciBlock>> loops;

    public static BaselineControlFlowGraph compute(BciBlockMapping blockMap) {
        try (Scope ds = Debug.scope("BaselineCFG", blockMap)) {
            BaselineControlFlowGraph cfg = new BaselineControlFlowGraph(blockMap);
            cfg.computeLoopInformation(blockMap);
            AbstractControlFlowGraph.computeDominators(cfg);

            assert CFGVerifier.verify(cfg);

            return cfg;
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    private BaselineControlFlowGraph(BciBlockMapping blockMap) {
        blocks = blockMap.blocks;
        loops = new ArrayList<>();
    }

    public List<BciBlock> getBlocks() {
        return blocks;
    }

    public Collection<Loop<BciBlock>> getLoops() {
        return loops;
    }

    public BciBlock getStartBlock() {
        if (!blocks.isEmpty()) {
            return blocks.get(0);
        }
        return null;
    }

    private void computeLoopInformation(BciBlockMapping blockMap) {
        try (Indent indent = Debug.logAndIndent("computeLoopInformation")) {
            for (BciBlock block : blocks) {
                calcLoop(block, blockMap);
                Debug.log("Block: %s, Loop: %s", block, block.getLoop());
            }
        }
    }

    private Loop<BciBlock> getLoop(int index, BciBlockMapping blockMap) {
        BciBlock header = blockMap.getLoopHeader(index);
        assert header.getLoopDepth() > 0;
        Loop<BciBlock> loop = header.getLoop();

        if (loop == null) {
            Loop<BciBlock> parent = null;

            if (header.getLoopDepth() > 1) {
                // Recursively create out loops.
                Iterator<Integer> i = header.loopIdIterable().iterator();
                assert i.hasNext() : "BciBlock.loopIdIterable() must return exactly BciBlock.getLoopDepth() elements!";
                int outerLoopId = i.next();
                assert index == outerLoopId : "The first loopId must be the id of the loop that is started by this header!";
                assert i.hasNext() : "BciBlock.loopIdIterable() must return exactly BciBlock.getLoopDepth() elements!";
                outerLoopId = i.next();
                parent = getLoop(outerLoopId, blockMap);
            }

            loop = new BaselineLoop(parent, index, header);
            loops.add(loop);
            header.setLoop(loop);
        }
        return loop;
    }

    private void calcLoop(BciBlock block, BciBlockMapping blockMap) {
        int loopId = block.getLoopId();
        if (loopId != -1) {
            block.setLoop(getLoop(loopId, blockMap));

        }
    }

}
