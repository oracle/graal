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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.AllocateInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.BinaryOperationInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.BranchInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.CallInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.CastInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.CompareInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.ConditionalBranchInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.ExtractElementInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.ExtractValueInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.GetElementPointerInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.IndirectBranchInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.InsertElementInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.InsertValueInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.LoadInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.PhiInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.ReturnInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.SelectInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.ShuffleVectorInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.StoreInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.SwitchInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.SwitchOldInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.UnreachableInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.VoidCallInstruction;
import com.oracle.truffle.llvm.parser.bc.impl.LLVMControlFlowAnalysis.LLVMControlFlow;

import com.oracle.truffle.llvm.parser.bc.impl.util.LLVMFrameIDs;
import com.oracle.truffle.llvm.parser.base.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.base.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.base.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.base.model.functions.FunctionParameter;
import com.oracle.truffle.llvm.parser.base.model.visitors.FunctionVisitor;
import com.oracle.truffle.llvm.parser.base.model.globals.GlobalAlias;
import com.oracle.truffle.llvm.parser.base.model.globals.GlobalConstant;
import com.oracle.truffle.llvm.parser.base.model.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.base.model.visitors.InstructionVisitor;
import com.oracle.truffle.llvm.parser.base.model.Model;
import com.oracle.truffle.llvm.parser.base.model.visitors.ModelVisitor;
import com.oracle.truffle.llvm.parser.base.model.types.MetaType;
import com.oracle.truffle.llvm.parser.base.model.types.Type;

public final class LLVMFrameDescriptors {

    public static LLVMFrameDescriptors generate(Model model) {
        LLVMControlFlowAnalysis cfg = LLVMControlFlowAnalysis.generate(model);

        LLVMFrameDescriptorsVisitor visitor = new LLVMFrameDescriptorsVisitor(cfg);

        model.accept(visitor);

        return new LLVMFrameDescriptors(visitor.getDescriptors(), visitor.getSlots());
    }

    private final Map<String, FrameDescriptor> descriptors;

    private final Map<String, Map<InstructionBlock, List<FrameSlot>>> slots;

    private LLVMFrameDescriptors(Map<String, FrameDescriptor> descriptors, Map<String, Map<InstructionBlock, List<FrameSlot>>> slots) {
        this.descriptors = descriptors;
        this.slots = slots;
    }

    public FrameDescriptor getDescriptor(String method) {
        return descriptors.get(method);
    }

    public FrameDescriptor getDescriptor() {
        return descriptors.values().iterator().next(); /* Any will do */
    }

    public Map<InstructionBlock, List<FrameSlot>> getSlots(String method) {
        return slots.get(method);
    }

    private static class LLVMFrameDescriptorsVisitor implements ModelVisitor {

        private final LLVMControlFlowAnalysis cfg;

        private final Map<String, FrameDescriptor> descriptors = new HashMap<>();

        private final Map<String, Map<InstructionBlock, List<FrameSlot>>> slots = new HashMap<>();

        LLVMFrameDescriptorsVisitor(LLVMControlFlowAnalysis cfg) {
            this.cfg = cfg;
        }

        public Map<String, FrameDescriptor> getDescriptors() {
            return descriptors;
        }

        public Map<String, Map<InstructionBlock, List<FrameSlot>>> getSlots() {
            return slots;
        }

        @Override
        public void visit(GlobalAlias alias) {
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
            if (method.getReturnType() != MetaType.VOID) {
                frame.addFrameSlot(LLVMFrameIDs.FUNCTION_RETURN_VALUE_FRAME_SLOT_ID);
            }
            frame.addFrameSlot(LLVMFrameIDs.STACK_ADDRESS_FRAME_SLOT_ID, FrameSlotKind.Object);

            for (FunctionParameter parameter : method.getParameters()) {
                frame.addFrameSlot(parameter.getName(), parameter.getType().getFrameSlotKind());
            }

            LLVMFrameDescriptorsFunctionVisitor visitor = new LLVMFrameDescriptorsFunctionVisitor(frame, cfg.dependencies(method.getName()));

            method.accept(visitor);
            visitor.finish();

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

        private final Map<InstructionBlock, List<FrameSlot>> map = new HashMap<>();

        private InstructionBlock entry = null;

        private final List<InstructionBlock> unvisited = new ArrayList<>();

        LLVMFrameDescriptorsFunctionVisitor(FrameDescriptor frame, LLVMControlFlow cfg) {
            this.frame = frame;
            this.cfg = cfg;
        }

        void finish() {
            // if a program terminates execution solely by invoking 'exit()' some blocks may
            // otherwise never get visited, this is just a dirty fix for now since we will need to
            // rewrite stack allocation for lifetime analysis later anyways
            for (final InstructionBlock block : unvisited) {
                block.accept(this);
            }
        }

        private List<InstructionBlock> getNondominatingBlocks(InstructionBlock block) {
            List<InstructionBlock> nondominating = new ArrayList<>();
            if (block != entry) {
                getNondominatingBlocksWorker(block, entry, nondominating);
            }
            return nondominating;
        }

        private void getNondominatingBlocksWorker(InstructionBlock dominator, InstructionBlock block, List<InstructionBlock> nondominating) {
            for (InstructionBlock blk : cfg.successor(block)) {
                if (!nondominating.contains(blk) && !dominator.equals(blk)) {
                    nondominating.add(blk);
                    getNondominatingBlocksWorker(dominator, blk, nondominating);
                }
            }
        }

        public Map<InstructionBlock, List<FrameSlot>> getSlotMap() {
            return map;
        }

        public List<FrameSlot> getSlots(InstructionBlock block) {
            int count = frame.getSize();

            unvisited.remove(block);
            block.accept(this);

            return new ArrayList<>(frame.getSlots().subList(count, frame.getSize()));
        }

        private boolean isDominating(InstructionBlock dominator, InstructionBlock block) {
            if (dominator.equals(block)) {
                return true;
            }
            return !getNondominatingBlocks(dominator).contains(block);
        }

        @Override
        public void visit(InstructionBlock block) {
            if (entry == null) {
                entry = block;
            }
            unvisited.add(block);
            List<InstructionBlock> processed = new ArrayList<>();
            List<FrameSlot> slots = new ArrayList<>();
            Deque<InstructionBlock> currentQueue = new ArrayDeque<>();
            Set<InstructionBlock> successors = cfg.successor(block);
            currentQueue.push(block);
            while (!currentQueue.isEmpty()) {
                InstructionBlock blk = currentQueue.pop();
                processed.add(blk);
                boolean dominates = false;
                for (InstructionBlock successor : successors) {
                    dominates |= isDominating(blk, successor);
                }
                if (!dominates) {
                    slots.addAll(getSlots(blk));
                    Set<InstructionBlock> predecessors = cfg.predecessor(blk);
                    for (InstructionBlock predecessor : predecessors) {
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
            findOrAddFrameSlot(allocate);
        }

        @Override
        public void visit(BinaryOperationInstruction operation) {
            findOrAddFrameSlot(operation);
        }

        @Override
        public void visit(BranchInstruction branch) {
        }

        @Override
        public void visit(CallInstruction call) {
            findOrAddFrameSlot(call);
        }

        @Override
        public void visit(CastInstruction cast) {
            findOrAddFrameSlot(cast);
        }

        @Override
        public void visit(CompareInstruction compare) {
            findOrAddFrameSlot(compare);
        }

        @Override
        public void visit(ConditionalBranchInstruction branch) {
        }

        @Override
        public void visit(ExtractElementInstruction extract) {
            findOrAddFrameSlot(extract);
        }

        @Override
        public void visit(ExtractValueInstruction extract) {
            findOrAddFrameSlot(extract);
        }

        @Override
        public void visit(GetElementPointerInstruction gep) {
            findOrAddFrameSlot(gep);
        }

        @Override
        public void visit(IndirectBranchInstruction ibi) {
        }

        @Override
        public void visit(InsertElementInstruction insert) {
            findOrAddFrameSlot(insert);
        }

        @Override
        public void visit(InsertValueInstruction insert) {
            findOrAddFrameSlot(insert);
        }

        @Override
        public void visit(LoadInstruction load) {
            findOrAddFrameSlot(load);
        }

        @Override
        public void visit(PhiInstruction phi) {
            findOrAddFrameSlot(phi);
        }

        @Override
        public void visit(ReturnInstruction ret) {
        }

        @Override
        public void visit(SelectInstruction select) {
            findOrAddFrameSlot(select);
        }

        @Override
        public void visit(ShuffleVectorInstruction shuffle) {
            findOrAddFrameSlot(shuffle);
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

        private void findOrAddFrameSlot(ValueInstruction val) {
            frame.findOrAddFrameSlot(val.getName(), val.getType().getFrameSlotKind());
        }
    }
}
