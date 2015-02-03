/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.stackslotalloc;

import static com.oracle.graal.api.code.CodeUtil.*;

import java.util.*;

import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.lir.*;

public class InstructionNumberer {

    private final LIRInstruction[] opIdToInstructionMap;

    protected InstructionNumberer(LIR lir) {
        // Assign IDs to LIR nodes and build a mapping, lirOps, from ID to LIRInstruction node.
        int numInstructions = 0;
        for (AbstractBlock<?> block : lir.getControlFlowGraph().getBlocks()) {
            numInstructions += lir.getLIRforBlock(block).size();
        }

        // initialize with correct length
        opIdToInstructionMap = new LIRInstruction[numInstructions];
    }

    /**
     * Converts an {@linkplain LIRInstruction#id instruction id} to an instruction index. All LIR
     * instructions in a method have an index one greater than their linear-scan order predecessor
     * with the first instruction having an index of 0.
     */
    private static int opIdToIndex(int opId) {
        return opId >> 1;
    }

    /**
     * Retrieves the {@link LIRInstruction} based on its {@linkplain LIRInstruction#id id}.
     *
     * @param opId an instruction {@linkplain LIRInstruction#id id}
     * @return the instruction whose {@linkplain LIRInstruction#id} {@code == id}
     */
    protected LIRInstruction instructionForId(int opId) {
        assert isEven(opId) : "opId not even";
        LIRInstruction instr = opIdToInstructionMap[opIdToIndex(opId)];
        assert instr.id() == opId;
        return instr;
    }

    /**
     * Numbers all instructions in all blocks.
     */
    protected void numberInstructions(LIR lir, List<? extends AbstractBlock<?>> sortedBlocks) {

        int opId = 0;
        int index = 0;
        for (AbstractBlock<?> block : sortedBlocks) {

            List<LIRInstruction> instructions = lir.getLIRforBlock(block);

            int numInst = instructions.size();
            for (int j = 0; j < numInst; j++) {
                LIRInstruction op = instructions.get(j);
                op.setId(opId);

                opIdToInstructionMap[index] = op;
                assert instructionForId(opId) == op : "must match";

                index++;
                opId += 2; // numbering of lirOps by two
            }
        }
        assert index == opIdToInstructionMap.length : "must match";
        assert (index << 1) == opId : "must match: " + (index << 1);
        assert opId - 2 == maxOpId() : "must match";
    }

    /**
     * Gets the highest instruction id allocated by this object.
     */
    public int maxOpId() {
        assert opIdToInstructionMap.length > 0 : "no operations";
        return (opIdToInstructionMap.length - 1) << 1;
    }
}
