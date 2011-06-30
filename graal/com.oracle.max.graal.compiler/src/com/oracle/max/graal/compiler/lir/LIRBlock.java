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
package com.oracle.max.graal.compiler.lir;

import java.util.*;

import com.oracle.max.asm.*;
import com.oracle.max.graal.compiler.alloc.*;
import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.compiler.value.*;
import com.oracle.max.graal.graph.*;

/**
 * The {@code LIRBlock} class definition.
 */
public final class LIRBlock {

    public final Label label;
    private LIRList lir;
    private final int blockID;
    private FrameState lastState;
    private List<Node> instructions = new ArrayList<Node>(4);
    private List<LIRBlock> predecessors = new ArrayList<LIRBlock>(4);
    private List<LIRBlock> successors = new ArrayList<LIRBlock>(4);
    private LIRDebugInfo debugInfo;

    /**
     * Bit map specifying which {@linkplain OperandPool operands} are live upon entry to this block.
     * These are values used in this block or any of its successors where such value are not defined
     * in this block.
     * The bit index of an operand is its {@linkplain OperandPool#operandNumber(com.sun.cri.ci.CiValue) operand number}.
     */
    public BitMap liveIn;

    /**
     * Bit map specifying which {@linkplain OperandPool operands} are live upon exit from this block.
     * These are values used in a successor block that are either defined in this block or were live
     * upon entry to this block.
     * The bit index of an operand is its {@linkplain OperandPool#operandNumber(com.sun.cri.ci.CiValue) operand number}.
     */
    public BitMap liveOut;

    /**
     * Bit map specifying which {@linkplain OperandPool operands} are used (before being defined) in this block.
     * That is, these are the values that are live upon entry to the block.
     * The bit index of an operand is its {@linkplain OperandPool#operandNumber(com.sun.cri.ci.CiValue) operand number}.
     */
    public BitMap liveGen;

    /**
     * Bit map specifying which {@linkplain OperandPool operands} are defined/overwritten in this block.
     * The bit index of an operand is its {@linkplain OperandPool#operandNumber(com.sun.cri.ci.CiValue) operand number}.
     */
    public BitMap liveKill;

    private int firstLirInstructionID;
    private int lastLirInstructionID;
    public int blockEntryPco;

    public LIRBlock(Label label, LIRDebugInfo debugInfo) {
        this.label = label;
        blockID = -1;
        this.debugInfo = debugInfo;
    }

    public LIRDebugInfo debugInfo() {
        return this.debugInfo;
    }

    public LIRBlock(int blockID) {
        this.blockID = blockID;
        label = new Label();
        loopIndex = -1;
        linearScanNumber = blockID;
        instructions = new ArrayList<Node>(4);
        predecessors = new ArrayList<LIRBlock>(4);
        successors = new ArrayList<LIRBlock>(4);
    }

    public List<Node> getInstructions() {
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

    public void setInstructions(List<Node> list) {
        instructions = list;
    }

    public void setLastState(FrameState fs) {
        lastState = fs;
    }

    public FrameState lastState() {
        return lastState;
    }

    private Node first;
    private Node last;

    public Node firstInstruction() {
        return first;
    }


    public Node lastInstruction() {
        return last;
    }

    public void setFirstInstruction(Node n) {
        first = n;
    }


    public void setLastInstruction(Node n) {
        last = n;
    }

    public boolean endsWithJump() {
        List<LIRInstruction> instructionsList = lir.instructionsList();
        if (instructionsList.size() == 0) {
            return false;
        }
        LIROpcode code = instructionsList.get(instructionsList.size() - 1).code;
        return code == LIROpcode.Branch || code == LIROpcode.TableSwitch;
    }
}
