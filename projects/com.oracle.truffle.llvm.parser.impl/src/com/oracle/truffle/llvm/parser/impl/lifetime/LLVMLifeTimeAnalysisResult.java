/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.impl.lifetime;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.intel.llvm.ireditor.lLVM_IR.BasicBlock;
import com.oracle.truffle.api.frame.FrameSlot;

public abstract class LLVMLifeTimeAnalysisResult {

    /**
     * Gets the local variables which are dead at the beginning of a basic block.
     *
     * @return the basic blocks which are dead at the beginning of a basic block
     */
    public abstract Map<BasicBlock, FrameSlot[]> getBeginDead();

    /**
     * Gets the local variables which are dead at the end of a basic block.
     *
     * @return the basic blocks which are dead at the end of a basic block
     */
    public abstract Map<BasicBlock, FrameSlot[]> getEndDead();

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof LLVMLifeTimeAnalysisResult)) {
            return false;
        }
        LLVMLifeTimeAnalysisResult other = (LLVMLifeTimeAnalysisResult) obj;
        return equals(getBeginDead(), other.getBeginDead()) && equals(getEndDead(), other.getEndDead());
    }

    private static boolean equals(Map<BasicBlock, FrameSlot[]> map, Map<BasicBlock, FrameSlot[]> otherMap) {
        if (map.size() != otherMap.size()) {
            return false;
        }
        if (!getBasicBlockNames(map).equals(getBasicBlockNames(otherMap))) {
            return false;
        }
        for (BasicBlock b : map.keySet()) {
            FrameSlot[] frameSlots = map.get(b);
            FrameSlot[] otherFrameSlots = otherMap.get(findBasicBlock(b.getName(), otherMap));
            boolean equals = asStrings(frameSlots).equals(asStrings(otherFrameSlots));
            if (!equals) {
                return false;
            }
        }
        return true;
    }

    private static Set<String> asStrings(FrameSlot[] frameSlots) {
        Set<String> frameSlotNames = new HashSet<>();
        for (FrameSlot slot : frameSlots) {
            frameSlotNames.add(slot.getIdentifier().toString());
        }
        return frameSlotNames;
    }

    private static BasicBlock findBasicBlock(String name, Map<BasicBlock, FrameSlot[]> map) {
        for (BasicBlock b : map.keySet()) {
            if (b.getName().equals(name)) {
                return b;
            }
        }
        throw new AssertionError(name);
    }

    private static Set<String> getBasicBlockNames(Map<BasicBlock, FrameSlot[]> blockSlotMap) {
        Set<String> basicBlockNames = new HashSet<>();
        for (BasicBlock b : blockSlotMap.keySet()) {
            basicBlockNames.add(b.getName());
        }
        return basicBlockNames;
    }

    @Override
    public int hashCode() {
        return getBeginDead().hashCode() + 11 * getEndDead().hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("begin dead [");
        sb.append(frameSlotToString(getBeginDead()));
        sb.append("], end dead [");
        sb.append(frameSlotToString(getEndDead()));
        sb.append("]");
        return sb.toString();
    }

    private static String frameSlotToString(Map<BasicBlock, FrameSlot[]> beginDead) {
        StringBuilder sb = new StringBuilder();
        for (BasicBlock bb : beginDead.keySet()) {
            sb.append(bb.getName() + " (");
            FrameSlot[] slots = beginDead.get(bb);
            for (int i = 0; i < slots.length; i++) {
                if (i != 0) {
                    sb.append(", ");
                }
                sb.append(slots[i].getIdentifier());
            }
            sb.append(")");
        }
        return sb.toString();
    }

}
