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
package com.sun.c1x.opt;

import java.util.*;

import com.sun.c1x.*;
import com.sun.c1x.graph.*;
import com.sun.c1x.ir.*;

/**
 * Implements global value numbering based on dominators.
 *
 * @author Ben L. Titzer
 */
public class GlobalValueNumberer {

    final IR ir;
    final HashMap<BlockBegin, ValueMap> valueMaps;
    final InstructionSubstituter subst;
    ValueMap currentMap;

    /**
     * Creates a new GlobalValueNumbering pass and performs it on the IR.
     *
     * @param ir the IR on which to perform global value numbering
     */
    public GlobalValueNumberer(IR ir) {
        this.ir = ir;
        this.subst = new InstructionSubstituter(ir);
        List<BlockBegin> blocks = ir.linearScanOrder();
        valueMaps = new HashMap<BlockBegin, ValueMap>(blocks.size());
        optimize(blocks);
        subst.finish();
    }

    void optimize(List<BlockBegin> blocks) {
        int numBlocks = blocks.size();
        BlockBegin startBlock = blocks.get(0);
        assert startBlock == ir.startBlock && startBlock.numberOfPreds() == 0 && startBlock.dominator() == null : "start block incorrect";

        // initial value map, with nesting 0
        valueMaps.put(startBlock, new ValueMap());

        for (int i = 1; i < numBlocks; i++) {
            // iterate through all the blocks
            BlockBegin block = blocks.get(i);

            int numPreds = block.numberOfPreds();
            assert numPreds > 0 : "block must have predecessors";

            BlockBegin dominator = block.dominator();
            assert dominator != null : "dominator must exist";
            assert valueMaps.get(dominator) != null : "value map of dominator must exist";

            // create new value map with increased nesting
            currentMap = new ValueMap(valueMaps.get(dominator));

            assert numPreds > 1 || dominator == block.predAt(0) || block.isExceptionEntry() : "dominator must be equal to predecessor";

            // visit all instructions of this block
            for (Instruction instr = block.next(); instr != null; instr = instr.next()) {
                assert !instr.hasSubst() : "substitution already set";

                // attempt value numbering
                Instruction f = currentMap.findInsert(instr);
                if (f != instr) {
                    C1XMetrics.GlobalValueNumberHits++;
                    assert !subst.hasSubst(f) : "can't have a substitution";
                    subst.setSubst(instr, f);
                }
            }

            // remember value map for successors
            valueMaps.put(block, currentMap);
        }
    }
}
