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
package com.oracle.truffle.llvm.parser.bc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.llvm.parser.api.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.api.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.api.model.globals.GlobalValueSymbol;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.AllocateInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.BinaryOperationInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.BranchInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.CallInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.CastInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.CompareInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.ConditionalBranchInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.ExtractElementInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.ExtractValueInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.GetElementPointerInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.IndirectBranchInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.InsertElementInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.InsertValueInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.Instruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.LoadInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.PhiInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.ReturnInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.SelectInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.ShuffleVectorInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.StoreInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.SwitchInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.SwitchOldInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.TerminatingInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.UnreachableInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.api.model.symbols.instructions.VoidCallInstruction;
import com.oracle.truffle.llvm.parser.api.model.visitors.AbstractTerminatingInstructionVisitor;
import com.oracle.truffle.llvm.parser.api.model.visitors.InstructionVisitor;
import com.oracle.truffle.llvm.parser.api.model.visitors.ValueSymbolVisitor;
import com.oracle.truffle.llvm.parser.api.util.LLVMParserAsserts;
import com.oracle.truffle.llvm.runtime.options.LLVMOptions;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;
import com.oracle.truffle.llvm.runtime.types.symbols.ValueSymbol;

public final class LLVMLifetimeAnalysis {

    private final Map<InstructionBlock, FrameSlot[]> nullableBefore;

    private final Map<InstructionBlock, FrameSlot[]> nullableAfter;

    public LLVMLifetimeAnalysis(Map<InstructionBlock, FrameSlot[]> nullableBefore, Map<InstructionBlock, FrameSlot[]> nullableAfter) {
        this.nullableBefore = nullableBefore;
        this.nullableAfter = nullableAfter;
    }

    public Map<InstructionBlock, FrameSlot[]> getNullableBefore() {
        return nullableBefore;
    }

    public Map<InstructionBlock, FrameSlot[]> getNullableAfter() {
        return nullableAfter;
    }

    public static LLVMLifetimeAnalysis getResult(FunctionDefinition functionDefinition, FrameDescriptor frameDescriptor, Map<InstructionBlock, List<LLVMPhiManager.Phi>> phiRefs) {
        LLVMParserAsserts.assertNoNullElement(frameDescriptor.getSlots());
        final LLVMLifetimeAnalysisVisitor visitor = new LLVMLifetimeAnalysisVisitor(frameDescriptor, functionDefinition, phiRefs);
        final LLVMLifetimeAnalysis lifetimes = visitor.visit();
        if (LLVMOptions.DEBUG.printLifetimeAnalysisStatistics()) {
            printResult(functionDefinition, lifetimes);
        }
        return lifetimes;
    }

    private static final String FUNCTION_FORMAT = "%s:\n";
    private static final String AFTER_BLOCK_FORMAT = "\t dead after bb %4s:";

    private static void printResult(FunctionDefinition functionDefinition, LLVMLifetimeAnalysis lifetimes) {
        final Map<InstructionBlock, FrameSlot[]> mapping = lifetimes.getNullableAfter();
        System.out.print(String.format(FUNCTION_FORMAT, functionDefinition.getName()));
        for (InstructionBlock b : mapping.keySet()) {
            System.out.print(String.format(AFTER_BLOCK_FORMAT, b.getName()));
            FrameSlot[] variables = mapping.get(b);
            if (variables.length != 0) {
                System.out.print("\t");
                for (int i = 0; i < variables.length; i++) {
                    if (i != 0) {
                        System.out.print(", ");
                    }
                    System.out.print(variables[i].getIdentifier());
                }
            }
            System.out.println();
        }
    }

    private static final class LLVMReadVisitor {

        static List<FrameSlot> getReads(Instruction instruction, FrameDescriptor frame, boolean alsoCountPhiUsage) {
            final List<FrameSlot> reads = new ArrayList<>();
            instruction.accept(new InstructionVisitor() {

                private void resolve(Symbol symbol) {
                    if (symbol.hasName() && !(symbol instanceof GlobalValueSymbol || symbol instanceof FunctionType)) {
                        final FrameSlot frameSlot = frame.findFrameSlot(((ValueSymbol) symbol).getName());
                        if (frameSlot == null) {
                            throw new AssertionError("No Frameslot for ValueSymbol: " + symbol);
                        } else {
                            reads.add(frameSlot);
                        }
                    }
                }

                @Override
                public void visit(AllocateInstruction allocate) {
                    resolve(allocate.getCount());
                }

                @Override
                public void visit(BinaryOperationInstruction operation) {
                    resolve(operation.getLHS());
                    resolve(operation.getRHS());
                }

                @Override
                public void visit(BranchInstruction branch) {
                }

                @Override
                public void visit(CallInstruction call) {
                    for (int i = 0; i < call.getArgumentCount(); i++) {
                        resolve(call.getArgument(i));
                    }
                    resolve(call.getCallTarget());
                }

                @Override
                public void visit(CastInstruction cast) {
                    resolve(cast.getValue());
                }

                @Override
                public void visit(CompareInstruction operation) {
                    resolve(operation.getLHS());
                    resolve(operation.getRHS());
                }

                @Override
                public void visit(ConditionalBranchInstruction branch) {
                    resolve(branch.getCondition());
                }

                @Override
                public void visit(ExtractElementInstruction extract) {
                    resolve(extract.getIndex());
                    resolve(extract.getVector());
                }

                @Override
                public void visit(ExtractValueInstruction extract) {
                    resolve(extract.getAggregate());
                }

                @Override
                public void visit(GetElementPointerInstruction gep) {
                    resolve(gep.getBasePointer());
                    gep.getIndices().forEach(this::resolve);
                }

                @Override
                public void visit(IndirectBranchInstruction branch) {
                    resolve(branch.getAddress());
                }

                @Override
                public void visit(InsertElementInstruction insert) {
                    resolve(insert.getVector());
                    resolve(insert.getIndex());
                    resolve(insert.getValue());
                }

                @Override
                public void visit(InsertValueInstruction insert) {
                    resolve(insert.getAggregate());
                    resolve(insert.getValue());
                }

                @Override
                public void visit(LoadInstruction load) {
                    resolve(load.getSource());
                }

                @Override
                public void visit(PhiInstruction phi) {
                    if (alsoCountPhiUsage) {
                        for (int i = 0; i < phi.getSize(); i++) {
                            resolve(phi.getValue(i));
                        }
                    }
                }

                @Override
                public void visit(ReturnInstruction ret) {
                    if (ret.getValue() != null) {
                        resolve(ret.getValue());
                    }
                }

                @Override
                public void visit(SelectInstruction select) {
                    resolve(select.getCondition());
                    resolve(select.getTrueValue());
                    resolve(select.getFalseValue());
                }

                @Override
                public void visit(ShuffleVectorInstruction shuffle) {
                    resolve(shuffle.getMask());
                    resolve(shuffle.getVector1());
                    resolve(shuffle.getVector2());
                }

                @Override
                public void visit(StoreInstruction store) {
                    resolve(store.getDestination());
                    resolve(store.getSource());
                }

                @Override
                public void visit(SwitchInstruction select) {
                    // everything but the condition must be an integer constant anyways, they do not
                    // lie on the stack
                    resolve(select.getCondition());
                }

                @Override
                public void visit(SwitchOldInstruction select) {
                    resolve(select.getCondition());
                }

                @Override
                public void visit(UnreachableInstruction unreachable) {
                }

                @Override
                public void visit(VoidCallInstruction call) {
                    for (int i = 0; i < call.getArgumentCount(); i++) {
                        resolve(call.getArgument(i));
                    }
                    resolve(call.getCallTarget());
                }
            });
            LLVMParserAsserts.assertNoNullElement(reads);
            return reads;
        }

    }

    private static final class LLVMLifetimeAnalysisVisitor {

        private final List<InstructionBlock> basicBlocks;

        private final FrameDescriptor frame;

        private final FunctionDefinition functionDefinition;

        private final Map<InstructionBlock, List<LLVMPhiManager.Phi>> phiRefs;

        private final Map<Instruction, List<FrameSlot>> instructionReads = new HashMap<>();

        private final Map<Instruction, List<InstructionBlock>> successorBlocks = new HashMap<>();

        private final Map<Instruction, Set<FrameSlot>> bbEndKills = new HashMap<>();

        private final Map<InstructionBlock, Set<FrameSlot>> bbBeginKills = new HashMap<>();

        /**
         * The variable definitions per instruction (the last instruction can have several.
         */
        private final Map<Instruction, Set<FrameSlot>> defs = new HashMap<>();

        /**
         * The (transitive) inputs of each instruction.
         */
        private final Map<Instruction, Set<FrameSlot>> in = new HashMap<>();

        /**
         * The (transitive) outputs of each instruction.
         */
        private final Map<Instruction, Set<FrameSlot>> out = new HashMap<>();

        LLVMLifetimeAnalysisVisitor(FrameDescriptor frame, FunctionDefinition functionDefinition, Map<InstructionBlock, List<LLVMPhiManager.Phi>> phiRefs) {
            this.frame = frame;
            this.functionDefinition = functionDefinition;
            this.phiRefs = phiRefs;
            this.basicBlocks = new ArrayList<>(functionDefinition.getBlocks());
        }

        private FrameSlot getFrameSlot(String name) {
            final FrameSlot frameSlot = frame.findFrameSlot(name);
            if (frameSlot == null) {
                throw new AssertionError("No FrameSlot with name: " + name);
            } else {
                return frameSlot;
            }
        }

        private void initializeSuccessors() {
            final InstructionVisitor initSuccessorsVisitor = new AbstractTerminatingInstructionVisitor() {
                @Override
                public void visitTerminatingInstruction(TerminatingInstruction instruction) {
                    successorBlocks.put(instruction, instruction.getSuccessors());
                }
            };
            functionDefinition.accept(block -> block.accept(initSuccessorsVisitor));
        }

        private void initializeInstructionReads() {
            functionDefinition.accept(block -> {
                for (int i = 0; i < block.getInstructionCount(); i++) {
                    final Instruction inst = block.getInstruction(i);
                    final List<FrameSlot> currentInstructionReads = LLVMReadVisitor.getReads(inst, frame, false);
                    LLVMParserAsserts.assertNoNullElement(currentInstructionReads);
                    instructionReads.put(inst, currentInstructionReads);
                }
            });
        }

        private void initializeInstructionInOuts() {
            final InstructionVisitor initEndKillsVisitor = new ValueSymbolVisitor() {
                @Override
                public void visitValueInstruction(ValueInstruction valueInstruction) {
                    bbEndKills.put(valueInstruction, new HashSet<>());
                }
            };
            functionDefinition.accept(bb -> {
                bbBeginKills.put(bb, new HashSet<>());
                final List<LLVMPhiManager.Phi> bbPhis = phiRefs.getOrDefault(bb, Collections.emptyList());
                bb.accept(initEndKillsVisitor);

                for (int i = 0; i < bb.getInstructionCount(); i++) {
                    final Instruction inst = bb.getInstruction(i);

                    // in[n] = use[n]
                    // variables inside phi instructions do not have usages since they are actually
                    // written before
                    final Set<FrameSlot> uses = new HashSet<>(instructionReads.getOrDefault(inst, Collections.emptyList()));

                    // so we have to add the usage of the phi instructions where they are written
                    // (at the last instruction of a block)
                    if (i == bb.getInstructionCount() - 1) {
                        for (final LLVMPhiManager.Phi phi : bbPhis) {
                            final Symbol val = phi.getValue();
                            if (val.hasName() && !(val instanceof GlobalValueSymbol || val instanceof FunctionType)) {
                                uses.add(getFrameSlot(((ValueSymbol) val).getName()));
                            }
                        }
                    }

                    in.put(inst, uses);
                    out.put(inst, new HashSet<>());
                }
            });
        }

        private void initializeVariableDefinitions() {
            final InstructionVisitor initVarDefVisitor = new ValueSymbolVisitor() {
                @Override
                public void visitValueInstruction(ValueInstruction valueInstruction) {
                    final Set<FrameSlot> instructionDefs = new HashSet<>(1);
                    instructionDefs.add(getFrameSlot(valueInstruction.getName()));
                    defs.put(valueInstruction, instructionDefs);
                }
            };
            functionDefinition.accept(block -> block.accept(initVarDefVisitor));
        }

        private void findFixPoint() {
            boolean changed;
            final List<InstructionBlock> reversedBlocks = new ArrayList<>(basicBlocks);
            Collections.reverse(reversedBlocks);
            do {
                changed = false;
                for (final InstructionBlock block : reversedBlocks) {
                    for (int i = 0; i < block.getInstructionCount(); i++) {
                        final Instruction inst = block.getInstruction(i);

                        // update out
                        if (inst.isTerminating()) {
                            // non sequential successor
                            // out[n] = in[n+1, n+2, ...]
                            assert i == block.getInstructionCount() - 1;
                            final List<InstructionBlock> nextBlocks = successorBlocks.getOrDefault(inst, Collections.emptyList());
                            for (InstructionBlock nextBlock : nextBlocks) {
                                final Instruction nextInst = getFirstNonPhiInstruction(nextBlock);
                                final Set<FrameSlot> nextIn = in.getOrDefault(nextInst, new HashSet<>(0));
                                final Set<FrameSlot> addTo = out.getOrDefault(inst, new HashSet<>(0));
                                changed |= addFrameSlots(addTo, nextIn);
                            }

                        } else {
                            // out[n] = in[n + 1]
                            assert i + 1 < block.getInstructionCount();
                            final Instruction nextInst = block.getInstruction(i + 1);
                            final Set<FrameSlot> nextIn = in.getOrDefault(nextInst, new HashSet<>(0));
                            final Set<FrameSlot> addTo = out.getOrDefault(inst, new HashSet<>(0));
                            changed |= addFrameSlots(addTo, nextIn);

                        }
                        // update in
                        final Set<FrameSlot> outWithoutDefs = new HashSet<>(out.getOrDefault(inst, new HashSet<>(0)));
                        final List<FrameSlot> realDefs = new ArrayList<>(defs.getOrDefault(inst, new HashSet<>(0)));
                        outWithoutDefs.removeAll(realDefs);
                        changed |= addFrameSlots(in.getOrDefault(inst, new HashSet<>(0)), outWithoutDefs);
                    }
                }
            } while (changed);
        }

        private static Instruction getFirstNonPhiInstruction(InstructionBlock block) {
            // Phi-Instruction only appear at the start of basic blocks, but there can be
            // arbitrarily many
            for (int i = 0; i < block.getInstructionCount(); i++) {
                final Instruction inst = block.getInstruction(i);
                if (!(inst instanceof PhiInstruction)) {
                    return inst;
                }
            }
            throw new AssertionError("Block without ending Instruction!");
        }

        private void getInstructionKills(Map<Instruction, Set<FrameSlot>> kills) {
            for (final InstructionBlock bb : basicBlocks) {
                for (int i = 0; i < bb.getInstructionCount(); i++) {
                    final Instruction inst = bb.getInstruction(i);
                    final Set<FrameSlot> inSlots = in.getOrDefault(inst, new HashSet<>(0));
                    final Set<FrameSlot> outSlots = out.getOrDefault(inst, new HashSet<>(0));
                    final Set<FrameSlot> instructionKills = new HashSet<>(inSlots);
                    instructionKills.removeAll(outSlots);
                    if (inst.isTerminating()) {
                        for (final InstructionBlock bas : successorBlocks.getOrDefault(inst, Collections.emptyList())) {
                            final Instruction firstInst = getFirstNonPhiInstruction(bas);
                            Set<FrameSlot> deadAtBegin = new HashSet<>(out.getOrDefault(inst, new HashSet<>(0)));
                            deadAtBegin.removeAll(in.getOrDefault(firstInst, new HashSet<>(0)));
                            bbBeginKills.put(bas, deadAtBegin);
                        }
                        if (inst instanceof ReturnInstruction || inst instanceof UnreachableInstruction) {
                            kills.put(inst, new HashSet<>(frame.getSlots()));
                        }
                    }
                    kills.put(inst, instructionKills);
                }
            }
        }

        private Map<InstructionBlock, FrameSlot[]> convertInstructionKillsToBasicBlockKills() {
            final Map<InstructionBlock, FrameSlot[]> convertedMap = new HashMap<>();
            for (final InstructionBlock bb : basicBlocks) {
                final List<FrameSlot> blockKills = new ArrayList<>();
                for (int i = 0; i < bb.getInstructionCount(); i++) {
                    blockKills.addAll(bbEndKills.getOrDefault(bb.getInstruction(i), new HashSet<>(0)));
                }
                final FrameSlot[] blockKillArr = blockKills.toArray(new FrameSlot[blockKills.size()]);
                convertedMap.put(bb, blockKillArr);
            }
            return convertedMap;
        }

        private LLVMLifetimeAnalysis visit() {
            initializeSuccessors();
            initializeInstructionReads();
            initializeInstructionInOuts();
            initializeVariableDefinitions();
            findFixPoint();
            getInstructionKills(bbEndKills);
            final Map<InstructionBlock, FrameSlot[]> endKills = convertInstructionKillsToBasicBlockKills();
            final Map<InstructionBlock, FrameSlot[]> beginKills = new HashMap<>();
            for (InstructionBlock block : basicBlocks) {
                final Set<FrameSlot> bbBegin = bbBeginKills.getOrDefault(block, new HashSet<>(0));
                beginKills.put(block, bbBegin.toArray(new FrameSlot[bbBegin.size()]));
            }
            return new LLVMLifetimeAnalysis(beginKills, endKills);
        }

        private static boolean addFrameSlots(Set<FrameSlot> addTo, Set<FrameSlot> add) {
            return addTo.addAll(add);
        }

    }
}
