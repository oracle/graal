/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.ssi;

import java.util.*;

import jdk.internal.jvmci.meta.*;

import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.BlockEndOp;
import com.oracle.graal.lir.LIRInstruction.*;
import com.oracle.graal.lir.StandardOp.*;
import com.oracle.graal.lir.ssa.SSAUtil.*;

public final class SSIUtils {

    public static BlockEndOp outgoing(LIR lir, AbstractBlockBase<?> block) {
        return (BlockEndOp) outgoingInst(lir, block);
    }

    public static LIRInstruction outgoingInst(LIR lir, AbstractBlockBase<?> block) {
        List<LIRInstruction> instructions = lir.getLIRforBlock(block);
        int index = instructions.size() - 1;
        LIRInstruction op = instructions.get(index);
        return op;
    }

    public static LabelOp incoming(LIR lir, AbstractBlockBase<?> block) {
        return (LabelOp) incomingInst(lir, block);
    }

    private static LIRInstruction incomingInst(LIR lir, AbstractBlockBase<?> block) {
        return lir.getLIRforBlock(block).get(0);
    }

    public static void removeIncoming(LIR lir, AbstractBlockBase<?> block) {
        incoming(lir, block).clearIncomingValues();
    }

    public static void removeOutgoing(LIR lir, AbstractBlockBase<?> block) {
        outgoing(lir, block).clearOutgoingValues();
    }

    /**
     * Visits each SIGMA/PHI value pair of an edge, i.e. the outgoing value from the predecessor and
     * the incoming value to the merge block.
     */
    public static void forEachValuePair(LIR lir, AbstractBlockBase<?> toBlock, AbstractBlockBase<?> fromBlock, PhiValueVisitor visitor) {
        assert toBlock.getPredecessors().contains(fromBlock) : String.format("%s not in predecessor list: %s", fromBlock, toBlock.getPredecessors());
        assert fromBlock.getSuccessorCount() == 1 || toBlock.getPredecessorCount() == 1 : String.format("Critical Edge? %s has %d successors and %s has %d predecessors", fromBlock,
                        fromBlock.getSuccessors(), toBlock, toBlock.getPredecessorCount());
        assert fromBlock.getSuccessors().contains(toBlock) : String.format("Predecessor block %s has wrong successor: %s, should contain: %s", fromBlock, fromBlock.getSuccessors(), toBlock);

        BlockEndOp blockEnd = outgoing(lir, fromBlock);
        LabelOp label = incoming(lir, toBlock);

        assert label.getIncomingSize() == blockEnd.getOutgoingSize() : String.format("In/Out size mismatch: in=%d vs. out=%d, blocks %s vs. %s", label.getIncomingSize(), blockEnd.getOutgoingSize(),
                        toBlock, fromBlock);

        for (int i = 0; i < label.getIncomingSize(); i++) {
            visitor.visit(label.getIncomingValue(i), blockEnd.getOutgoingValue(i));
        }
    }

    public static void forEachRegisterHint(LIR lir, AbstractBlockBase<?> block, LabelOp label, Value targetValue, OperandMode mode, ValueConsumer valueConsumer) {
        assert mode == OperandMode.DEF : "Wrong operand mode: " + mode;
        assert lir.getLIRforBlock(block).get(0).equals(label) : String.format("Block %s and Label %s do not match!", block, label);

        if (!label.isPhiIn()) {
            return;
        }
        int idx = indexOfValue(label, targetValue);
        assert idx >= 0 : String.format("Value %s not in label %s", targetValue, label);

        for (AbstractBlockBase<?> pred : block.getPredecessors()) {
            BlockEndOp blockEnd = outgoing(lir, pred);
            Value sourceValue = blockEnd.getOutgoingValue(idx);
            valueConsumer.visitValue((LIRInstruction) blockEnd, sourceValue, null, null);
        }

    }

    private static int indexOfValue(LabelOp label, Value value) {
        for (int i = 0; i < label.getIncomingSize(); i++) {
            if (label.getIncomingValue(i).equals(value)) {
                return i;
            }
        }
        return -1;
    }

}
