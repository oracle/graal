/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.lir;

import java.util.*;

import com.oracle.max.asm.*;
import com.sun.c1x.alloc.*;
import com.sun.c1x.debug.*;
import com.sun.c1x.ir.*;
import com.sun.c1x.util.*;
import com.sun.c1x.value.*;
import com.sun.cri.ci.*;

/**
 * The {@code LIRBlock} class definition.
 */
public final class LIRBlock {

    public final Label label = new Label();
    private LIRList lir;
    private final int blockID;
    private FrameState lastState;
    private List<Instruction> instructions = new ArrayList<Instruction>(4);
    private List<LIRBlock> predecessors = new ArrayList<LIRBlock>(4);
    private List<LIRBlock> successors = new ArrayList<LIRBlock>(4);
    private List<LIRBlock> exceptionHandlerSuccessors = new ArrayList<LIRBlock>(4);

    /**
     * Bit map specifying which {@linkplain OperandPool operands} are live upon entry to this block.
     * These are values used in this block or any of its successors where such value are not defined
     * in this block.
     * The bit index of an operand is its {@linkplain OperandPool#operandNumber(com.sun.cri.ci.CiValue) operand number}.
     */
    public CiBitMap liveIn;

    /**
     * Bit map specifying which {@linkplain OperandPool operands} are live upon exit from this block.
     * These are values used in a successor block that are either defined in this block or were live
     * upon entry to this block.
     * The bit index of an operand is its {@linkplain OperandPool#operandNumber(com.sun.cri.ci.CiValue) operand number}.
     */
    public CiBitMap liveOut;

    /**
     * Bit map specifying which {@linkplain OperandPool operands} are used (before being defined) in this block.
     * That is, these are the values that are live upon entry to the block.
     * The bit index of an operand is its {@linkplain OperandPool#operandNumber(com.sun.cri.ci.CiValue) operand number}.
     */
    public CiBitMap liveGen;

    /**
     * Bit map specifying which {@linkplain OperandPool operands} are defined/overwritten in this block.
     * The bit index of an operand is its {@linkplain OperandPool#operandNumber(com.sun.cri.ci.CiValue) operand number}.
     */
    public CiBitMap liveKill;

    private int firstLirInstructionID;
    private int lastLirInstructionID;
    public int blockEntryPco;

    public List<LIRBlock> getExceptionHandlerSuccessors() {
        return exceptionHandlerSuccessors;
    }

    public LIRBlock(int blockID) {
        this.blockID = blockID;
        loopIndex = -1;
        linearScanNumber = blockID;
    }

    public List<Instruction> getInstructions() {
        return instructions;
    }

    public int firstLirInstructionId() {
        return firstLirInstructionID;
    }

    public void setFirstLirInstructionId(int firstLirInstructionId) {
        this.firstLirInstructionID = firstLirInstructionId;
    }

    public int lastLirInstructionId() {
        return lastLirInstructionID;
    }

    public void setLastLirInstructionId(int lastLirInstructionId) {
        this.lastLirInstructionID = lastLirInstructionId;
    }

    public int loopDepth;

    public LIRList lir() {
        return lir;
    }

    public void setLir(LIRList lir) {
        this.lir = lir;
    }

    public void setBlockEntryPco(int codePos) {
        this.blockEntryPco = codePos;
    }

    public void printWithoutPhis(LogStream out) {
        out.println("LIR Block " + blockID());
    }

    public int blockID() {
        return blockID;
    }

    public int numberOfPreds() {
        return predecessors.size();
    }

    public int numberOfSux() {
        return successors.size();
    }

    public boolean isPredecessor(LIRBlock block) {
        return predecessors.contains(block);
    }

    public LIRBlock predAt(int i) {
        return predecessors.get(i);
    }

    public LIRBlock suxAt(int i) {
        return successors.get(i);
    }

    public List<LIRBlock> blockSuccessors() {
        return successors;
    }

    @Override
    public String toString() {
        return "B" + blockID();
    }

    public List<LIRBlock> blockPredecessors() {
        return predecessors;
    }

    public int loopDepth() {
        // TODO(tw): Set correct loop depth.
        return 0;
    }

    public int loopIndex() {
        return loopIndex;
    }

    public void setLoopIndex(int v) {
        loopIndex = v;
    }

    public void setLoopDepth(int v) {
        this.loopDepth = v;
    }

    private int loopIndex;

    public Label label() {
        return label;
    }

    private int linearScanNumber = -1;
    private boolean linearScanLoopEnd;
    private boolean linearScanLoopHeader;
    private boolean exceptionEntry;
    private boolean backwardBranchTarget;


    public void setExceptionEntry(boolean b) {
        this.exceptionEntry = b;
    }

    public boolean isExceptionEntry() {
        return exceptionEntry;
    }

    public void setBackwardBranchTarget(boolean b) {
        this.backwardBranchTarget = b;
    }

    public boolean backwardBranchTarget() {
        return backwardBranchTarget;
    }

    public void setLinearScanNumber(int v) {
        linearScanNumber = v;
    }

    public int linearScanNumber() {
        return linearScanNumber;
    }

    public void setLinearScanLoopEnd() {
        linearScanLoopEnd = true;
    }

    public boolean isLinearScanLoopEnd() {
        return linearScanLoopEnd;
    }

    public void setLinearScanLoopHeader() {
        this.linearScanLoopHeader = true;
    }

    public boolean isLinearScanLoopHeader() {
        return linearScanLoopHeader;
    }

    public void replaceWith(LIRBlock other) {
        for (LIRBlock pred : predecessors) {
            Util.replaceAllInList(this, other, pred.successors);
        }
        for (int i = 0; i < other.predecessors.size(); ++i) {
            if (other.predecessors.get(i) == this) {
                other.predecessors.remove(i);
                other.predecessors.addAll(i, this.predecessors);
            }
        }
        successors.clear();
        predecessors.clear();
    }

    public void setInstructions(List<Instruction> list) {
        instructions = list;
    }

    public void substituteSuccessor(LIRBlock target, LIRBlock newSucc) {
        for (int i = 0; i < successors.size(); ++i) {
            if (successors.get(i) == target) {
                successors.set(i, newSucc);
                break;
            }
        }
    }

    public void substitutePredecessor(LIRBlock source, LIRBlock newSucc) {
        for (int i = 0; i < predecessors.size(); ++i) {
            if (predecessors.get(i) == source) {
                predecessors.set(i, newSucc);
                break;
            }
        }
    }

    public void setLastState(FrameState fs) {
        lastState = fs;
    }

    public FrameState lastState() {
        return lastState;
    }
}
