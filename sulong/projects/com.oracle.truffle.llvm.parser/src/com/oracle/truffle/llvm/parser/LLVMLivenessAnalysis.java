/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.functions.FunctionParameter;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.AllocateInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.BinaryOperationInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.BranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.CallInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.CastInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.CompareExchangeInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.CompareInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ConditionalBranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.DbgDeclareInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.DbgValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ExtractElementInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ExtractValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.FenceInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.GetElementPointerInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.IndirectBranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.InsertElementInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.InsertValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.Instruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.InvokeInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.LandingpadInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.LoadInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.PhiInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ReadModifyWriteInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ResumeInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ReturnInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.SelectInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ShuffleVectorInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.StoreInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.SwitchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.SwitchOldInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.TerminatingInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.UnaryOperationInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.UnreachableInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidCallInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidInvokeInstruction;
import com.oracle.truffle.llvm.parser.model.visitors.SymbolVisitor;
import com.oracle.truffle.llvm.runtime.types.symbols.LLVMIdentifier;
import com.oracle.truffle.llvm.runtime.types.symbols.SSAValue;

public final class LLVMLivenessAnalysis {

    private final FunctionDefinition functionDefinition;

    private final SSAValue[] frameSlots;

    private LLVMLivenessAnalysis(FunctionDefinition functionDefinition) {
        this.functionDefinition = functionDefinition;

        // collect all stack values
        ArrayList<SSAValue> slots = new ArrayList<>();
        for (FunctionParameter param : functionDefinition.getParameters()) {
            param.setFrameIdentifier(slots.size());
            slots.add(param);
        }
        for (InstructionBlock block : functionDefinition.getBlocks()) {
            for (Instruction instruction : block.getInstructions()) {
                if (instruction instanceof ValueInstruction) {
                    ValueInstruction value = (ValueInstruction) instruction;
                    value.setFrameIdentifier(slots.size());
                    slots.add(value);
                }
            }
        }
        this.frameSlots = slots.toArray(new SSAValue[slots.size()]);
    }

    public static LLVMLivenessAnalysisResult computeLiveness(Map<InstructionBlock, List<LLVMPhiManager.Phi>> phis, FunctionDefinition functionDefinition, PrintStream logLivenessStream) {
        LLVMLivenessAnalysis analysis = new LLVMLivenessAnalysis(functionDefinition);

        List<InstructionBlock> blocks = functionDefinition.getBlocks();
        BlockInfo[] blockInfos = analysis.initializeGenKill(phis, blocks);
        ArrayList<InstructionBlock>[] predecessors = computePredecessors(blocks);
        int processedBlocks = iterateToFixedPoint(blocks, analysis.frameSlots.length, blockInfos, predecessors);
        if (logLivenessStream != null) {
            analysis.printIntermediateResult(logLivenessStream, blocks, blockInfos, processedBlocks);
        }

        LLVMLivenessAnalysisResult result = analysis.computeLivenessAnalysisResult(blocks, blockInfos, predecessors);
        if (logLivenessStream != null) {
            analysis.printResult(logLivenessStream, blocks, result);
        }
        return result;
    }

    private BlockInfo[] initializeGenKill(Map<InstructionBlock, List<LLVMPhiManager.Phi>> phis, List<InstructionBlock> blocks) {
        BlockInfo[] result = new BlockInfo[blocks.size()];
        for (int i = 0; i < blocks.size(); i++) {
            InstructionBlock block = blocks.get(i);
            BlockInfo blockInfo = result[i] = new BlockInfo(frameSlots.length);
            if (i == 0) {
                // in the first block, the arguments are also always alive
                for (FunctionParameter param : functionDefinition.getParameters()) {
                    processRead(blockInfo, param.getFrameIdentifier());
                }
            }

            LLVMLivenessReadVisitor readVisitor = new LLVMLivenessReadVisitor(blockInfo);
            for (int j = 0; j < block.getInstructionCount(); j++) {
                Instruction instruction = block.getInstruction(j);
                if (instruction instanceof PhiInstruction) {
                    processPhiWrite((PhiInstruction) instruction, blockInfo);
                } else {
                    instruction.accept(readVisitor);
                    int frameSlotIndex = resolve(instruction);
                    if (frameSlotIndex >= 0) {
                        blockInfo.defs.set(frameSlotIndex);
                        if (!blockInfo.gen.get(frameSlotIndex)) {
                            blockInfo.kill.set(frameSlotIndex);
                        }
                    }
                }
            }

            List<LLVMPhiManager.Phi> bbPhis = phis.getOrDefault(block, Collections.emptyList());
            for (LLVMPhiManager.Phi phi : bbPhis) {
                processValueUsedInPhi(phi.getValue(), blockInfo);
            }
        }
        return result;
    }

    private static int iterateToFixedPoint(List<InstructionBlock> blocks, int frameSize, BlockInfo[] blockInfos, ArrayList<InstructionBlock>[] predecessors) {
        // by processing the graph bottom-up, we may reach a fixed point more quickly
        ArrayDeque<InstructionBlock> workList = new ArrayDeque<>(blocks);
        BitSet blockOnWorkList = new BitSet(blocks.size());
        blockOnWorkList.set(0, blockOnWorkList.size());

        BitSet newPredecessorOut = new BitSet(frameSize);
        BitSet newIn = new BitSet(frameSize);

        int processedBlocks = 0;
        while (!workList.isEmpty()) {
            processedBlocks++;
            InstructionBlock block = removeBlockFromWorkList(workList, blockOnWorkList);
            BlockInfo blockInfo = blockInfos[block.getBlockIndex()];

            newIn.clear();
            newIn.or(blockInfo.out);
            newIn.andNot(blockInfo.defs);
            newIn.or(blockInfo.gen);
            newIn.or(blockInfo.phiDefs);

            blockInfo.in.clear();
            blockInfo.in.or(newIn);

            for (InstructionBlock predecessor : predecessors[block.getBlockIndex()]) {
                BlockInfo predecessorBlockInfo = blockInfos[predecessor.getBlockIndex()];
                newPredecessorOut.clear();
                newPredecessorOut.or(blockInfo.in);
                newPredecessorOut.andNot(blockInfo.phiDefs);
                newPredecessorOut.or(predecessorBlockInfo.phiUses);

                boolean changed = or(predecessorBlockInfo.out, newPredecessorOut);
                if (changed) {
                    addBlockToWorkList(workList, blockOnWorkList, predecessor);
                }
            }
        }
        return processedBlocks;
    }

    @SuppressWarnings("unchecked")
    private LLVMLivenessAnalysisResult computeLivenessAnalysisResult(List<InstructionBlock> blocks, BlockInfo[] blockInfos, ArrayList<InstructionBlock>[] predecessors) {
        ArrayList<NullerInformation>[] nullableWithinBlock = new ArrayList[blocks.size()];
        BitSet[] nullableBeforeBlock = new BitSet[blocks.size()];
        BitSet[] nullableAfterBlock = new BitSet[blocks.size()];

        int[] lastInstructionIndexTouchingLocal = new int[frameSlots.length];
        LLVMNullerReadVisitor nullerReadVisitor = new LLVMNullerReadVisitor(lastInstructionIndexTouchingLocal);
        for (int i = 0; i < blocks.size(); i++) {
            ArrayList<NullerInformation> blockNullers = new ArrayList<>();
            Arrays.fill(lastInstructionIndexTouchingLocal, -1);
            BlockInfo blockInfo = blockInfos[i];
            // we destroy the kill and phiDefs bitsets as they are no longer needed anyways
            blockInfo.kill.clear();
            blockInfo.phiDefs.clear();

            if (i == 0) {
                // as an approximation, we claim that the arguments are used by the first
                // instruction
                for (FunctionParameter param : functionDefinition.getParameters()) {
                    int frameSlotIndex = param.getFrameIdentifier();
                    lastInstructionIndexTouchingLocal[frameSlotIndex] = 0;
                }
            }

            InstructionBlock block = blocks.get(i);
            for (int j = 0; j < block.getInstructionCount(); j++) {
                Instruction instruction = block.getInstruction(j);

                if (instruction instanceof PhiInstruction) {
                    // we need to skip the reads of phi nodes as they belong to a different block
                } else {
                    nullerReadVisitor.setInstructionIndex(j);
                    instruction.accept(nullerReadVisitor);
                }

                int frameSlotIndex = resolve(instruction);
                if (frameSlotIndex >= 0) {
                    // whenever we have a write that kills a value, we need a value nuller after the
                    // last usage (except when the last usage happened in the same instruction as
                    // the write)
                    if (lastInstructionIndexTouchingLocal[frameSlotIndex] != -1 && lastInstructionIndexTouchingLocal[frameSlotIndex] != j) {
                        blockNullers.add(new NullerInformation(frameSlots[frameSlotIndex], lastInstructionIndexTouchingLocal[frameSlotIndex]));
                    }
                    lastInstructionIndexTouchingLocal[frameSlotIndex] = j;
                }
            }

            // compute the values that die in this block. we do that in place, i.e., we destroy the
            // def bitset that is no longer needed anyways.
            blockInfo.defs.or(blockInfo.in);
            blockInfo.defs.andNot(blockInfo.out);

            int terminatingInstructionIndex = block.getInstructionCount() - 1;
            BitSet valuesThatDieInBlock = blockInfo.defs;
            int bitIndex = -1;
            while ((bitIndex = valuesThatDieInBlock.nextSetBit(bitIndex + 1)) >= 0) {
                assert lastInstructionIndexTouchingLocal[bitIndex] >= 0 : "must have a last usage, otherwise the value would not be alive in this block";
                if (blockInfo.phiUses.get(bitIndex) || lastInstructionIndexTouchingLocal[bitIndex] == terminatingInstructionIndex) {
                    // if a value dies that is used in a phi function or in a terminating
                    // instruction, it dies after the block
                    blockInfo.phiDefs.set(bitIndex);
                } else {
                    blockNullers.add(new NullerInformation(frameSlots[bitIndex], lastInstructionIndexTouchingLocal[bitIndex]));
                }
            }

            // compute the values that can be nulled out before we enter this block.
            for (InstructionBlock predecessor : predecessors[i]) {
                BlockInfo predInfo = blockInfos[predecessor.getBlockIndex()];
                blockInfo.kill.or(predInfo.out);
            }
            blockInfo.kill.andNot(blockInfo.in);

            // collect the results
            Collections.sort(blockNullers);
            nullableWithinBlock[i] = blockNullers;
            nullableBeforeBlock[i] = blockInfo.kill;
            nullableAfterBlock[i] = blockInfo.phiDefs;
        }
        return new LLVMLivenessAnalysisResult(frameSlots, nullableWithinBlock, nullableBeforeBlock, nullableAfterBlock);
    }

    public static final class NullerInformation implements Comparable<NullerInformation> {

        private final SSAValue identifier;
        private final int instructionIndex;

        public NullerInformation(SSAValue identifier, int instructionIndex) {
            this.identifier = identifier;
            this.instructionIndex = instructionIndex;
        }

        public SSAValue getIdentifier() {
            return identifier;
        }

        public int getInstructionIndex() {
            return instructionIndex;
        }

        @Override
        public int compareTo(NullerInformation o) {
            return o.instructionIndex - this.instructionIndex;
        }
    }

    @SuppressWarnings("unchecked")
    private static ArrayList<InstructionBlock>[] computePredecessors(List<InstructionBlock> blocks) {
        ArrayList<InstructionBlock>[] result = new ArrayList[blocks.size()];
        for (int i = 0; i < blocks.size(); i++) {
            result[i] = new ArrayList<>(2);
        }

        for (InstructionBlock block : blocks) {
            TerminatingInstruction terminatingInstruction = block.getTerminatingInstruction();
            for (int i = 0; i < terminatingInstruction.getSuccessorCount(); i++) {
                result[terminatingInstruction.getSuccessor(i).getBlockIndex()].add(block);
            }
        }
        return result;
    }

    private static void addBlockToWorkList(ArrayDeque<InstructionBlock> workList, BitSet blockOnWorkList, InstructionBlock predecessorBlock) {
        boolean predecessorBlockIndex = blockOnWorkList.get(predecessorBlock.getBlockIndex());
        if (!predecessorBlockIndex) {
            workList.addLast(predecessorBlock);
            blockOnWorkList.set(predecessorBlock.getBlockIndex());
        }
    }

    private static InstructionBlock removeBlockFromWorkList(ArrayDeque<InstructionBlock> workList, BitSet blockOnWorkList) {
        InstructionBlock block = workList.removeLast();
        blockOnWorkList.clear(block.getBlockIndex());
        return block;
    }

    private static boolean or(BitSet dest, BitSet source) {
        assert dest.size() == source.size();
        if (isChangeForOrNecessary(dest, source)) {
            dest.or(source);
            return true;
        }
        return false;
    }

    private static boolean isChangeForOrNecessary(BitSet dest, BitSet source) {
        // this could be far faster if we had direct access to the words in the BitSet
        int bitIndex = -1;
        while ((bitIndex = source.nextSetBit(bitIndex + 1)) >= 0) {
            if (!dest.get(bitIndex)) {
                return true;
            }
        }
        return false;
    }

    private static void processRead(SymbolImpl symbol, BlockInfo blockInfo) {
        int frameSlotIndex = resolve(symbol);
        processRead(blockInfo, frameSlotIndex);
    }

    private static void processRead(BlockInfo blockInfo, int frameSlotIndex) {
        if (frameSlotIndex >= 0) {
            if (!blockInfo.kill.get(frameSlotIndex)) {
                blockInfo.gen.set(frameSlotIndex);
            }
        }
    }

    private static void processValueUsedInPhi(SymbolImpl symbol, BlockInfo blockInfo) {
        int frameSlotIndex = resolve(symbol);
        if (frameSlotIndex >= 0) {
            blockInfo.phiUses.set(frameSlotIndex);
        }
    }

    private static void processPhiWrite(PhiInstruction phi, BlockInfo blockInfo) {
        int frameSlotIndex = resolve(phi);
        if (frameSlotIndex >= 0) {
            blockInfo.phiDefs.set(frameSlotIndex);
            blockInfo.defs.set(frameSlotIndex);
        }
    }

    private static int resolve(SymbolImpl symbol) {
        if (symbol instanceof SSAValue) {
            int identifier = ((SSAValue) symbol).getFrameIdentifier();
            assert identifier >= 0;
            return identifier;
        }
        return -1;
    }

    private final class LLVMLivenessReadVisitor extends LLVMLocalReadVisitor {
        private final BlockInfo blockInfo;

        LLVMLivenessReadVisitor(BlockInfo blockInfo) {
            this.blockInfo = blockInfo;
        }

        @Override
        public void visitLocalRead(SymbolImpl symbol) {
            processRead(symbol, blockInfo);
        }
    }

    private final class LLVMNullerReadVisitor extends LLVMLocalReadVisitor {
        private final int[] lastInstructionIndexTouchingLocal;
        private int instructionIndex;

        LLVMNullerReadVisitor(int[] lastInstructionIndexTouchingLocal) {
            this.lastInstructionIndexTouchingLocal = lastInstructionIndexTouchingLocal;
        }

        public void setInstructionIndex(int instructionIndex) {
            this.instructionIndex = instructionIndex;
        }

        @Override
        public void visitLocalRead(SymbolImpl symbol) {
            int frameSlotIndex = resolve(symbol);
            if (frameSlotIndex >= 0) {
                lastInstructionIndexTouchingLocal[frameSlotIndex] = instructionIndex;
            }
        }
    }

    private abstract static class LLVMLocalReadVisitor implements SymbolVisitor {

        @Override
        public void visit(AllocateInstruction allocate) {
            visitLocalRead(allocate.getCount());
        }

        @Override
        public void visit(BinaryOperationInstruction operation) {
            visitLocalRead(operation.getLHS());
            visitLocalRead(operation.getRHS());
        }

        @Override
        public void visit(UnaryOperationInstruction operation) {
            visitLocalRead(operation.getOperand());
        }

        @Override
        public void visit(BranchInstruction branch) {
        }

        @Override
        public void visit(InvokeInstruction call) {
            for (SymbolImpl arg : call.getArguments()) {
                visitLocalRead(arg);
            }
            visitLocalRead(call.getCallTarget());
        }

        @Override
        public void visit(CallInstruction call) {
            for (SymbolImpl arg : call.getArguments()) {
                visitLocalRead(arg);
            }
            visitLocalRead(call.getCallTarget());
        }

        @Override
        public void visit(CastInstruction cast) {
            visitLocalRead(cast.getValue());
        }

        @Override
        public void visit(LandingpadInstruction landingpadInstruction) {
            if (landingpadInstruction.getValue() != null) {
                visitLocalRead(landingpadInstruction.getValue());
            }
        }

        @Override
        public void visit(CompareInstruction operation) {
            visitLocalRead(operation.getLHS());
            visitLocalRead(operation.getRHS());
        }

        @Override
        public void visit(ConditionalBranchInstruction branch) {
            visitLocalRead(branch.getCondition());
        }

        @Override
        public void visit(ExtractElementInstruction extract) {
            visitLocalRead(extract.getIndex());
            visitLocalRead(extract.getVector());
        }

        @Override
        public void visit(ExtractValueInstruction extract) {
            visitLocalRead(extract.getAggregate());
        }

        @Override
        public void visit(GetElementPointerInstruction gep) {
            visitLocalRead(gep.getBasePointer());
            for (SymbolImpl symbol : gep.getIndices()) {
                visitLocalRead(symbol);
            }
        }

        @Override
        public void visit(IndirectBranchInstruction branch) {
            visitLocalRead(branch.getAddress());
        }

        @Override
        public void visit(InsertElementInstruction insert) {
            visitLocalRead(insert.getVector());
            visitLocalRead(insert.getIndex());
            visitLocalRead(insert.getValue());
        }

        @Override
        public void visit(InsertValueInstruction insert) {
            visitLocalRead(insert.getAggregate());
            visitLocalRead(insert.getValue());
        }

        @Override
        public void visit(LoadInstruction load) {
            visitLocalRead(load.getSource());
        }

        @Override
        public void visit(PhiInstruction phi) {
            assert false : "skipped as phis must be handled in a special way";
        }

        @Override
        public void visit(ReturnInstruction ret) {
            if (ret.getValue() != null) {
                visitLocalRead(ret.getValue());
            }
        }

        @Override
        public void visit(ResumeInstruction resume) {
            if (resume.getValue() != null) {
                visitLocalRead(resume.getValue());
            }
        }

        @Override
        public void visit(CompareExchangeInstruction cmpxchg) {
            visitLocalRead(cmpxchg.getPtr());
            visitLocalRead(cmpxchg.getCmp());
            visitLocalRead(cmpxchg.getReplace());
        }

        @Override
        public void visit(SelectInstruction select) {
            visitLocalRead(select.getCondition());
            visitLocalRead(select.getTrueValue());
            visitLocalRead(select.getFalseValue());
        }

        @Override
        public void visit(ShuffleVectorInstruction shuffle) {
            visitLocalRead(shuffle.getMask());
            visitLocalRead(shuffle.getVector1());
            visitLocalRead(shuffle.getVector2());
        }

        @Override
        public void visit(StoreInstruction store) {
            visitLocalRead(store.getDestination());
            visitLocalRead(store.getSource());
        }

        @Override
        public void visit(SwitchInstruction select) {
            // everything but the condition must be an integer constant anyways, they do not
            // lie on the stack
            visitLocalRead(select.getCondition());
        }

        @Override
        public void visit(SwitchOldInstruction select) {
            visitLocalRead(select.getCondition());
        }

        @Override
        public void visit(UnreachableInstruction unreachable) {
        }

        @Override
        public void visit(VoidCallInstruction call) {
            for (SymbolImpl arg : call.getArguments()) {
                visitLocalRead(arg);
            }
            visitLocalRead(call.getCallTarget());
        }

        @Override
        public void visit(VoidInvokeInstruction call) {
            for (SymbolImpl arg : call.getArguments()) {
                visitLocalRead(arg);
            }
            visitLocalRead(call.getCallTarget());
        }

        @Override
        public void visit(ReadModifyWriteInstruction rmw) {
            visitLocalRead(rmw.getPtr());
            visitLocalRead(rmw.getValue());
        }

        @Override
        public void visit(FenceInstruction fence) {
        }

        @Override
        public void visit(DbgDeclareInstruction inst) {
            visitLocalRead(inst.getValue());
        }

        @Override
        public void visit(DbgValueInstruction inst) {
            visitLocalRead(inst.getValue());
        }

        protected abstract void visitLocalRead(SymbolImpl symbol);
    }

    private static final class BlockInfo {
        public final BitSet in;
        public final BitSet out;

        public final BitSet gen;
        public final BitSet kill;
        public final BitSet defs;
        public final BitSet phiDefs;
        public final BitSet phiUses;

        BlockInfo(int frameSlots) {
            this.in = new BitSet(frameSlots);
            this.out = new BitSet(frameSlots);

            this.gen = new BitSet(frameSlots);
            this.kill = new BitSet(frameSlots);
            this.defs = new BitSet(frameSlots);
            this.phiDefs = new BitSet(frameSlots);
            this.phiUses = new BitSet(frameSlots);
        }
    }

    /**
     * Holds the information when a certain value can be invalidated. The nullableWithinBlock
     * information is sorted descending by the instructionIndex (i.e., the first instructions are
     * the last in the list).
     *
     * The indices in the {@link BitSet}s are the same as the indices in the {@link #frameSlots}
     * array.
     */
    public static class LLVMLivenessAnalysisResult {

        private final SSAValue[] frameSlots;

        private final ArrayList<NullerInformation>[] nullableWithinBlock;
        private final BitSet[] nullableBeforeBlock;
        private final BitSet[] nullableAfterBlock;

        public LLVMLivenessAnalysisResult(SSAValue[] frameSlots, ArrayList<NullerInformation>[] nullableWithinBlock, BitSet[] nullableBeforeBlock, BitSet[] nullableAfterBlock) {
            this.frameSlots = frameSlots;
            this.nullableWithinBlock = nullableWithinBlock;
            this.nullableBeforeBlock = nullableBeforeBlock;
            this.nullableAfterBlock = nullableAfterBlock;
        }

        public SSAValue[] getFrameSlots() {
            return frameSlots;
        }

        public ArrayList<NullerInformation>[] getNullableWithinBlock() {
            return nullableWithinBlock;
        }

        public BitSet[] getNullableBeforeBlock() {
            return nullableBeforeBlock;
        }

        public BitSet[] getNullableAfterBlock() {
            return nullableAfterBlock;
        }
    }

    private void printIntermediateResult(PrintStream logLivenessStream, List<InstructionBlock> blocks, BlockInfo[] blockInfos, int processedBlocks) {
        StringBuilder builder = new StringBuilder();
        builder.append(functionDefinition.getName());
        builder.append(" (processed ");
        builder.append(processedBlocks);
        builder.append(" blocks - CFG has ");
        builder.append(blocks.size());
        builder.append(" blocks)\n");
        for (int i = 0; i < blockInfos.length; i++) {
            BlockInfo blockInfo = blockInfos[i];
            builder.append("Basic block ");
            builder.append(i);
            builder.append(" (");
            builder.append(blocks.get(i).getName());
            builder.append(")\n");

            builder.append("  In:      ");
            builder.append(formatLocals(blockInfo.in));
            builder.append("\n");

            builder.append("  Gen:     ");
            builder.append(formatLocals(blockInfo.gen));
            builder.append("\n");

            builder.append("  Kill:    ");
            builder.append(formatLocals(blockInfo.kill));
            builder.append("\n");

            builder.append("  Def:     ");
            builder.append(formatLocals(blockInfo.defs));
            builder.append("\n");

            builder.append("  PhiDefs: ");
            builder.append(formatLocals(blockInfo.phiDefs));
            builder.append("\n");

            builder.append("  PhiUses: ");
            builder.append(formatLocals(blockInfo.phiUses));
            builder.append("\n");

            builder.append("  Out:     ");
            builder.append(formatLocals(blockInfo.out));
            builder.append("\n");
        }

        logLivenessStream.println(builder.toString());
    }

    private void printResult(PrintStream logLivenessStream, List<InstructionBlock> blocks, LLVMLivenessAnalysisResult result) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < blocks.size(); i++) {
            builder.append("Basic block ");
            builder.append(i);
            builder.append(" (");
            builder.append(blocks.get(i).getName());
            builder.append(")\n");

            builder.append("  NullableBefore: ");
            builder.append(formatLocals(result.nullableBeforeBlock[i]));
            builder.append("\n");

            builder.append("  NullableWithin:  ");
            builder.append(formatLocalNullers(result.nullableWithinBlock[i]));
            builder.append("\n");

            builder.append("  NullableAfter:  ");
            builder.append(formatLocals(result.nullableAfterBlock[i]));
            builder.append("\n");
        }

        logLivenessStream.println(builder.toString());
    }

    private static void appendValue(StringBuilder str, SSAValue value) {
        str.append(value.getFrameIdentifier());
        if (value.getName() != null && !LLVMIdentifier.UNKNOWN.equals(value.getName())) {
            str.append(" (").append(value.getName()).append(')');
        }
    }

    private String formatLocals(BitSet bitSet) {
        StringBuilder result = new StringBuilder();
        int bitIndex = -1;
        while ((bitIndex = bitSet.nextSetBit(bitIndex + 1)) >= 0) {
            if (result.length() > 0) {
                result.append(", ");
            }
            appendValue(result, frameSlots[bitIndex]);
        }
        return result.toString();
    }

    private static Object formatLocalNullers(ArrayList<NullerInformation> nullers) {
        StringBuilder result = new StringBuilder();
        for (NullerInformation nuller : nullers) {
            if (result.length() > 0) {
                result.append(", ");
            }
            appendValue(result, nuller.getIdentifier());
        }
        return result.toString();
    }
}
