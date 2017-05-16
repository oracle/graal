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
package com.oracle.truffle.llvm.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.llvm.parser.LLVMPhiManager.Phi;
import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.visitors.FunctionVisitor;
import com.oracle.truffle.llvm.parser.nodes.LLVMSymbolResolver;
import com.oracle.truffle.llvm.parser.util.LLVMFrameIDs;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;

final class LLVMBitcodeFunctionVisitor implements FunctionVisitor {

    private final LLVMParserRuntime module;

    private final FrameDescriptor frame;

    private final List<LLVMExpressionNode> blocks = new ArrayList<>();

    private final Map<String, Integer> labels;

    private final Map<InstructionBlock, List<Phi>> phis;

    private final List<LLVMNode> instructions = new ArrayList<>();

    private final LLVMSymbolResolver symbolResolver;

    private final SulongNodeFactory nodeFactory;

    private final int argCount;

    private final FunctionDefinition function;

    LLVMBitcodeFunctionVisitor(LLVMParserRuntime module, FrameDescriptor frame, Map<String, Integer> labels,
                    Map<InstructionBlock, List<Phi>> phis, SulongNodeFactory nodeFactory, int argCount, LLVMSymbolResolver symbolResolver, FunctionDefinition functionDefinition) {
        this.module = module;
        this.frame = frame;
        this.labels = labels;
        this.phis = phis;
        this.symbolResolver = symbolResolver;
        this.nodeFactory = nodeFactory;
        this.argCount = argCount;
        this.function = functionDefinition;
    }

    void addInstruction(LLVMNode node) {
        instructions.add(node);
    }

    void addTerminatingInstruction(LLVMControlFlowNode node, int blockId, String blockName) {
        blocks.add(nodeFactory.createBasicBlockNode(module, getBlock(), node, blockId, blockName));
        instructions.add(node);
    }

    int getArgCount() {
        return argCount;
    }

    public LLVMExpressionNode[] getBlock() {
        return instructions.toArray(new LLVMExpressionNode[instructions.size()]);
    }

    public List<LLVMExpressionNode> getBlocks() {
        return Collections.unmodifiableList(blocks);
    }

    public FunctionDefinition getFunction() {
        return function;
    }

    public LLVMParserRuntime getRuntime() {
        return module;
    }

    public FrameDescriptor getFrame() {
        return frame;
    }

    FrameSlot getReturnSlot() {
        return getSlot(LLVMFrameIDs.FUNCTION_RETURN_VALUE_FRAME_SLOT_ID);
    }

    FrameSlot getExceptionSlot() {
        return getSlot(LLVMFrameIDs.FUNCTION_EXCEPTION_VALUE_FRAME_SLOT_ID);
    }

    FrameSlot getSlot(String name) {
        return frame.findFrameSlot(name);
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
        block.accept(new LLVMBitcodeInstructionVisitor(this, block, nodeFactory));
    }

}
