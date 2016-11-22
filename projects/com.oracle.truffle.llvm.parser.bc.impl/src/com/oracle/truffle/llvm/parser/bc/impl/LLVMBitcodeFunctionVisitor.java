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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.parser.base.facade.NodeFactoryFacade;
import com.oracle.truffle.llvm.parser.base.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.bc.impl.LLVMPhiManager.Phi;
import com.oracle.truffle.llvm.parser.bc.impl.nodes.LLVMSymbolResolver;

import com.oracle.truffle.llvm.parser.bc.impl.util.LLVMFrameIDs;
import com.oracle.truffle.llvm.parser.base.model.visitors.FunctionVisitor;
import com.oracle.truffle.llvm.parser.base.model.blocks.InstructionBlock;

class LLVMBitcodeFunctionVisitor implements FunctionVisitor {

    private final LLVMBitcodeVisitor module;

    private final FrameDescriptor frame;

    private final List<LLVMNode> blocks = new ArrayList<>();

    private final Map<String, Integer> labels;

    private final Map<InstructionBlock, List<Phi>> phis;

    private final List<LLVMNode> instructions = new ArrayList<>();

    private final LLVMSymbolResolver symbolResolver;

    private final NodeFactoryFacade factoryFacade;

    private final int argCount;

    private final FunctionDefinition function;

    LLVMBitcodeFunctionVisitor(LLVMBitcodeVisitor module, FrameDescriptor frame, Map<String, Integer> labels,
                    Map<InstructionBlock, List<Phi>> phis, NodeFactoryFacade factoryFacade, int argCount, LLVMSymbolResolver symbolResolver, FunctionDefinition functionDefinition) {
        this.module = module;
        this.frame = frame;
        this.labels = labels;
        this.phis = phis;
        this.symbolResolver = symbolResolver;
        this.factoryFacade = factoryFacade;
        this.argCount = argCount;
        this.function = functionDefinition;
    }

    void addInstruction(LLVMNode node) {
        instructions.add(node);
    }

    void addTerminatingInstruction(LLVMNode node, int blockId, String blockName) {
        blocks.add(factoryFacade.createBasicBlockNode(getBlock(), node, blockId, blockName));
        instructions.add(node);
    }

    int getArgCount() {
        return argCount;
    }

    public LLVMNode[] getBlock() {
        return instructions.toArray(new LLVMNode[instructions.size()]);
    }

    public List<LLVMNode> getBlocks() {
        return Collections.unmodifiableList(blocks);
    }

    public FunctionDefinition getFunction() {
        return function;
    }

    public LLVMBitcodeVisitor getModule() {
        return module;
    }

    public FrameDescriptor getFrame() {
        return frame;
    }

    FrameSlot getReturnSlot() {
        return getSlot(LLVMFrameIDs.FUNCTION_RETURN_VALUE_FRAME_SLOT_ID);
    }

    FrameSlot getSlot(String name) {
        return frame.findFrameSlot(name);
    }

    FrameSlot getStackSlot() {
        return getSlot(LLVMFrameIDs.STACK_ADDRESS_FRAME_SLOT_ID);
    }

    LLVMSymbolResolver getSymbolResolver() {
        return symbolResolver;
    }

    public Map<String, Integer> labels() {
        return labels;
    }

    Map<InstructionBlock, List<Phi>> getPhiManager() {
        return phis;
    }

    @Override
    public void visit(InstructionBlock block) {
        this.instructions.clear();
        block.accept(new LLVMBitcodeInstructionVisitor(this, block, factoryFacade));
    }
}
