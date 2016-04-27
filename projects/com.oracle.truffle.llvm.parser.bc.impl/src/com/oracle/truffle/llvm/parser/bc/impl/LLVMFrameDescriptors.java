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

import java.util.*;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.llvm.parser.bc.impl.LLVMControlFlowAnalysis.LLVMControlFlow;

import uk.ac.man.cs.llvm.ir.model.*;
import uk.ac.man.cs.llvm.ir.model.elements.*;
import uk.ac.man.cs.llvm.ir.types.Type;

public class LLVMFrameDescriptors {

    public static LLVMFrameDescriptors generate(Model model) {
        LLVMControlFlowAnalysis cfg = LLVMControlFlowAnalysis.generate(model);

        LLVMFrameDescriptorsVisitor visitor = new LLVMFrameDescriptorsVisitor(cfg);

        model.accept(visitor);

        return new LLVMFrameDescriptors(visitor.getDescriptors(), visitor.getSlots());
    }

    private final Map<String, FrameDescriptor> descriptors;

    private final Map<String, Map<Block, List<FrameSlot>>> slots;

    private LLVMFrameDescriptors(Map<String, FrameDescriptor> descriptors, Map<String, Map<Block, List<FrameSlot>>> slots) {
        this.descriptors = descriptors;
        this.slots = slots;
    }

    public FrameDescriptor getDescriptor(String method) {
        return descriptors.get(method);
    }

    public FrameDescriptor getDescriptor() {
        return descriptors.values().iterator().next(); /* Any will do */
    }

    public Map<Block, List<FrameSlot>> getSlots(String method) {
        return slots.get(method);
    }

    private static class LLVMFrameDescriptorsVisitor implements ModelVisitor {

        private final LLVMControlFlowAnalysis cfg;

        private final Map<String, FrameDescriptor> descriptors = new HashMap<>();

        private final Map<String, Map<Block, List<FrameSlot>>> slots = new HashMap<>();

        public LLVMFrameDescriptorsVisitor(LLVMControlFlowAnalysis cfg) {
            this.cfg = cfg;
        }

        public Map<String, FrameDescriptor> getDescriptors() {
            return descriptors;
        }

        public Map<String, Map<Block, List<FrameSlot>>> getSlots() {
            return slots;
        }

        @Override
        public void visit(GlobalConstant constant) {
        }

        @Override
        public void visit(GlobalVariable variable) {
        }
        @Override
        public void visit(FunctionDeclaration method) {
        }

        @Override
        public void visit(FunctionDefinition method) {
            FrameDescriptor frame = new FrameDescriptor();
            frame.addFrameSlot(LLVMBitcodeHelper.FUNCTION_RETURN_VALUE_FRAME_SLOT_ID);
            frame.addFrameSlot(LLVMBitcodeHelper.STACK_ADDRESS_FRAME_SLOT_ID, FrameSlotKind.Object);

            for (FunctionParameter parameter : method.getParameters()) {
                frame.addFrameSlot(parameter.getName(), LLVMBitcodeHelper.toFrameSlotKind(parameter.getType()));
            }

            LLVMFrameDescriptorsFunctionVisitor visitor = new LLVMFrameDescriptorsFunctionVisitor(frame, cfg.dependencies(method.getName()));

            method.accept(visitor);

            descriptors.put(method.getName(), frame);
            slots.put(method.getName(), visitor.getSlotMap());
        }

        @Override
        public void visit(Type type) {
        }
    }

    private static class LLVMFrameDescriptorsFunctionVisitor implements FunctionVisitor, InstructionVisitor {

        private final FrameDescriptor frame;

        private LLVMControlFlow cfg;

        private final Map<Block, List<FrameSlot>> map = new HashMap<>();

        private Block entry = null;

        private LLVMFrameDescriptorsFunctionVisitor(FrameDescriptor frame, LLVMControlFlow cfg) {
            this.frame = frame;
            this.cfg = cfg;
        }

        private List<Block> getNondominatingBlocks(Block block) {
            List<Block> nondominating = new ArrayList<>();
            if (block != entry) {
                getNondominatingBlocksWorker(block, entry, nondominating);
            }
            return nondominating;
        }

        private void getNondominatingBlocksWorker(Block dominator, Block block, List<Block> nondominating) {
            for (Block blk : cfg.successor(block)) {
                if (!nondominating.contains(blk) && !dominator.equals(blk)) {
                    nondominating.add(blk);
                    getNondominatingBlocksWorker(dominator, blk, nondominating);
                }
            }
        }

        public Map<Block, List<FrameSlot>> getSlotMap() {
            return map;
        }

        public List<FrameSlot> getSlots(Block block) {
            int count = frame.getSize();

            block.accept(this);

            return new ArrayList<>(frame.getSlots().subList(count, frame.getSize()));
        }

        private boolean isDominating(Block dominator, Block block) {
            if (dominator.equals(block)) {
                return true;
            }
            return !getNondominatingBlocks(dominator).contains(block);
        }

        @Override
        public void visit(Block block) {
            if (entry == null) {
                entry = block;
            }
            List<Block> processed = new ArrayList<>();
            List<FrameSlot> slots = new ArrayList<>();
            Deque<Block> currentQueue = new ArrayDeque<>();
            Set<Block> successors = cfg.successor(block);
            currentQueue.push(block);
            while (!currentQueue.isEmpty()) {
                Block blk = currentQueue.pop();
                processed.add(blk);
                boolean dominates = false;
                for (Block successor : successors) {
                    dominates |= isDominating(blk, successor);
                }
                if (!dominates) {
                    slots.addAll(getSlots(blk));
                    Set<Block> predecessors = cfg.predecessor(blk);
                    for (Block predecessor : predecessors) {
                        if (!processed.contains(predecessor)) {
                            currentQueue.push(predecessor);
                        }
                    }
                }
            }
            map.put(block, slots);
        }

        @Override
        public void visit(AllocateInstruction allocate) {
            frame.findOrAddFrameSlot(allocate.getName(), LLVMBitcodeHelper.toFrameSlotKind(allocate.getType()));
        }

        @Override
        public void visit(BinaryOperationInstruction operation) {
            frame.findOrAddFrameSlot(operation.getName(), LLVMBitcodeHelper.toFrameSlotKind(operation.getType()));
        }

        @Override
        public void visit(BranchInstruction branch) {
        }

        @Override
        public void visit(CallInstruction call) {
            frame.findOrAddFrameSlot(call.getName(), LLVMBitcodeHelper.toFrameSlotKind(call.getType()));
        }

        @Override
        public void visit(CastInstruction cast) {
            frame.findOrAddFrameSlot(cast.getName(), LLVMBitcodeHelper.toFrameSlotKind(cast.getType()));
        }

        @Override
        public void visit(CompareInstruction compare) {
            frame.findOrAddFrameSlot(compare.getName(), LLVMBitcodeHelper.toFrameSlotKind(compare.getType()));
        }

        @Override
        public void visit(ConditionalBranchInstruction branch) {
        }

        @Override
        public void visit(ExtractElementInstruction extract) {
            frame.findOrAddFrameSlot(extract.getName(), LLVMBitcodeHelper.toFrameSlotKind(extract.getType()));
        }

        @Override
        public void visit(ExtractValueInstruction extract) {
            frame.findOrAddFrameSlot(extract.getName(), LLVMBitcodeHelper.toFrameSlotKind(extract.getType()));
        }

        @Override
        public void visit(GetElementPointerInstruction gep) {
            frame.findOrAddFrameSlot(gep.getName(), LLVMBitcodeHelper.toFrameSlotKind(gep.getType()));
        }

        @Override
        public void visit(IndirectBranchInstruction ibi) {
        }

        @Override
        public void visit(InsertElementInstruction insert) {
            frame.findOrAddFrameSlot(insert.getName(), LLVMBitcodeHelper.toFrameSlotKind(insert.getType()));
        }

        @Override
        public void visit(InsertValueInstruction insert) {
            frame.findOrAddFrameSlot(insert.getName(), LLVMBitcodeHelper.toFrameSlotKind(insert.getType()));
        }

        @Override
        public void visit(LoadInstruction load) {
            frame.findOrAddFrameSlot(load.getName(), LLVMBitcodeHelper.toFrameSlotKind(load.getType()));
        }

        @Override
        public void visit(PhiInstruction phi) {
            frame.findOrAddFrameSlot(phi.getName(), LLVMBitcodeHelper.toFrameSlotKind(phi.getType()));
        }

        @Override
        public void visit(ReturnInstruction ret) {
        }

        @Override
        public void visit(SelectInstruction select) {
            frame.findOrAddFrameSlot(select.getName(), LLVMBitcodeHelper.toFrameSlotKind(select.getType()));
        }

        @Override
        public void visit(ShuffleVectorInstruction shuffle) {
            frame.findOrAddFrameSlot(shuffle.getName(), LLVMBitcodeHelper.toFrameSlotKind(shuffle.getType()));
        }

        @Override
        public void visit(StoreInstruction store) {
        }

        @Override
        public void visit(SwitchInstruction branch) {
        }

        @Override
        public void visit(SwitchOldInstruction si) {
        }

        @Override
        public void visit(UnreachableInstruction unreachable) {
        }

        @Override
        public void visit(VoidCallInstruction call) {
        }
    }
}
