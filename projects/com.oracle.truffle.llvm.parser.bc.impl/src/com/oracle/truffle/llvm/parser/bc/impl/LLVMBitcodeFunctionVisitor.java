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
package com.oracle.truffle.llvm.parser.bc.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.nodes.base.LLVMStackFrameNuller;
import com.oracle.truffle.llvm.nodes.base.LLVMStackFrameNuller.LLVMAddressNuller;
import com.oracle.truffle.llvm.nodes.base.LLVMStackFrameNuller.LLVMBooleanNuller;
import com.oracle.truffle.llvm.nodes.base.LLVMStackFrameNuller.LLVMDoubleNuller;
import com.oracle.truffle.llvm.nodes.base.LLVMStackFrameNuller.LLVMFloatNuller;
import com.oracle.truffle.llvm.nodes.base.LLVMStackFrameNuller.LLVMIntNuller;
import com.oracle.truffle.llvm.nodes.base.LLVMStackFrameNuller.LLVMLongNuller;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMBasicBlockNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMContext;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMTerminatorNode;
import com.oracle.truffle.llvm.parser.bc.impl.LLVMPhiManager.Phi;
import com.oracle.truffle.llvm.parser.bc.impl.nodes.LLVMNodeGenerator;

import com.oracle.truffle.llvm.parser.bc.impl.util.LLVMFrameIDs;
import com.oracle.truffle.llvm.parser.base.model.visitors.FunctionVisitor;
import com.oracle.truffle.llvm.parser.base.model.globals.GlobalValueSymbol;
import com.oracle.truffle.llvm.parser.base.model.blocks.InstructionBlock;

public class LLVMBitcodeFunctionVisitor implements FunctionVisitor {

    private final LLVMBitcodeVisitor module;

    private final FrameDescriptor frame;

    private final Map<InstructionBlock, List<FrameSlot>> slotsToNull;

    private final List<LLVMStackFrameNuller[]> nullers = new ArrayList<>();

    private final List<LLVMBasicBlockNode> blocks = new ArrayList<>();

    private final Map<String, Integer> labels;

    private final Map<InstructionBlock, List<Phi>> phis;

    private final List<LLVMNode> instructions = new ArrayList<>();

    private final LLVMNodeGenerator symbolResolver;

    private final int argCount;

    public LLVMBitcodeFunctionVisitor(LLVMBitcodeVisitor module, FrameDescriptor frame, Map<InstructionBlock, List<FrameSlot>> slotsToNull, Map<String, Integer> labels,
                    Map<InstructionBlock, List<Phi>> phis, int argCount) {
        this.module = module;
        this.frame = frame;
        this.slotsToNull = slotsToNull;
        this.labels = labels;
        this.phis = phis;
        this.symbolResolver = new LLVMNodeGenerator(this);
        this.argCount = argCount;
    }

    public void addInstruction(LLVMNode node) {
        instructions.add(node);
    }

    public void addTerminatingInstruction(LLVMTerminatorNode node, int blockId, String blockName) {
        blocks.add(new LLVMBasicBlockNode(getBlock(), node, blockId, blockName));
        instructions.add(node);
    }

    public int getArgCount() {
        return argCount;
    }

    public LLVMNode[] getBlock() {
        return instructions.toArray(new LLVMNode[instructions.size()]);
    }

    public LLVMBasicBlockNode[] getBlocks() {
        return blocks.toArray(new LLVMBasicBlockNode[blocks.size()]);
    }

    public int getBlockCount() {
        return blocks.size();
    }

    public LLVMContext getContext() {
        return module.getContext();
    }

    public LLVMBitcodeVisitor getModule() {
        return module;
    }

    public FrameDescriptor getFrame() {
        return frame;
    }

    public FrameSlot getReturnSlot() {
        return getSlot(LLVMFrameIDs.FUNCTION_RETURN_VALUE_FRAME_SLOT_ID);
    }

    public FrameSlot getSlot(String name) {
        return frame.findFrameSlot(name);
    }

    public FrameSlot getStackSlot() {
        return getSlot(LLVMFrameIDs.STACK_ADDRESS_FRAME_SLOT_ID);
    }

    public LLVMNodeGenerator getSymbolResolver() {
        return symbolResolver;
    }

    public LLVMExpressionNode global(GlobalValueSymbol symbol) {
        return module.getGlobalVariable(symbol);
    }

    public Map<String, Integer> labels() {
        return labels;
    }

    public LLVMStackFrameNuller[][] getNullers() {
        return nullers.toArray(new LLVMStackFrameNuller[0][]);
    }

    public Map<InstructionBlock, List<Phi>> getPhiManager() {
        return phis;
    }

    @Override
    public void visit(InstructionBlock block) {
        this.instructions.clear();

        block.accept(new LLVMBitcodeInstructionVisitor(this, block));
        nullers.add(createNullers(slotsToNull.get(block)));
    }

    private static LLVMStackFrameNuller[] createNullers(List<FrameSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return new LLVMStackFrameNuller[0];
        }
        LLVMStackFrameNuller[] nodes = new LLVMStackFrameNuller[slots.size()];
        int i = 0;
        for (FrameSlot slot : slots) {
            switch (slot.getKind()) {
                case Boolean:
                    nodes[i++] = new LLVMBooleanNuller(slot);
                    break;
                case Byte:
                    nodes[i++] = new LLVMStackFrameNuller.LLVMByteNuller(slot);
                    break;
                case Int:
                    nodes[i++] = new LLVMIntNuller(slot);
                    break;
                case Long:
                    nodes[i++] = new LLVMLongNuller(slot);
                    break;
                case Float:
                    nodes[i++] = new LLVMFloatNuller(slot);
                    break;
                case Double:
                    nodes[i++] = new LLVMDoubleNuller(slot);
                    break;
                case Object:
                    nodes[i++] = new LLVMAddressNuller(slot);
                    break;
                case Illegal:
                    throw new AssertionError("illegal");
                default:
                    throw new AssertionError();
            }
        }
        return nodes;
    }
}
